package com.dooji.craftsense.network;

import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CraftSenseNetworking {
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(new Identifier("craftsense", "craft_item"), (server, player, handler, buf, responseSender) -> {
            CraftItemPayload payload = CraftItemPayload.read(buf);
            server.execute(() -> handleCraftItemPayload(payload, player));
        });
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayerEntity player) {
        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Identifier recipeId = new Identifier(payload.recipeId());

        Optional<CraftingRecipe> recipeOptional = recipeManager.get(recipeId)
                .filter(recipe -> recipe instanceof CraftingRecipe)
                .map(recipe -> (CraftingRecipe) recipe);

        if (recipeOptional.isPresent()) {
            CraftingRecipe recipe = recipeOptional.get();
            PlayerInventory inventory = player.getInventory();

            if (player.currentScreenHandler instanceof CraftingScreenHandler handler) {
                CraftingInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getInput();
                ItemStack resultStack = recipe.getOutput().copy();
                ItemStack cursorStack = handler.getCursorStack();

                CraftingRecipe recipeToUse = selectCraftableVariant(recipeManager, recipe, resultStack, inventory, gridInventory, cursorStack);
                if (recipeToUse == null) {
                    return;
                }

                if (payload.isShiftPressed()) {
                    if (!placeInInventoryOrCursor(inventory, resultStack, player)) {
                        return;
                    }
                } else {
                    if (cursorStack.isEmpty()) {
                        handler.setCursorStack(resultStack);
                        // sendSlotUpdate(player, handler.syncId, -1, resultStack);
                    } else if (areStacksEqualWithComponents(cursorStack, resultStack)) {
                        cursorStack.increment(resultStack.getCount());
                        handler.setCursorStack(cursorStack);
                        // sendSlotUpdate(player, handler.syncId, -1, cursorStack);
                    } else {
                        return;
                    }
                }

                if (hasAllIngredients(inventory, gridInventory, recipeToUse, cursorStack)) {
                    consumeIngredients(recipeToUse, gridInventory, inventory, cursorStack);
                    clearGridAndSync(handler, player);
                }
            }
        }
    }

    private static CraftingRecipe selectCraftableVariant(RecipeManager recipeManager, CraftingRecipe requestedRecipe, ItemStack desiredResult, PlayerInventory inventory, CraftingInventory gridInventory, ItemStack cursorStack) {
        if (desiredResult.isEmpty()) {
            return null;
        }

        List<CraftingRecipe> candidates = recipeManager.listAllOfType(RecipeType.CRAFTING).stream()
                .filter(r -> areStacksEqualWithComponents(r.getOutput().copy(), desiredResult))
                .toList();

        for (CraftingRecipe candidate : candidates) {
            boolean ok = hasAllIngredients(inventory, gridInventory, candidate, cursorStack);
            if (ok) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.areItemsEqual(stack1, stack2)) {
            return false;
        }

        if (stack1.hasNbt() && stack2.hasNbt()) {
            return Objects.equals(stack1.getNbt(), stack2.getNbt());
        }

        return !stack1.hasNbt() && !stack2.hasNbt();
    }

    private static boolean hasAllIngredients(PlayerInventory inventory, CraftingInventory gridInventory, CraftingRecipe recipe, ItemStack cursorStack) {
        for (var ingredient : recipe.getIngredients()) {
            boolean found = false;
            for (int i = 0; i < gridInventory.size(); i++) {
                if (ingredient.test(gridInventory.getStack(i))) {
                    found = true;
                    break;
                }
            }

            if (!found && ingredient.test(cursorStack)) {
                found = true;
            }

            if (!found && !findInInventory(inventory, ingredient)) {
                return false;
            }
        }

        return true;
    }

    private static boolean findInInventory(PlayerInventory inventory, Ingredient ingredient) {
        for (int i = 0; i < inventory.size(); i++) {
            if (ingredient.test(inventory.getStack(i))) return true;
        }

        return false;
    }

    private static void consumeIngredients(CraftingRecipe recipe, CraftingInventory gridInventory, PlayerInventory inventory, ItemStack cursorStack) {
        Map<Ingredient, Integer> ingredientsNeeded = new HashMap<>();

        for (var ingredient : recipe.getIngredients()) {
            ingredientsNeeded.put(ingredient, ingredientsNeeded.getOrDefault(ingredient, 0) + 1);
        }

        for (var entry : ingredientsNeeded.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int requiredAmount = entry.getValue();

            int consumedFromGrid = consumeFromGrid(ingredient, gridInventory, requiredAmount);
            requiredAmount -= consumedFromGrid;

            if (requiredAmount > 0 && ingredient.test(cursorStack)) {
                int toConsume = Math.min(requiredAmount, cursorStack.getCount());
                cursorStack.decrement(toConsume);
                requiredAmount -= toConsume;
            }

            if (requiredAmount > 0) {
                consumeFromInventory(ingredient, inventory, requiredAmount);
            }
        }
    }

    private static int consumeFromGrid(Ingredient ingredient, CraftingInventory gridInventory, int requiredAmount) {
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
        CraftingInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getInput();

        for (int i = 0; i < gridInventory.size(); i++) {
            ItemStack currentStack = gridInventory.getStack(i);
            if (currentStack.isEmpty()) {
                continue;
            }

            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, 0, i + 1, currentStack));
        }
    }

    private static boolean placeInInventoryOrCursor(PlayerInventory inventory, ItemStack stack, ServerPlayerEntity player) {
        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            ItemStack slotStack = inventory.getStack(i);

            if (ItemStack.areItemsEqual(slotStack, stack) && slotStack.getCount() < slotStack.getMaxCount()) {
                int transferable = Math.min(stack.getCount(), slotStack.getMaxCount() - slotStack.getCount());
                slotStack.increment(transferable);
                stack.decrement(transferable);
                sendSlotUpdate(player, 0, i, slotStack);

                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            ItemStack slotStack = inventory.getStack(i);
            if (slotStack.isEmpty()) {
                inventory.setStack(i, stack);
                sendSlotUpdate(player, 0, i, stack);
                return true;
            }
        }

        return false;
    }

    private static void sendSlotUpdate(ServerPlayerEntity player, int syncId, int slot, ItemStack stack) {
        if (slot == -1) {
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(syncId, -1, 0, stack));
        } else {
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(syncId, 0, slot, stack));
        }
    }
}