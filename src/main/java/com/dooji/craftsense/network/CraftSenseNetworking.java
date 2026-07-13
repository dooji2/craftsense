package com.dooji.craftsense.network;

import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.*;

public class CraftSenseNetworking {
    public static void init() {
        PayloadTypeRegistry.playC2S().register(CraftItemPayload.ID, CraftItemPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecordCraftPayload.ID, RecordCraftPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CraftItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleCraftItemPayload(payload, context.player()));
        });
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayerEntity player) {
        Identifier recipeId = Identifier.of(payload.recipeId());

        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Optional<CraftingRecipe> recipeOptional = recipeManager.get(recipeId)
                .map(recipeEntry -> recipeEntry.value())
                .filter(recipe -> recipe instanceof CraftingRecipe)
                .map(recipe -> (CraftingRecipe) recipe);

        if (recipeOptional.isEmpty()) {
            return;
        }

        CraftingRecipe recipe = recipeOptional.get();
        AbstractRecipeScreenHandler<?, ?> handler;
        RecipeInputInventory gridInventory;
        if (player.currentScreenHandler instanceof PlayerScreenHandler playerHandler) {
            handler = playerHandler;
            gridInventory = playerHandler.getCraftingInput();
        } else if (player.currentScreenHandler instanceof CraftingScreenHandler craftingHandler) {
            handler = craftingHandler;
            gridInventory = ((CraftingScreenHandlerAccessor) craftingHandler).getInput();
        } else {
            return;
        }

        if (!recipe.fits(gridInventory.getWidth(), gridInventory.getHeight()) || recipe.getIngredients().isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        List<ItemStack> sources = new ArrayList<>();
        for (int i = 0; i < gridInventory.size(); i++) {
            sources.add(gridInventory.getStack(i));
        }
        sources.add(handler.getCursorStack());
        sources.addAll(inventory.main);

        int[] used = new int[sources.size()];
        int[] selected = new int[recipe.getIngredients().size()];
        Arrays.fill(selected, -1);
        ItemStack resultStack = selectIngredients(recipe, sources, used, selected, 0, gridInventory, player.getWorld(), inventory, payload.isShiftPressed());
        if (resultStack.isEmpty()) {
            return;
        }

        CraftingRecipeInput input = createRecipeInput(recipe, sources, selected, gridInventory);
        DefaultedList<ItemStack> remainders = recipe.getRemainder(input);

        for (int i = 0; i < gridInventory.size(); i++) {
            if (used[i] > 0) {
                gridInventory.removeStack(i, used[i]);
            }
        }

        int cursorIndex = gridInventory.size();
        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            inventory.getStack(i).decrement(used[cursorIndex + 1 + i]);
        }

        ItemStack cursorStack = handler.getCursorStack().copy();
        cursorStack.decrement(used[cursorIndex]);
        if (payload.isShiftPressed()) {
            handler.setCursorStack(cursorStack);
            inventory.insertStack(resultStack.copy());
        } else if (cursorStack.isEmpty()) {
            handler.setCursorStack(resultStack.copy());
        } else {
            cursorStack.increment(resultStack.getCount());
            handler.setCursorStack(cursorStack);
        }

        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                inventory.offerOrDrop(remainder.copy());
            }
        }

        inventory.markDirty();
        handler.onContentChanged(gridInventory);
        handler.sendContentUpdates();
        resultStack.onCraftByPlayer(player.getWorld(), player, resultStack.getCount());
    }

    private static ItemStack selectIngredients(CraftingRecipe recipe, List<ItemStack> sources, int[] used, int[] selected, int index, RecipeInputInventory gridInventory, World world, PlayerInventory inventory, boolean shiftPressed) {
        if (index == selected.length) {
            CraftingRecipeInput input = createRecipeInput(recipe, sources, selected, gridInventory);
            if (!recipe.matches(input, world)) {
                return ItemStack.EMPTY;
            }

            ItemStack resultStack = recipe.craft(input, world.getRegistryManager());
            if (resultStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int cursorIndex = gridInventory.size();
            if (shiftPressed) {
                return canInsertResult(inventory, resultStack, used, cursorIndex + 1) ? resultStack : ItemStack.EMPTY;
            }

            ItemStack cursorStack = sources.get(cursorIndex).copy();
            cursorStack.decrement(used[cursorIndex]);
            if (cursorStack.isEmpty() || ItemStack.areItemsAndComponentsEqual(cursorStack, resultStack) && cursorStack.getCount() + resultStack.getCount() <= cursorStack.getMaxCount()) {
                return resultStack;
            }
            return ItemStack.EMPTY;
        }

        Ingredient ingredient = recipe.getIngredients().get(index);
        if (ingredient.isEmpty()) {
            return selectIngredients(recipe, sources, used, selected, index + 1, gridInventory, world, inventory, shiftPressed);
        }

        for (int i = 0; i < sources.size(); i++) {
            if (used[i] < sources.get(i).getCount() && ingredient.test(sources.get(i))) {
                used[i]++;
                selected[index] = i;
                ItemStack resultStack = selectIngredients(recipe, sources, used, selected, index + 1, gridInventory, world, inventory, shiftPressed);
                if (!resultStack.isEmpty()) {
                    return resultStack;
                }

                used[i]--;
                selected[index] = -1;
            }
        }

        return ItemStack.EMPTY;
    }

    private static CraftingRecipeInput createRecipeInput(CraftingRecipe recipe, List<ItemStack> sources, int[] selected, RecipeInputInventory gridInventory) {
        int width = gridInventory.getWidth();
        int recipeWidth = recipe instanceof ShapedRecipe shapedRecipe ? shapedRecipe.getWidth() : width;
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < gridInventory.size(); i++) {
            stacks.add(ItemStack.EMPTY);
        }

        for (int i = 0; i < selected.length; i++) {
            if (selected[i] >= 0) {
                int slot = i / recipeWidth * width + i % recipeWidth;
                stacks.set(slot, sources.get(selected[i]).copyWithCount(1));
            }
        }

        return CraftingRecipeInput.create(width, gridInventory.getHeight(), stacks);
    }

    private static boolean canInsertResult(PlayerInventory inventory, ItemStack resultStack, int[] used, int inventoryStart) {
        int remaining = resultStack.getCount();
        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            ItemStack stack = inventory.getStack(i);
            int count = stack.getCount() - used[inventoryStart + i];
            if (count > 0 && ItemStack.areItemsAndComponentsEqual(stack, resultStack)) {
                remaining -= inventory.getMaxCount(stack) - count;
            } else if (count == 0) {
                remaining -= inventory.getMaxCount(resultStack);
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }
}