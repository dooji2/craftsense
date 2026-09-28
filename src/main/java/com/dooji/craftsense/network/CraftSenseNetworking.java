package com.dooji.craftsense.network;

import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class CraftSenseNetworking {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(CraftItemPayload.ID, CraftItemPayload.CODEC, (payload, context) -> handleCraftItemPayload(payload, (ServerPlayer) context.player()));
        registrar.playToClient(RecordCraftPayload.ID, RecordCraftPayload.CODEC, (payload, context) -> CraftSenseClientNetworking.recordCraft(payload));
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayer player) {
        ResourceLocation recipeId = ResourceLocation.parse(payload.recipeId());

        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Optional<CraftingRecipe> recipeOptional = recipeManager.byKey(recipeId)
                .map(recipeEntry -> recipeEntry.value())
                .filter(recipe -> recipe instanceof CraftingRecipe)
                .map(recipe -> (CraftingRecipe) recipe);

        if (recipeOptional.isEmpty()) {
            return;
        }

        CraftingRecipe recipe = recipeOptional.get();
        RecipeBookMenu<?, ?> handler;
        CraftingContainer gridInventory;
        if (player.containerMenu instanceof InventoryMenu playerHandler) {
            handler = playerHandler;
            gridInventory = playerHandler.getCraftSlots();
        } else if (player.containerMenu instanceof CraftingMenu craftingHandler) {
            handler = craftingHandler;
            gridInventory = ((CraftingScreenHandlerAccessor) craftingHandler).getInput();
        } else {
            return;
        }

        if (!recipe.canCraftInDimensions(gridInventory.getWidth(), gridInventory.getHeight()) || recipe.getIngredients().isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> sources = new ArrayList<>();
        for (int i = 0; i < gridInventory.getContainerSize(); i++) {
            sources.add(gridInventory.getItem(i));
        }
        sources.add(handler.getCarried());
        sources.addAll(inventory.items);

        int[] used = new int[sources.size()];
        int[] selected = new int[recipe.getIngredients().size()];
        Arrays.fill(selected, -1);
        ItemStack resultStack = selectIngredients(recipe, sources, used, selected, 0, gridInventory, player.level(), inventory, payload.isShiftPressed());
        if (resultStack.isEmpty()) {
            return;
        }

        CraftingInput input = createRecipeInput(recipe, sources, selected, gridInventory);
        NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);

        for (int i = 0; i < gridInventory.getContainerSize(); i++) {
            if (used[i] > 0) {
                gridInventory.removeItem(i, used[i]);
            }
        }

        int cursorIndex = gridInventory.getContainerSize();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventory.getItem(i).shrink(used[cursorIndex + 1 + i]);
        }

        ItemStack cursorStack = handler.getCarried().copy();
        cursorStack.shrink(used[cursorIndex]);
        if (payload.isShiftPressed()) {
            handler.setCarried(cursorStack);
            inventory.add(resultStack.copy());
        } else if (cursorStack.isEmpty()) {
            handler.setCarried(resultStack.copy());
        } else {
            cursorStack.grow(resultStack.getCount());
            handler.setCarried(cursorStack);
        }

        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                inventory.placeItemBackInInventory(remainder.copy());
            }
        }

        inventory.setChanged();
        handler.slotsChanged(gridInventory);
        handler.broadcastChanges();
        resultStack.onCraftedBy(player.level(), player, resultStack.getCount());
    }

    private static ItemStack selectIngredients(CraftingRecipe recipe, List<ItemStack> sources, int[] used, int[] selected, int index, CraftingContainer gridInventory, Level world, Inventory inventory, boolean shiftPressed) {
        if (index == selected.length) {
            CraftingInput input = createRecipeInput(recipe, sources, selected, gridInventory);
            if (!recipe.matches(input, world)) {
                return ItemStack.EMPTY;
            }

            ItemStack resultStack = recipe.assemble(input, world.registryAccess());
            if (resultStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int cursorIndex = gridInventory.getContainerSize();
            if (shiftPressed) {
                return canInsertResult(inventory, resultStack, used, cursorIndex + 1) ? resultStack : ItemStack.EMPTY;
            }

            ItemStack cursorStack = sources.get(cursorIndex).copy();
            cursorStack.shrink(used[cursorIndex]);
            if (cursorStack.isEmpty() || ItemStack.isSameItemSameComponents(cursorStack, resultStack) && cursorStack.getCount() + resultStack.getCount() <= cursorStack.getMaxStackSize()) {
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

    private static CraftingInput createRecipeInput(CraftingRecipe recipe, List<ItemStack> sources, int[] selected, CraftingContainer gridInventory) {
        int width = gridInventory.getWidth();
        int recipeWidth = recipe instanceof ShapedRecipe shapedRecipe ? shapedRecipe.getWidth() : width;
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < gridInventory.getContainerSize(); i++) {
            stacks.add(ItemStack.EMPTY);
        }

        for (int i = 0; i < selected.length; i++) {
            if (selected[i] >= 0) {
                int slot = i / recipeWidth * width + i % recipeWidth;
                stacks.set(slot, sources.get(selected[i]).copyWithCount(1));
            }
        }

        return CraftingInput.of(width, gridInventory.getHeight(), stacks);
    }

    private static boolean canInsertResult(Inventory inventory, ItemStack resultStack, int[] used, int inventoryStart) {
        int remaining = resultStack.getCount();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            int count = stack.getCount() - used[inventoryStart + i];
            if (count > 0 && ItemStack.isSameItemSameComponents(stack, resultStack)) {
                remaining -= inventory.getMaxStackSize(stack) - count;
            } else if (count == 0) {
                remaining -= inventory.getMaxStackSize(resultStack);
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }
}