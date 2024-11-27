package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CraftSenseTracker;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;

import static com.dooji.craftsense.manager.CategoryManager.getCategory;

public class CraftingPredictor {
    private static CraftingPredictor instance;
    private final RecipeManager recipeManager;
    private final CategoryHabitsTracker habitsConfig;

    public CraftingPredictor(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
        this.habitsConfig = CategoryHabitsTracker.getInstance();
    }

    public static CraftingPredictor getInstance(RecipeManager recipeManager) {
        if (instance == null) {
            instance = new CraftingPredictor(recipeManager);
        }
        return instance;
    }

    public List<ItemStack> getAvailableItems(PlayerInventory playerInventory, ItemStack cursorStack) {
        List<ItemStack> availableItems = new ArrayList<>(playerInventory.main);
        if (!cursorStack.isEmpty()) {
            availableItems.add(cursorStack.copy());
        }
        return availableItems;
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

        if (stack.hasNbt() && itemToMatch.hasNbt()) {
            return Objects.equals(stack.getNbt(), itemToMatch.getNbt());
        }
        return !stack.hasNbt() && !itemToMatch.hasNbt();
    }

    public Optional<CraftingRecipe> suggestRecipe(RecipeInputInventory input, PlayerInventory playerInventory, ItemStack cursorStack, World world) {
        if (!CraftSense.configManager.isEnabled() || isGridEmpty(input) || recipeManager.getFirstMatch(RecipeType.CRAFTING, input, world).isPresent()) {
            return Optional.empty();
        }

        List<RecipeEntry<CraftingRecipe>> recipes = recipeManager.listAllOfType(RecipeType.CRAFTING);
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
            if (getCategory(Registries.ITEM.get(new Identifier(itemName))).equals(bestCategory)) {
                int itemCount = entry.getValue();
                if (itemCount > highestItemCount) {
                    mostCraftedItem = itemName;
                    highestItemCount = itemCount;
                }
            }
        }

        for (RecipeEntry<CraftingRecipe> recipeEntry : recipes) {
            CraftingRecipe recipe = recipeEntry.value();
            String category = getCategory(recipe.getResult(world.getRegistryManager()).getItem());
            String itemName = recipe.getResult(world.getRegistryManager()).getTranslationKey();

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

        if (bestRecipe != null && getCategory(bestRecipe.getResult(world.getRegistryManager()).getItem()).equals("TOOL")) {
            boolean hasWeapon = playerInventoryContainsWeapon(playerInventory);

            if (CraftSenseTracker.isPrioritizingCombatItems() && !hasWeapon) {
                Optional<CraftingRecipe> combatRecipe = suggestCombatRecipe(recipes, input, playerInventory, cursorStack, world);
                if (combatRecipe.isPresent()) {
                    return combatRecipe;
                }
            }
        }

        return bestRecipe != null ? Optional.of(bestRecipe) : Optional.empty();
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
                    int alignmentScore = matchShapedRecipe(shapedRecipe, input, availableItems, offsetX, offsetY);
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

    public int matchShapedRecipe(ShapedRecipe recipe, RecipeInputInventory input, List<ItemStack> availableItems, int offsetX, int offsetY) {
        int score = 0;
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        int recipeWidth = recipe.getWidth();
        int recipeHeight = recipe.getHeight();
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();

        boolean mismatch = false;

        for (int gridY = 0; gridY < 3; gridY++) {
            for (int gridX = 0; gridX < 3; gridX++) {
                int gridIndex = gridY * 3 + gridX;
                ItemStack placedItem = input.getStack(gridIndex);

                boolean isWithinRecipe = gridX >= offsetX && gridX < offsetX + recipeWidth
                        && gridY >= offsetY && gridY < offsetY + recipeHeight;

                if (!isWithinRecipe && !placedItem.isEmpty()) {
                    mismatch = true;
                    break;
                }
            }
            if (mismatch) {
                break;
            }
        }

        if (mismatch) {
            return -1;
        }

        for (int recipeY = 0; recipeY < recipeHeight; recipeY++) {
            for (int recipeX = 0; recipeX < recipeWidth; recipeX++) {
                int index = recipeY * recipeWidth + recipeX;
                Ingredient ingredient = ingredients.get(index);

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
                    if (ingredient.test(placedItem)) {
                        score += 2;
                        decrementAvailableItemCount(tempAvailableItems, placedItem);
                    } else {
                        mismatch = true;
                        break;
                    }
                } else {

                    boolean found = false;
                    for (ItemStack matchingStack : ingredient.getMatchingStacks()) {
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
            if (mismatch) {
                break;
            }
        }

        if (mismatch) {
            return -1;
        } else {

            long nonEmptyIngredientCount = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).count();
            if (score == nonEmptyIngredientCount * 2) {
                return Integer.MAX_VALUE;
            }
            return score;
        }
    }

    private int matchShapelessRecipe(CraftingRecipe recipe, RecipeInputInventory input, List<ItemStack> availableItems) {
        int score = 0;
        List<ItemStack> tempAvailableItems = copyItemStacks(availableItems);

        List<Ingredient> ingredients = recipe.getIngredients();
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
            for (ItemStack matchingStack : ingredient.getMatchingStacks()) {
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
            String resultName = recipe.getResult(world.getRegistryManager()).getTranslationKey().toUpperCase();
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
        if (lastCraftedItem == null || !isGridEmpty(input)) {
            return Optional.empty();
        }
        List<RecipeEntry<CraftingRecipe>> recipes = recipeManager.listAllOfType(RecipeType.CRAFTING);
        for (RecipeEntry<CraftingRecipe> recipeEntry : recipes) {
            CraftingRecipe recipe = recipeEntry.value();
            String resultTranslationKey = recipe.getResult(world.getRegistryManager()).getTranslationKey();
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