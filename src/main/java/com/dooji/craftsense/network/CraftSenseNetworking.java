package com.dooji.craftsense.network;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.mixin.CraftingMenuAccessor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class CraftSenseNetworking {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CraftSenseNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("craftsense");
        registrar.playToServer(CraftItemPayload.TYPE, CraftItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCraftItemPayload(payload, (ServerPlayer) context.player())));
        registrar.playToClient(RecordCraftPayload.TYPE, RecordCraftPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRecordCraftPayload(payload)));
    }

    private static void handleRecordCraftPayload(RecordCraftPayload payload) {
        CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
        String category = CategoryManager.getCategory(payload.itemStack().getItem());
        habitsConfig.recordCraft(category, payload.itemStack().getItem().getDescriptionId());
    }

    private static void handleCraftItemPayload(CraftItemPayload payload, ServerPlayer player) {
        RecipeManager recipeManager = player.getServer().getRecipeManager();

        Optional<CraftingRecipe> recipeOptional = recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream()
                .filter(h -> h.id().toString().equals(payload.recipeId()))
                .map(RecipeHolder::value)
                .findFirst();

        if (recipeOptional.isPresent()) {
            CraftingRecipe recipe = recipeOptional.get();
            Inventory inventory = player.getInventory();

            if (player.containerMenu instanceof CraftingMenu handler) {
                CraftingContainer gridInventory = ((CraftingMenuAccessor) handler).getCraftSlots();
                RegistryAccess registries = player.getServer().registryAccess();
                ItemStack resultStack = recipe.getResultItem(registries).copy();
                ItemStack cursorStack = handler.getCarried();

                CraftingRecipe recipeToUse = selectCraftableVariant(recipeManager, resultStack, inventory, gridInventory, cursorStack, registries);
                if (recipeToUse == null) {
                    return;
                }

                if (payload.isShiftPressed()) {
                    if (!placeInInventoryOrCursor(inventory, resultStack, player)) {
                        return;
                    }
                } else {
                    if (cursorStack.isEmpty()) {
                        handler.setCarried(resultStack);
                    } else if (areStacksEqualWithComponents(cursorStack, resultStack)) {
                        cursorStack.grow(resultStack.getCount());
                        handler.setCarried(cursorStack);
                    } else {
                        return;
                    }
                }

                if (hasAllIngredients(inventory, gridInventory, recipe, cursorStack)) {
                    consumeIngredients(recipe, gridInventory, inventory, cursorStack);
                    clearGridAndSync(handler, player);
                }
            }
        }
    }

    private static CraftingRecipe selectCraftableVariant(RecipeManager recipeManager, ItemStack desiredResult, Inventory inventory, CraftingContainer gridInventory, ItemStack cursorStack, RegistryAccess registries) {
        if (desiredResult.isEmpty()) {
            return null;
        }

        return recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream()
                .map(RecipeHolder::value)
                .filter(r -> areStacksEqualWithComponents(r.getResultItem(registries).copy(), desiredResult))
                .filter(r -> hasAllIngredients(inventory, gridInventory, r, cursorStack))
                .findFirst()
                .orElse(null);
    }

    private static boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.isSameItem(stack1, stack2)) {
            return false;
        }
        return Objects.equals(stack1.getComponents(), stack2.getComponents());
    }

    private static boolean hasAllIngredients(Inventory inventory, CraftingContainer gridInventory, CraftingRecipe recipe, ItemStack cursorStack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            boolean found = false;

            for (int i = 0; i < gridInventory.getContainerSize(); i++) {
                if (ingredient.test(gridInventory.getItem(i))) {
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

    private static boolean findInInventory(Inventory inventory, Ingredient ingredient) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ingredient.test(inventory.getItem(i))) return true;
        }
        return false;
    }

    private static void consumeIngredients(CraftingRecipe recipe, CraftingContainer gridInventory, Inventory inventory, ItemStack cursorStack) {
        Map<Ingredient, Integer> ingredientsNeeded = new HashMap<>();

        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredientsNeeded.put(ingredient, ingredientsNeeded.getOrDefault(ingredient, 0) + 1);
        }

        for (Map.Entry<Ingredient, Integer> entry : ingredientsNeeded.entrySet()) {
            Ingredient ingredient = entry.getKey();
            int requiredAmount = entry.getValue();

            int consumedFromGrid = consumeFromGrid(ingredient, gridInventory, requiredAmount);
            requiredAmount -= consumedFromGrid;

            if (requiredAmount > 0 && ingredient.test(cursorStack)) {
                int toConsume = Math.min(requiredAmount, cursorStack.getCount());
                cursorStack.shrink(toConsume);
                requiredAmount -= toConsume;
            }

            if (requiredAmount > 0) {
                consumeFromInventory(ingredient, inventory, requiredAmount);
            }
        }
    }

    private static int consumeFromGrid(Ingredient ingredient, CraftingContainer gridInventory, int requiredAmount) {
        int amountConsumed = 0;

        for (int i = 0; i < gridInventory.getContainerSize(); i++) {
            ItemStack stack = gridInventory.getItem(i);
            if (ingredient.test(stack) && !stack.isEmpty()) {
                int toConsume = Math.min(requiredAmount, stack.getCount());
                stack.shrink(toConsume);
                requiredAmount -= toConsume;
                amountConsumed += toConsume;

                if (requiredAmount <= 0) {
                    break;
                }
            }
        }

        return amountConsumed;
    }

    private static void consumeFromInventory(Ingredient ingredient, Inventory inventory, int requiredAmount) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);

            if (ingredient.test(stack) && !stack.isEmpty()) {
                int toConsume = Math.min(requiredAmount, stack.getCount());
                stack.shrink(toConsume);
                requiredAmount -= toConsume;

                if (requiredAmount <= 0) {
                    break;
                }
            }
        }
    }

    private static void clearGridAndSync(CraftingMenu handler, ServerPlayer player) {
        CraftingContainer gridInventory = ((CraftingMenuAccessor) handler).getCraftSlots();

        for (int i = 0; i < gridInventory.getContainerSize(); i++) {
            ItemStack currentStack = gridInventory.getItem(i);
            if (currentStack.isEmpty()) {
                continue;
            }
            player.connection.send(new ClientboundContainerSetSlotPacket(handler.containerId, 0, i + 1, currentStack));
        }
    }

    private static boolean placeInInventoryOrCursor(Inventory inventory, ItemStack stack, ServerPlayer player) {
        for (int i = 0; i < 36; i++) {
            ItemStack slotStack = inventory.getItem(i);

            if (ItemStack.isSameItem(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                int transferable = Math.min(stack.getCount(), slotStack.getMaxStackSize() - slotStack.getCount());
                slotStack.grow(transferable);
                stack.shrink(transferable);
                sendSlotUpdate(player, 0, i, slotStack);

                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack slotStack = inventory.getItem(i);

            if (slotStack.isEmpty()) {
                inventory.setItem(i, stack);
                sendSlotUpdate(player, 0, i, stack);
                return true;
            }
        }

        return false;
    }

    private static void sendSlotUpdate(ServerPlayer player, int syncId, int slot, ItemStack stack) {
        if (slot == -1) {
            player.connection.send(new ClientboundContainerSetSlotPacket(syncId, -1, 0, stack));
        } else {
            player.connection.send(new ClientboundContainerSetSlotPacket(syncId, 0, slot, stack));
        }
    }
}
