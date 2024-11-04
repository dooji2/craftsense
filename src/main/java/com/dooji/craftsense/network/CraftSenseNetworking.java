package com.dooji.craftsense.network;

import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CraftSenseNetworking {
    public static void init() {
        PayloadTypeRegistry.playC2S().register(CraftItemPayload.ID, CraftItemPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CraftItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleCraftItemPayload(payload, context.player()));
        });
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayerEntity player) {
        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Identifier recipeId = Identifier.of(payload.recipeId());

        Optional<CraftingRecipe> recipeOptional = recipeManager.get(recipeId)
                .map(recipeEntry -> recipeEntry.value())
                .filter(recipe -> recipe instanceof CraftingRecipe)
                .map(recipe -> (CraftingRecipe) recipe);

        if (recipeOptional.isPresent()) {
            CraftingRecipe recipe = recipeOptional.get();
            PlayerInventory inventory = player.getInventory();

            if (player.currentScreenHandler instanceof CraftingScreenHandler handler) {
                RecipeInputInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getInput();
                ItemStack resultStack = recipe.getResult(player.getServer().getRegistryManager()).copy();
                ItemStack cursorStack = handler.getCursorStack();

                if (cursorStack.isEmpty()) {
                    handler.setCursorStack(resultStack);
                } else if (areStacksEqualWithComponents(cursorStack, resultStack)) {
                    cursorStack.increment(resultStack.getCount());
                    handler.setCursorStack(cursorStack);
                } else {
                    return;
                }

                if (hasAllIngredients(inventory, gridInventory, recipe)) {
                    consumeIngredients(recipe, gridInventory, inventory);

                    player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                            handler.syncId, -1, 0, handler.getCursorStack()
                    ));

                    clearGridAndSync(handler, player);
                }
            }
        }
    }

    private static boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.areItemsEqual(stack1, stack2)) {
            return false;
        }

        return Objects.equals(stack1.getComponents(), stack2.getComponents());
    }

    private static boolean hasAllIngredients(PlayerInventory inventory, RecipeInputInventory gridInventory, CraftingRecipe recipe) {
        for (var ingredient : recipe.getIngredients()) {
            boolean found = false;
            for (int i = 0; i < gridInventory.size(); i++) {
                if (ingredient.test(gridInventory.getStack(i))) {
                    found = true;
                    break;
                }
            }
            if (!found && !findInInventory(inventory, ingredient)) return false;
        }
        return true;
    }

    private static boolean findInInventory(PlayerInventory inventory, Ingredient ingredient) {
        for (int i = 0; i < inventory.size(); i++) {
            if (ingredient.test(inventory.getStack(i))) return true;
        }
        return false;
    }

    private static void consumeIngredients(CraftingRecipe recipe, RecipeInputInventory gridInventory, PlayerInventory inventory) {
        Map<Ingredient, Integer> ingredientsNeeded = new HashMap<>();

        for (var ingredient : recipe.getIngredients()) {
            ingredientsNeeded.put(ingredient, ingredientsNeeded.getOrDefault(ingredient, 0) + 1);
        }

        for (var entry : ingredientsNeeded.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int requiredAmount = entry.getValue();

            int consumedFromGrid = consumeFromGrid(ingredient, gridInventory, requiredAmount);
            requiredAmount -= consumedFromGrid;

            if (requiredAmount > 0) {
                consumeFromInventory(ingredient, inventory, requiredAmount);
            }
        }
    }

    private static int consumeFromGrid(Ingredient ingredient, RecipeInputInventory gridInventory, int requiredAmount) {
        int amountConsumed = 0;

        for (int i = 0; i < gridInventory.size(); i++) {
            ItemStack stack = gridInventory.getStack(i);
            if (ingredient.test(stack) && !stack.isEmpty()) {
                int toConsume = Math.min(requiredAmount, stack.getCount());
                stack.decrement(toConsume);
                requiredAmount -= toConsume;
                amountConsumed += toConsume;

                if (requiredAmount <= 0) {
                    break;
                }
            }
        }

        return amountConsumed;
    }

    private static void consumeFromInventory(Ingredient ingredient, PlayerInventory inventory, int requiredAmount) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (ingredient.test(stack) && !stack.isEmpty()) {
                int toConsume = Math.min(requiredAmount, stack.getCount());
                stack.decrement(toConsume);
                requiredAmount -= toConsume;

                if (requiredAmount <= 0) {
                    break;
                }
            }
        }
    }

    private static void clearGridAndSync(CraftingScreenHandler handler, ServerPlayerEntity player) {
        RecipeInputInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getInput();

        for (int i = 0; i < gridInventory.size(); i++) {
            ItemStack currentStack = gridInventory.getStack(i);
            if (currentStack.isEmpty()) {
                continue;
            }

            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                    handler.syncId, i + 1, 0, currentStack
            ));
        }
    }
}