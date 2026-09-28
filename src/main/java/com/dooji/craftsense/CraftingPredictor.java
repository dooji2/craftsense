package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.*;

public class CraftingPredictor {
    private static CraftingPredictor instance;
    private final CategoryHabitsTracker habitsConfig;
    private Collection<RecipeHolder<?>> recipes = Collections.emptyList();

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

    public void setRecipes(Collection<RecipeHolder<?>> recipes) {
        this.recipes = recipes;
        recipeCache.clear();
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return recipes;
    }

    public Identifier getRecipeId(CraftingRecipe targetRecipe) {
        for (RecipeHolder<?> recipeEntry : recipes) {
            if (recipeEntry.value() == targetRecipe) {
                return recipeEntry.id().identifier();
            }
        }

        return null;
    }

    public static List<Optional<Ingredient>> getIngredients(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return shapedRecipe.getIngredients();
        }

        return recipe.placementInfo().ingredients().stream().map(Optional::of).toList();
    }

    public static boolean fits(CraftingRecipe recipe, int width, int height) {
        return recipe instanceof ShapedRecipe shapedRecipe
                ? shapedRecipe.getWidth() <= width && shapedRecipe.getHeight() <= height
                : recipe.placementInfo().ingredients().size() <= width * height;
    }

    public static ItemStack getResult(CraftingRecipe recipe, Level world) {
        return recipe.display().getFirst().result().resolveForFirstStack(SlotDisplayContext.fromLevel(world));
    }

    private List<CraftingRecipe> getCraftingRecipes() {
        List<CraftingRecipe> craftingRecipes = new ArrayList<>();
        for (RecipeHolder<?> recipeEntry : recipes) {
            if (recipeEntry.value() instanceof CraftingRecipe recipe && recipe.getType() == RecipeType.CRAFTING) {
                craftingRecipes.add(recipe);
            }
        }

        return craftingRecipes;
    }

    public List<ItemStack> getAvailableItems(Inventory playerInventory, ItemStack cursorStack, CraftingContainer craftingGrid) {
        List<ItemStack> availableItems = new ArrayList<>(playerInventory.getNonEquipmentItems());

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

    public Optional<CraftingRecipe> suggestRecipe(CraftingContainer input, Inventory playerInventory, ItemStack cursorStack, Level world, boolean prioritizeCombatItems) {
        List<CraftingRecipe> recipes = getCraftingRecipes();
        if (isGridEmpty(input)) {
            return Optional.empty();
        }

        CraftingInput recipeInput = input.asCraftInput();
        for (CraftingRecipe recipe : recipes) {
            if (recipe.matches(recipeInput, world)) {
                return Optional.empty();
            }
        }

        String inputHash = calculateInputHash(input, playerInventory, cursorStack);

        if (recipeCache.containsKey(inputHash)) {
            return recipeCache.get(inputHash);
        }

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
        for (Item item : BuiltInRegistries.ITEM) {
            String itemName = item.getDescriptionId();

            if (CategoryManager.getCategory(item).equals(bestCategory)) {
                int itemCount = habitsConfig.getItemCraftCount(itemName);

                if (itemCount > highestItemCount) {
                    mostCraftedItem = itemName;
                    highestItemCount = itemCount;
                }
            }
        }

        List<ItemStack> availableItems = getAvailableItems(playerInventory, cursorStack, input);
        List<CraftingRecipe> filteredRecipes = recipes.stream()
                .filter(recipe -> fits(recipe, input.getWidth(), input.getHeight()))
                .filter(recipe -> !getIngredients(recipe).isEmpty())
                .filter(recipe -> hasRequiredIngredients(recipe, availableItems))
                .toList();

        for (CraftingRecipe recipe : filteredRecipes) {
            ItemStack resultStack = getResult(recipe, world);
            String category = CategoryManager.getCategory(resultStack.getItem());
            String itemName = resultStack.getItem().getDescriptionId();

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

        if (bestRecipe != null && CategoryManager.getCategory(getResult(bestRecipe, world).getItem()).equals("TOOL")) {
            boolean hasWeapon = playerInventoryContainsWeapon(playerInventory);

            if (prioritizeCombatItems && !hasWeapon) {
                Optional<CraftingRecipe> combatRecipe = suggestCombatRecipe(filteredRecipes, input, playerInventory, cursorStack, world);
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

        for (Optional<Ingredient> ingredientOptional : getIngredients(recipe)) {
            if (ingredientOptional.isEmpty()) {
                continue;
            }

            Ingredient ingredient = ingredientOptional.get();

            boolean found = false;
            for (ItemStack matchingStack : ingredient.items().map(item -> item.value().getDefaultInstance()).toList()) {
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
        List<ItemStack> availableItems = getAvailableItems(playerInventory, cursorStack, input);

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int recipeWidth = shapedRecipe.getWidth();
            int recipeHeight = shapedRecipe.getHeight();

            int maxOffsetX = input.getWidth() - recipeWidth;
            int maxOffsetY = input.getHeight() - recipeHeight;

            for (int offsetX = 0; offsetX <= maxOffsetX; offsetX++) {
                for (int offsetY = 0; offsetY <= maxOffsetY; offsetY++) {
                    Tuple<Integer, Boolean> matchResult = matchShapedRecipe(shapedRecipe, input, availableItems, offsetX, offsetY);
                    int alignmentScore = matchResult.getA();
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
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getItem().getDescriptionId() + ":" + stack.getCount()).append(",");
        }

        for (ItemStack stack : playerInventory.getNonEquipmentItems()) {
            hashBuilder.append(stack.isEmpty() ? "-" : stack.getItem().getDescriptionId() + ":" + stack.getCount()).append(",");
        }

        if (!cursorStack.isEmpty()) {
            hashBuilder.append(cursorStack.getItem().getDescriptionId()).append(":").append(cursorStack.getCount());
        }

        return hashBuilder.append(habitsConfig.itemCraftCount).append(habitsConfig.getLastCraftedItem()).toString();
    }

    public Tuple<Integer, Boolean> matchShapedRecipe(ShapedRecipe recipe, CraftingContainer input, List<ItemStack> availableItems, int offsetX, int offsetY) {
        int bestScore = -1;
        boolean bestMirrored = false;

        for (boolean mirrored : new boolean[]{false, true}) {
            int score = 0;
            List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

            int recipeWidth = recipe.getWidth();
            int recipeHeight = recipe.getHeight();
            List<Optional<Ingredient>> ingredients = recipe.getIngredients();

            boolean mismatch = false;

            for (int gridY = 0; gridY < input.getHeight(); gridY++) {
                for (int gridX = 0; gridX < input.getWidth(); gridX++) {
                    int gridIndex = gridY * input.getWidth() + gridX;
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
                    Optional<Ingredient> ingredientOptional = ingredients.get(mirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);

                    int gridX = offsetX + recipeX;
                    int gridY = offsetY + recipeY;

                    int gridIndex = gridY * input.getWidth() + gridX;
                    ItemStack placedItem = input.getItem(gridIndex);

                    if (ingredientOptional.isEmpty()) {
                        if (!placedItem.isEmpty()) {
                            mismatch = true;
                            break;
                        }
                        continue;
                    }

                    Ingredient ingredient = ingredientOptional.get();

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
                        for (ItemStack matchingStack : ingredient.items().map(item -> item.value().getDefaultInstance()).toList()) {
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
                long nonEmptyIngredientCount = ingredients.stream().filter(Optional::isPresent).count();
                if (score == nonEmptyIngredientCount * 2) {
                    return new Tuple<>(Integer.MAX_VALUE, mirrored);
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestMirrored = mirrored;
                }
            }
        }

        return new Tuple<>(bestScore, bestMirrored);
    }

    private int matchShapelessRecipe(CraftingRecipe recipe, CraftingContainer input, List<ItemStack> availableItems) {
        int score = 0;
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        List<Ingredient> ingredients = getIngredients(recipe).stream().flatMap(Optional::stream).toList();
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
            for (ItemStack matchingStack : ingredient.items().map(item -> item.value().getDefaultInstance()).toList()) {
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

    private boolean playerInventoryContainsWeapon(Inventory playerInventory) {
        for (ItemStack stack : playerInventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && (stack.getItem().getDescriptionId().toUpperCase().contains("SWORD") || stack.getItem().getDescriptionId().toUpperCase().contains("_AXE"))) {
                return true;
            }
        }

        return false;
    }

    private Optional<CraftingRecipe> suggestCombatRecipe(List<CraftingRecipe> recipes, CraftingContainer input, Inventory playerInventory, ItemStack cursorStack, Level world) {
        for (CraftingRecipe recipe : recipes) {
            String resultName = getResult(recipe, world).getItem().getDescriptionId().toUpperCase();
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

        List<CraftingRecipe> recipes = getCraftingRecipes();

        for (CraftingRecipe recipe : recipes) {
            if (getIngredients(recipe).isEmpty()) {
                continue;
            }

            String resultTranslationKey = getResult(recipe, world).getItem().getDescriptionId();

            if (fits(recipe, input.getWidth(), input.getHeight()) && resultTranslationKey.equals(lastCraftedItem)) {
                int score = calculateMatchScore(recipe, input, playerInventory, cursorStack);
                if (score > 0) {
                    return Optional.of(recipe);
                }
            }
        }

        return Optional.empty();
    }
}