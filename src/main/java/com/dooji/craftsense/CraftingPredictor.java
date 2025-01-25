package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.mixin.ShapedRecipeAccessor;
import com.dooji.craftsense.mixin.ShapelessRecipeAccessor;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class CraftingPredictor {
    private static CraftingPredictor instance;
    private final CategoryHabitsTracker habitsConfig;

    private Collection<RecipeEntry<?>> recipes = Collections.emptyList();
    private Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey;

    private final Map<String, Optional<CraftingRecipe>> recipeCache = new HashMap<>();

    public CraftingPredictor() {
        this.habitsConfig = CategoryHabitsTracker.getInstance();
    }

    public static CraftingPredictor getInstance() {
        if (instance == null) {
            instance = new CraftingPredictor();
        }
        return instance;
    }

    public List<ItemStack> getAvailableItems(PlayerInventory playerInventory, ItemStack cursorStack, RecipeInputInventory craftingGrid) {
        List<ItemStack> availableItems = new ArrayList<>(playerInventory.main);

        if (!cursorStack.isEmpty()) {
            availableItems.add(cursorStack.copy());
        }

        for (int i = 0; i < craftingGrid.size(); i++) {
            ItemStack stack = craftingGrid.getStack(i);
            if (!stack.isEmpty()) {
                availableItems.add(stack.copy());
            }
        }

        return availableItems;
    }

    public void setRecipes(Collection<RecipeEntry<?>> recipes) {
        this.recipes = recipes;
    }

    public void setRecipesByKey(Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey) {
        this.recipesByKey = recipesByKey;
    }

    public Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> getRecipesByKey() {
        return recipesByKey != null ? recipesByKey : Collections.emptyMap();
    }

    private boolean isGridEmpty(RecipeInputInventory input) {
        for (int i = 0; i < input.size(); i++) {
            if (!input.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> copyItemStacks(List<ItemStack> original) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : original) {
            copy.add(stack.copy());
        }

        return copy;
    }

    private boolean decrementAvailableItemCount(List<ItemStack> availableItems, ItemStack itemToMatch) {
        for (ItemStack stack : availableItems) {
            if (itemsAndComponentsMatch(stack, itemToMatch) && stack.getCount() > 0) {
                stack.decrement(1);
                return true;
            }
        }

        return false;
    }

    private boolean itemsAndComponentsMatch(ItemStack stack, ItemStack itemToMatch) {
        if (!ItemStack.areItemsEqual(stack, itemToMatch)) {
            return false;
        }

        return Objects.equals(stack.getComponents(), itemToMatch.getComponents());
    }

    private <T extends Recipe<CraftingRecipeInput>> Optional<RecipeEntry<T>> findMatchingRecipe(
            RecipeType<T> type, RecipeInputInventory input, World world, List<ServerRecipeManager.ServerRecipe> recipes) {

        CraftingRecipeInput recipeInput = input.createRecipeInput();

        for (ServerRecipeManager.ServerRecipe serverRecipe : recipes) {
            RecipeEntry<T> recipeEntry = (RecipeEntry<T>) serverRecipe.parent();
            T recipe = recipeEntry.value();

            if (recipe.getType() == type && recipe.matches(recipeInput, world)) {
                return Optional.of(recipeEntry);
            }
        }

        return Optional.empty();
    }

    public Optional<CraftingRecipe> suggestRecipe(RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack, World world) {
        List<ServerRecipeManager.ServerRecipe> serverRecipes = recipesByKey.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (!CraftSense.configManager.isEnabled() || isGridEmpty(input) || recipes.isEmpty() || findMatchingRecipe(RecipeType.CRAFTING, input, world, serverRecipes).isPresent()) {
            return Optional.empty();
        }

        String inputHash = calculateInputHash(input, playerInventory, cursorStack);

        if (recipeCache.containsKey(inputHash)) {
            return recipeCache.get(inputHash);
        }

        Collection<RecipeEntry<?>> allRecipes = recipes;
        List<RecipeEntry<CraftingRecipe>> recipes = allRecipes.stream()
                .filter(recipeEntry -> recipeEntry.value() instanceof CraftingRecipe)
                .map(recipeEntry -> (RecipeEntry<CraftingRecipe>) recipeEntry)
                .collect(Collectors.toList());

        CraftingRecipe bestRecipe = null;
        int bestScore = -1;

        String bestCategory = null;
        int highestCategoryCount = -1;
        for (Map.Entry<String, Integer> entry : habitsConfig.categoryCraftCount.entrySet()) {
            if (entry.getValue() > highestCategoryCount) {
                bestCategory = entry.getKey();
                highestCategoryCount = entry.getValue();
            }
        }

        String mostCraftedItem = null;
        int highestItemCount = -1;
        for (Map.Entry<String, Integer> entry : habitsConfig.itemCraftCount.entrySet()) {
            String itemName = entry.getKey();
            if (CategoryManager.getCategory(Registries.ITEM.get(Identifier.of(itemName))).equals(bestCategory)) {
                int itemCount = entry.getValue();
                if (itemCount > highestItemCount) {
                    mostCraftedItem = itemName;
                    highestItemCount = itemCount;
                }
            }
        }

        List<ItemStack> availableItems = getAvailableItems(playerInventory, cursorStack, input);
        List<RecipeEntry<CraftingRecipe>> filteredRecipeEntries = recipes.stream()
                .filter(recipeEntry -> hasRequiredIngredients(recipeEntry.value(), availableItems))
                .collect(Collectors.toList());

        for (RecipeEntry<CraftingRecipe> recipeEntry : filteredRecipeEntries) {
            CraftingRecipe recipe = recipeEntry.value();
            ItemStack resultStack = getRecipeResult(recipe);
            if (resultStack.isEmpty()) continue;
            String category = CategoryManager.getCategory(resultStack.getItem());
            String itemName = resultStack.getItem().getTranslationKey();

            int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
            if (score <= 0) continue;

            if (category.equals(bestCategory)) {
                int itemCount = habitsConfig.getItemCraftCount(itemName);
                int categoryCount = habitsConfig.getCraftCount(category);

                if (itemName.equals(mostCraftedItem)) {
                    score += itemCount * 5 + categoryCount * 2;
                } else {
                    score += itemCount * 3 + categoryCount * 2;
                }
            } else {
                score += 1;
            }

            if (score > bestScore) {
                bestScore = score;
                bestRecipe = recipe;
            }
        }

        if (bestRecipe != null && CategoryManager.getCategory(getRecipeResult(bestRecipe).getItem()).equals("TOOL")) {
            boolean hasWeapon = playerInventoryContainsWeapon(playerInventory);

            if (CraftSenseTracker.isPrioritizingCombatItems() && !hasWeapon) {
                Optional<CraftingRecipe> combatRecipe = suggestCombatRecipe(filteredRecipeEntries, input, playerInventory, cursorStack, world);
                if (combatRecipe.isPresent()) {
                    return combatRecipe;
                }
            }
        }

        Optional<CraftingRecipe> result = bestRecipe != null ? Optional.of(bestRecipe) : Optional.empty();
        recipeCache.put(inputHash, result);
        return result;
    }

    private ItemStack getRecipeResult(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe) {
            ItemStack result = ((ShapedRecipeAccessor) recipe).getResult();
            return result.copy();
        } else if (recipe instanceof ShapelessRecipe) {
            ItemStack result = ((ShapelessRecipeAccessor) recipe).getResult();
            return result.copy();
        } else {
            return ItemStack.EMPTY;
        }
    }

    public boolean hasRequiredIngredients(CraftingRecipe recipe, List<ItemStack> availableItems) {
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        List<Ingredient> ingredients = recipe.getIngredientPlacement().getIngredients();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.getMatchingItems().isEmpty()) {
                continue;
            }

            boolean found = false;
            for (RegistryEntry<Item> matchingItemEntry : ingredient.getMatchingItems()) {
                Item matchingItem = matchingItemEntry.value();
                ItemStack matchingStack = new ItemStack(matchingItem);
                if (decrementAvailableItemCount(tempAvailableItems, matchingStack)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private int calculateMatchScore(CraftingRecipe recipe, RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack) {
        int score = -1;

        List<ItemStack> availableItems = new ArrayList<>();
        for (ItemStack stack : playerInventory.main) {
            availableItems.add(stack.copy());
        }

        if (!cursorStack.isEmpty()) {
            availableItems.add(cursorStack.copy());
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int recipeWidth = shapedRecipe.getWidth();
            int recipeHeight = shapedRecipe.getHeight();

            int maxOffsetX = 3 - recipeWidth;
            int maxOffsetY = 3 - recipeHeight;

            for (int offsetX = 0; offsetX <= maxOffsetX; offsetX++) {
                for (int offsetY = 0; offsetY <= maxOffsetY; offsetY++) {
                    Pair<Integer, Boolean> matchResult = matchShapedRecipe(shapedRecipe, input, getAvailableItems(playerInventory, cursorStack, input), offsetX, offsetY);
                    int alignmentScore = matchResult.getLeft();
                    if (alignmentScore > score) {
                        score = alignmentScore;
                        if (score == Integer.MAX_VALUE) {
                            return score;
                        }
                    }
                }
            }
        } else {
            int alignmentScore = matchShapelessRecipe(recipe, input, availableItems);
            if (alignmentScore > score) {
                score = alignmentScore;
            }
        }

        return score;
    }

    public String calculateInputHash(RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack) {
        StringBuilder hashBuilder = new StringBuilder();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStack(i);
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getItem().getTranslationKey() + ":" + stack.getCount()).append(",");
        }

        for (ItemStack stack : playerInventory.main) {
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getItem().getTranslationKey() + ":" + stack.getCount()).append(",");
        }

        if (!cursorStack.isEmpty()) {
            hashBuilder.append(cursorStack.getItem().getTranslationKey()).append(":").append(cursorStack.getCount());
        }

        return hashBuilder.toString();
    }

    public Pair<Integer, Boolean> matchShapedRecipe(ShapedRecipe recipe, RecipeInputInventory input, List<ItemStack> availableItems, int offsetX, int offsetY) {
        int bestScore = -1;
        boolean bestMirrored = false;

        for (boolean mirrored : new boolean[]{false, true}) {
            int score = 0;
            List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

            int recipeWidth = recipe.getWidth();
            int recipeHeight = recipe.getHeight();
            List<Optional<Ingredient>> ingredients = recipe.getIngredients();

            boolean mismatch = false;

            for (int gridY = 0; gridY < 3; gridY++) {
                for (int gridX = 0; gridX < 3; gridX++) {
                    int gridIndex = gridY * 3 + gridX;
                    ItemStack placedItem = input.getStack(gridIndex);

                    boolean isWithinRecipe = gridX >= offsetX && gridX < offsetX + recipeWidth && gridY >= offsetY && gridY < offsetY + recipeHeight;

                    if (!isWithinRecipe && !placedItem.isEmpty()) {
                        mismatch = true;
                        break;
                    }
                }

                if (mismatch) break;
            }

            if (mismatch) continue;

            for (int recipeY = 0; recipeY < recipeHeight; recipeY++) {
                for (int recipeX = 0; recipeX < recipeWidth; recipeX++) {
                    int index = recipeY * recipeWidth + recipeX;
                    Optional<Ingredient> ingredient = ingredients.get(mirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);

                    int gridX = offsetX + recipeX;
                    int gridY = offsetY + recipeY;

                    int gridIndex = gridY * 3 + gridX;
                    ItemStack placedItem = input.getStack(gridIndex);

                    if (ingredient.isEmpty()) {
                        if (!placedItem.isEmpty()) {
                            mismatch = true;
                            break;
                        }
                        continue;
                    }

                    if (!placedItem.isEmpty()) {
                        if (ingredient.get().test(placedItem)) {
                            score += 2;
                            decrementAvailableItemCount(tempAvailableItems, placedItem);
                        } else {
                            mismatch = true;
                            break;
                        }
                    } else {
                        boolean found = false;
                        for (RegistryEntry<Item> matchingItem : ingredient.get().getMatchingItems()) {
                            if (decrementAvailableItemCount(tempAvailableItems, new ItemStack(matchingItem.value()))) {
                                score += 1;
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            mismatch = true;
                            break;
                        }
                    }
                }

                if (mismatch) break;
            }

            if (!mismatch) {
                long nonEmptyIngredientCount = ingredients.stream().filter(Optional::isPresent).count();
                if (score == nonEmptyIngredientCount * 2) {
                    return new Pair<>(Integer.MAX_VALUE, mirrored);
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestMirrored = mirrored;
                }
            }
        }

        return new Pair<>(bestScore, bestMirrored);
    }

    private int matchShapelessRecipe(CraftingRecipe recipe, RecipeInputInventory input, List<ItemStack> availableItems) {
        int score = 0;
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        List<Ingredient> ingredients = ((ShapelessRecipeAccessor) recipe).getIngredients();
        List<Ingredient> ingredientsToMatch = new ArrayList<>(ingredients);

        for (int i = 0; i < input.size(); i++) {
            ItemStack placedItem = input.getStack(i);
            if (!placedItem.isEmpty()) {
                boolean matched = false;

                Iterator<Ingredient> iterator = ingredientsToMatch.iterator();
                while (iterator.hasNext()) {
                    Ingredient ingredient = iterator.next();
                    if (ingredient.test(placedItem)) {
                        score += 2;
                        iterator.remove();
                        decrementAvailableItemCount(tempAvailableItems, placedItem);
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    return -1;
                }
            }
        }

        for (Ingredient ingredient : ingredientsToMatch) {
            boolean found = false;
            for (RegistryEntry<Item> matchingItemEntry : ingredient.getMatchingItems()) {
                Item matchingItem = matchingItemEntry.value();
                ItemStack matchingStack = new ItemStack(matchingItem);
                if (decrementAvailableItemCount(tempAvailableItems, matchingStack)) {
                    score += 1;
                    found = true;
                    break;
                }
            }

            if (!found) {
                return -1;
            }
        }

        if (score == ingredients.size() * 2) {
            return Integer.MAX_VALUE;
        }

        return score;
    }

    private boolean playerInventoryContainsWeapon(PlayerInventory playerInventory) {
        for (ItemStack stack : playerInventory.main) {
            if (!stack.isEmpty() && (stack.getItem().getTranslationKey().toUpperCase().contains("SWORD") || stack.getItem().getTranslationKey().toUpperCase().contains("_AXE"))) {
                return true;
            }
        }

        return false;
    }

    private Optional<CraftingRecipe> suggestCombatRecipe(List<RecipeEntry<CraftingRecipe>> recipes, RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack, World world) {
        for (RecipeEntry<CraftingRecipe> recipeEntry : recipes) {
            CraftingRecipe recipe = recipeEntry.value();
            ItemStack resultStack = getRecipeResult(recipe);
            if (resultStack.isEmpty()) continue;
            String resultName = resultStack.getItem().getTranslationKey().toUpperCase();
            if (resultName.contains("SWORD") || resultName.contains("_AXE") || resultName.contains("SHIELD")) {
                int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
                if (score > 0) {
                    return Optional.of(recipe);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<CraftingRecipe> suggestLastCraftedItem(RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack, World world) {
        String lastCraftedItem = CategoryHabitsTracker.getInstance().getLastCraftedItem();

        List<ServerRecipeManager.ServerRecipe> serverRecipes = recipesByKey.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (lastCraftedItem == null || !isGridEmpty(input) || recipes.isEmpty() || findMatchingRecipe(RecipeType.CRAFTING, input, world, serverRecipes).isPresent()) {
            return Optional.empty();
        }

        Collection<RecipeEntry<?>> allRecipes = recipes;
        List<RecipeEntry<CraftingRecipe>> recipes = allRecipes.stream()
                .filter(recipeEntry -> recipeEntry.value() instanceof CraftingRecipe)
                .map(recipeEntry -> (RecipeEntry<CraftingRecipe>) recipeEntry)
                .collect(Collectors.toList());

        for (RecipeEntry<CraftingRecipe> recipeEntry : recipes) {
            CraftingRecipe recipe = recipeEntry.value();
            ItemStack resultStack = getRecipeResult(recipe);

            if (resultStack.isEmpty()) continue;

            String resultTranslationKey = resultStack.getItem().getTranslationKey();

            if (resultTranslationKey.equals(lastCraftedItem)) {
                int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
                if (score > 0) {
                    return Optional.of(recipe);
                }
            }
        }

        return Optional.empty();
    }
}