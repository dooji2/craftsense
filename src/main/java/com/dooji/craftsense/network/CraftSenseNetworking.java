package com.dooji.craftsense.network;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.mixin.CraftingScreenHandlerAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.core.NonNullList;

import java.util.*;

public class CraftSenseNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation NETWORK_CHANNEL = new ResourceLocation(CraftSense.MOD_ID, "main");
    public static SimpleChannel INSTANCE;

    public static void init() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(NETWORK_CHANNEL)
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(v -> true)
                .serverAcceptedVersions(v -> true)
                .simpleChannel();

        INSTANCE.messageBuilder(CraftItemPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CraftItemPayload::write)
                .decoder(CraftItemPayload::read)
                .consumerNetworkThread((payload, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> handleCraftItemPayload(payload, context.getSender()));
                    context.setPacketHandled(true);
                })
                .add();

        INSTANCE.messageBuilder(RecordCraftPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecordCraftPayload::write)
                .decoder(RecordCraftPayload::read)
                .consumerNetworkThread((payload, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> CraftSenseClientNetworking.recordCraft(payload));
                    context.setPacketHandled(true);
                })
                .add();
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayer player) {
        ResourceLocation recipeId = new ResourceLocation(payload.recipeId());

        RecipeManager recipeManager = player.getServer().getRecipeManager();
        Optional<CraftingRecipe> recipeOptional = recipeManager.byKey(recipeId)
                .filter(recipe -> recipe instanceof CraftingRecipe)
                .map(recipe -> (CraftingRecipe) recipe);

        if (recipeOptional.isEmpty()) {
            return;
        }

        CraftingRecipe recipe = recipeOptional.get();
        RecipeBookMenu<?> handler;
        CraftingContainer gridInventory;
        if (player.containerMenu instanceof InventoryMenu playerHandler) {
            handler = playerHandler;
            gridInventory = (CraftingContainer) playerHandler.slots.get(1).container;
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
        ItemStack resultStack = selectIngredients(recipe, sources, used, selected, 0, gridInventory, handler, player.level(), inventory, payload.isShiftPressed());
        if (resultStack.isEmpty()) {
            return;
        }

        CraftingContainer input = createRecipeInput(recipe, sources, selected, gridInventory, handler);
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

    private static ItemStack selectIngredients(CraftingRecipe recipe, List<ItemStack> sources, int[] used, int[] selected, int index, CraftingContainer gridInventory, RecipeBookMenu<?> handler, Level world, Inventory inventory, boolean shiftPressed) {
        if (index == selected.length) {
            CraftingContainer input = createRecipeInput(recipe, sources, selected, gridInventory, handler);
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
            if (cursorStack.isEmpty() || ItemStack.isSameItemSameTags(cursorStack, resultStack) && cursorStack.getCount() + resultStack.getCount() <= cursorStack.getMaxStackSize()) {
                return resultStack;
            }
            return ItemStack.EMPTY;
        }

        Ingredient ingredient = recipe.getIngredients().get(index);
        if (ingredient.isEmpty()) {
            return selectIngredients(recipe, sources, used, selected, index + 1, gridInventory, handler, world, inventory, shiftPressed);
        }

        for (int i = 0; i < sources.size(); i++) {
            if (used[i] < sources.get(i).getCount() && ingredient.test(sources.get(i))) {
                used[i]++;
                selected[index] = i;
                ItemStack resultStack = selectIngredients(recipe, sources, used, selected, index + 1, gridInventory, handler, world, inventory, shiftPressed);
                if (!resultStack.isEmpty()) {
                    return resultStack;
                }

                used[i]--;
                selected[index] = -1;
            }
        }

        return ItemStack.EMPTY;
    }

    private static CraftingContainer createRecipeInput(CraftingRecipe recipe, List<ItemStack> sources, int[] selected, CraftingContainer gridInventory, RecipeBookMenu<?> handler) {
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

        return new TransientCraftingContainer(handler, width, gridInventory.getHeight(), NonNullList.of(ItemStack.EMPTY, stacks.toArray(new ItemStack[0])));
    }

    private static boolean canInsertResult(Inventory inventory, ItemStack resultStack, int[] used, int inventoryStart) {
        int remaining = resultStack.getCount();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            int count = stack.getCount() - used[inventoryStart + i];
            if (count > 0 && ItemStack.isSameItemSameTags(stack, resultStack)) {
                remaining -= Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - count;
            } else if (count == 0) {
                remaining -= Math.min(resultStack.getMaxStackSize(), inventory.getMaxStackSize());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }
}