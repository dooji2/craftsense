package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.manager.CraftSenseTracker;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.*;

public class CraftingPredictor {
    private static CraftingPredictor instance;
    private final RecipeManager recipeManager;
    private final CategoryHabitsTracker habitsConfig;

    private final Map<String, Optional<CraftingRecipe>> recipeCache = new HashMap<>();

    public CraftingPredictor(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
        this.habitsConfig = CategoryHabitsTracker.getInstance();
    }

    public static CraftingPredictor getInstance(RecipeManager recipeManager) {
        if (instance == null || instance.recipeManager != recipeManager) {
            instance = new CraftingPredictor(recipeManager);
        }
        return instance;
    }

    public List<ItemStack> getAvailableItems(Inventory playerInventory, ItemStack cursorStack, CraftingContainer craftingGrid) {
        List<ItemStack> availableItems = new ArrayList<>(playerInventory.items);

        if (!cursorStack.isEmpty()) {
            availableItems.add(cursorStack.copy());
        }

        for (int i = 0; i < craftingGrid.getContainerSize(); i++) {
            ItemStack stack = craftingGrid.getItem(i);
            if (!stack.isEmpty()) {
                availableItems.add(stack.copy());
            }
        }

        return availableItems;
    }

    private boolean isGridEmpty(CraftingContainer input) {
        for (int i = 0; i < input.getContainerSize(); i++) {
            if (!input.getItem(i).isEmpty()) {
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
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private boolean itemsAndComponentsMatch(ItemStack stack, ItemStack itemToMatch) {
        if (!ItemStack.isSameItem(stack, itemToMatch)) {
            return false;
        }
        return Objects.equals(stack.getComponents(), itemToMatch.getComponents());
    }

    public Optional<CraftingRecipe> suggestRecipe(CraftingContainer input, Inventory playerInventory, ItemStack cursorStack, Level world) {
        if (!CraftSense.configManager.isEnabled() || isGridEmpty(input)
                || recipeManager.getRecipeFor(RecipeType.CRAFTING, input.asCraftInput(), world).isPresent()) {
            return Optional.empty();
        }

        String inputHash = calculateInputHash(input, playerInventory, cursorStack);

        if (recipeCache.containsKey(inputHash)) {
            return recipeCache.get(inputHash);
        }

        Collection<RecipeHolder<CraftingRecipe>> recipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
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
            if (CategoryManager.getCategory(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemName)).orElse(Items.AIR)).equals(bestCategory)) {
                int itemCount = entry.getValue();
                if (itemCount > highestItemCount) {
                    mostCraftedItem = itemName;
                    highestItemCount = itemCount;
                }
            }
        }

        List<ItemStack> availableItems = getAvailableItems(playerInventory, cursorStack, input);
        List<RecipeHolder<CraftingRecipe>> filteredRecipeHolders = recipes.stream()
                .filter(holder -> hasRequiredIngredients(holder.value(), availableItems))
                .toList();

        for (RecipeHolder<CraftingRecipe> holder : filteredRecipeHolders) {
            CraftingRecipe recipe = holder.value();
            String category = CategoryManager.getCategory(recipe.getResultItem(world.registryAccess()).getItem());
            String itemName = recipe.getResultItem(world.registryAccess()).getDescriptionId();

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

        if (bestRecipe != null && CategoryManager.getCategory(bestRecipe.getResultItem(world.registryAccess()).getItem()).equals("TOOL")) {
            boolean hasWeapon = playerInventoryContainsWeapon(playerInventory);
            if (CraftSenseTracker.isPrioritizingCombatItems() && !hasWeapon) {
                Optional<CraftingRecipe> combatRecipe = suggestCombatRecipe(filteredRecipeHolders, input, playerInventory, cursorStack, world);
                if (combatRecipe.isPresent()) {
                    return combatRecipe;
                }
            }
        }

        Optional<CraftingRecipe> result = bestRecipe != null ? Optional.of(bestRecipe) : Optional.empty();
        recipeCache.put(inputHash, result);
        return result;
    }

    public boolean hasRequiredIngredients(CraftingRecipe recipe, List<ItemStack> availableItems) {
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }

            boolean found = false;
            for (ItemStack matchingStack : ingredient.getItems()) {
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

    private int calculateMatchScore(CraftingRecipe recipe, CraftingContainer input, Inventory playerInventory, ItemStack cursorStack) {
        int score = -1;

        List<ItemStack> availableItems = new ArrayList<>();
        for (ItemStack stack : playerInventory.items) {
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
                    int alignmentScore = matchResult.left();
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

    public String calculateInputHash(CraftingContainer input, Inventory playerInventory, ItemStack cursorStack) {
        StringBuilder hashBuilder = new StringBuilder();
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getDescriptionId() + ":" + stack.getCount()).append(",");
        }

        for (ItemStack stack : playerInventory.items) {
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getDescriptionId() + ":" + stack.getCount()).append(",");
        }

        if (!cursorStack.isEmpty()) {
            hashBuilder.append(cursorStack.getDescriptionId()).append(":").append(cursorStack.getCount());
        }

        return hashBuilder.toString();
    }

    public Pair<Integer, Boolean> matchShapedRecipe(ShapedRecipe recipe, CraftingContainer input, List<ItemStack> availableItems, int offsetX, int offsetY) {
        int bestScore = -1;
        boolean bestMirrored = false;

        for (boolean mirrored : new boolean[]{false, true}) {
            int score = 0;
            List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

            int recipeWidth = recipe.getWidth();
            int recipeHeight = recipe.getHeight();
            NonNullList<Ingredient> ingredients = recipe.getIngredients();

            boolean mismatch = false;

            for (int gridY = 0; gridY < 3; gridY++) {
                for (int gridX = 0; gridX < 3; gridX++) {
                    int gridIndex = gridY * 3 + gridX;
                    ItemStack placedItem = input.getItem(gridIndex);

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
                    Ingredient ingredient = ingredients.get(mirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);

                    int gridX = offsetX + recipeX;
                    int gridY = offsetY + recipeY;

                    int gridIndex = gridY * 3 + gridX;
                    ItemStack placedItem = input.getItem(gridIndex);

                    if (ingredient.isEmpty()) {
                        if (!placedItem.isEmpty()) {
                            mismatch = true;
                            break;
                        }
                        continue;
                    }

                    if (!placedItem.isEmpty()) {
                        if (ingredient.test(placedItem)) {
                            score += 2;
                            decrementAvailableItemCount(tempAvailableItems, placedItem);
                        } else {
                            mismatch = true;
                            break;
                        }
                    } else {
                        boolean found = false;
                        for (ItemStack matchingStack : ingredient.getItems()) {
                            if (decrementAvailableItemCount(tempAvailableItems, matchingStack)) {
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
                long nonEmptyIngredientCount = ingredients.stream().filter(i -> !i.isEmpty()).count();
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

    private int matchShapelessRecipe(CraftingRecipe recipe, CraftingContainer input, List<ItemStack> availableItems) {
        int score = 0;
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        List<Ingredient> ingredientsToMatch = new ArrayList<>(ingredients);

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack placedItem = input.getItem(i);
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
            for (ItemStack matchingStack : ingredient.getItems()) {
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

        if (score == recipe.getIngredients().size() * 2) {
            return Integer.MAX_VALUE;
        }

        return score;
    }

    private boolean playerInventoryContainsWeapon(Inventory playerInventory) {
        for (ItemStack stack : playerInventory.items) {
            if (!stack.isEmpty() && (stack.getItem().getDescriptionId().toUpperCase().contains("SWORD")
                    || stack.getItem().getDescriptionId().toUpperCase().contains("_AXE"))) {
                return true;
            }
        }
        return false;
    }

    private Optional<CraftingRecipe> suggestCombatRecipe(List<RecipeHolder<CraftingRecipe>> holders, CraftingContainer input, Inventory playerInventory, ItemStack cursorStack, Level world) {
        for (RecipeHolder<CraftingRecipe> holder : holders) {
            CraftingRecipe recipe = holder.value();
            String resultName = recipe.getResultItem(world.registryAccess()).getDescriptionId().toUpperCase();
            if (resultName.contains("SWORD") || resultName.contains("_AXE") || resultName.contains("SHIELD")) {
                int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
                if (score > 0) {
                    return Optional.of(recipe);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<CraftingRecipe> suggestLastCraftedItem(CraftingContainer input, Inventory playerInventory, ItemStack cursorStack, Level world) {
        String lastCraftedItem = CategoryHabitsTracker.getInstance().getLastCraftedItem();
        if (lastCraftedItem == null || !isGridEmpty(input)) {
            return Optional.empty();
        }

        Collection<RecipeHolder<CraftingRecipe>> recipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);

        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            CraftingRecipe recipe = holder.value();
            String resultDescriptionId = recipe.getResultItem(world.registryAccess()).getDescriptionId();

            if (resultDescriptionId.equals(lastCraftedItem)) {
                int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
                if (score > 0) {
                    return Optional.of(recipe);
                }
            }
        }

        return Optional.empty();
    }
}
