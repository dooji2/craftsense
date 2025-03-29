package com.dooji.craftsense.network;

import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.mixin.RecipeManagerAccessor;
import com.dooji.craftsense.mixin.ShapedRecipeAccessor;
import com.dooji.craftsense.mixin.ShapelessRecipeAccessor;
import com.dooji.craftsense.network.payloads.RecipesPayload;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.network.payloads.RecipesRequestPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class CraftSenseNetworking {
    public static void init() {
        PayloadTypeRegistry.playC2S().register(CraftItemPayload.ID, CraftItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RecipesRequestPayload.ID, RecipesRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecipesPayload.ID, RecipesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecordCraftPayload.ID, RecordCraftPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CraftItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                handleCraftItemPayload(payload.recipeId(), payload.isShiftPressed(), context.player());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RecipesRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                RecipeManager recipeManager = player.getServer().getRecipeManager();
                List<RecipeEntry<?>> recipes = ((ServerRecipeManager) recipeManager).values().stream().collect(Collectors.toList());
                Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey = ((RecipeManagerAccessor) recipeManager).getRecipesByKey();

                ServerPlayNetworking.getSender(player).sendPacket(new RecipesPayload(recipes, recipesByKey));
            });
        });
    }

    private static void handleCraftItemPayload(String recipeIdStr, Boolean isShiftPressed, ServerPlayerEntity player) {
        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Identifier recipeId = Identifier.of(recipeIdStr);
        Optional<RecipeEntry<?>> recipeEntryOptional = ((ServerRecipeManager) recipeManager).values().stream()
                .filter(entry -> entry.id().getValue().equals(recipeId))
                .findFirst();

        if (recipeEntryOptional.isPresent()) {
            Recipe<?> recipe = recipeEntryOptional.get().value();

            if (recipe instanceof CraftingRecipe craftingRecipe) {
                PlayerInventory inventory = player.getInventory();

                if (player.currentScreenHandler instanceof CraftingScreenHandler handler) {
                    RecipeInputInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
                    ItemStack resultStack = getRecipeResult(craftingRecipe);
                    ItemStack cursorStack = handler.getCursorStack();

                    if (isShiftPressed) {
                        if (!placeInInventoryOrCursor(inventory, resultStack, player)) {
                            return;
                        }
                    } else {
                        if (cursorStack.isEmpty()) {
                            handler.setCursorStack(resultStack);
                            // why was i updating the output slot um
                            // sendSlotUpdate(player, handler.syncId, -1, resultStack);
                        } else if (areStacksEqualWithComponents(cursorStack, resultStack)) {
                            cursorStack.increment(resultStack.getCount());
                            handler.setCursorStack(cursorStack);
                            // sendSlotUpdate(player, handler.syncId, -1, cursorStack);
                        } else {
                            return;
                        }
                    }

                    if (hasAllIngredients(inventory, gridInventory, craftingRecipe, cursorStack)) {
                        consumeIngredients(craftingRecipe, gridInventory, inventory, cursorStack);
                        clearGridAndSync(handler, player);
                    }
                }
            }
        }
    }

    private static ItemStack getRecipeResult(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipeAccessor shaped) {
            return shaped.getResult().copy();
        } else if (recipe instanceof ShapelessRecipeAccessor shapeless) {
            return shapeless.getResult().copy();
        } else {
            return ItemStack.EMPTY;
        }
    }

    private static boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.areItemsEqual(stack1, stack2)) {
            return false;
        }

        return Objects.equals(stack1.getComponents(), stack2.getComponents());
    }

    private static boolean hasAllIngredients(PlayerInventory inventory, RecipeInputInventory gridInventory, CraftingRecipe recipe, ItemStack cursorStack) {
        List<Ingredient> ingredients = recipe.getIngredientPlacement().getIngredients();

        for (Ingredient ingredient : ingredients) {
            boolean found = false;

            for (int i = 0; i < gridInventory.size(); i++) {
                ItemStack stack = gridInventory.getStack(i);

                if (ingredient.test(stack)) {
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
            ItemStack stack = inventory.getStack(i);

            if (ingredient.test(stack)) {
                return true;
            }
        }

        return false;
    }

    private static void consumeIngredients(CraftingRecipe recipe, RecipeInputInventory gridInventory, PlayerInventory inventory, ItemStack cursorStack) {
        Map<Ingredient, Integer> ingredientsNeeded = new java.util.HashMap<>();
        List<Ingredient> ingredients = recipe.getIngredientPlacement().getIngredients();

        for (Ingredient ingredient : ingredients) {
            ingredientsNeeded.put(ingredient, ingredientsNeeded.getOrDefault(ingredient, 0) + 1);
        }

        for (Map.Entry<Ingredient, Integer> entry : ingredientsNeeded.entrySet()) {
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
        RecipeInputInventory gridInventory = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
        
        for (int i = 0; i < gridInventory.size(); i++) {
            ItemStack currentStack = gridInventory.getStack(i);
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