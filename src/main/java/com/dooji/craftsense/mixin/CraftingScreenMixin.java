package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.network.CraftSenseNetworking;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector2i;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin({CraftingScreen.class, InventoryScreen.class})
public abstract class CraftingScreenMixin {

    @Unique
    private int resultSlotX;

    @Unique
    private int resultSlotY;

    @Unique
    private String lastGridHash = "";

    @Unique
    private Optional<CraftingRecipe> cachedLastCraftedRecipe = Optional.empty();

    @Unique
    private Optional<CraftingRecipe> cachedSuggestedRecipe = Optional.empty();

    @Unique
    private long lastMouseClickTime = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (((RecipeUpdateListener) (Object) this).getRecipeBookComponent().isVisible()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Inventory playerInventory = client.player.getInventory();
        Level world = client.level;

        RecipeBookMenu<?> handler = (RecipeBookMenu<?>) ((AbstractContainerScreen<?>) (Object) this).getMenu();
        CraftingContainer input;
        if (handler instanceof InventoryMenu playerHandler) {
            input = (CraftingContainer) playerHandler.slots.get(1).container;
        } else {
            input = ((CraftingScreenHandlerAccessor) handler).getInput();
        }

        ItemStack cursorStack = handler.getCarried();
        CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
        String currentStateHash = predictor.calculateInputHash(input, playerInventory, cursorStack);

        if (!currentStateHash.equals(lastGridHash)) {
            lastGridHash = currentStateHash;
            cachedLastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, cursorStack, world);

            if (cachedLastCraftedRecipe.isEmpty()) {
                cachedSuggestedRecipe = predictor.suggestRecipe(input, playerInventory, cursorStack, world);
            } else {
                cachedSuggestedRecipe = Optional.empty();
            }
        }

        int screenX = ((HandledScreenAccessor) this).getX();
        int screenY = ((HandledScreenAccessor) this).getY();
        Slot resultSlot = handler.slots.get(handler.getResultSlotIndex());
        resultSlotX = screenX + resultSlot.x;
        resultSlotY = screenY + resultSlot.y;

        if (cachedLastCraftedRecipe.isPresent()) {
            CraftingRecipe recipe = cachedLastCraftedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                if (CraftSense.configManager.isFirstTime() && client.screen instanceof CraftingScreen) {
                    renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.lastCraftedSuggest").getString(), resultSlotX, resultSlotY);
                }

                ItemStack resultStack = recipe.getResultItem(world.registryAccess());
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, true);
                return;
            }
        }

        if (cachedSuggestedRecipe.isPresent()) {
            CraftingRecipe recipe = cachedSuggestedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                if (CraftSense.configManager.isFirstTime() && client.screen instanceof CraftingScreen) {
                    renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.suggest").getString(), resultSlotX, resultSlotY);
                }

                ItemStack resultStack = recipe.getResultItem(world.registryAccess());
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

                if (recipe instanceof ShapedRecipe) {
                    renderShapedRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY, playerInventory, cursorStack, world, predictor);
                } else if (recipe instanceof ShapelessRecipe) {
                    renderShapelessRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY);
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        boolean isShiftPressed = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_LSHIFT);

        if (((RecipeUpdateListener) (Object) this).getRecipeBookComponent().isVisible()) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (isMouseOverSlot((int) mouseX, (int) mouseY, resultSlotX, resultSlotY)) {
            Minecraft client = Minecraft.getInstance();
            Inventory playerInventory = client.player.getInventory();
            Level world = client.level;

            RecipeBookMenu<?> handler = (RecipeBookMenu<?>) ((AbstractContainerScreen<?>) (Object) this).getMenu();

            CraftingContainer input;
            if (handler instanceof InventoryMenu playerHandler) {
                input = (CraftingContainer) playerHandler.slots.get(1).container;
            } else {
                input = ((CraftingScreenHandlerAccessor) handler).getInput();
            }

            CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
            Optional<CraftingRecipe> lastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, handler.getCarried(), world);

            Optional<CraftingRecipe> optionalRecipe = lastCraftedRecipe.isPresent()
                    ? lastCraftedRecipe
                    : predictor.suggestRecipe(input, playerInventory, handler.getCarried(), world);

            if (optionalRecipe.isPresent()) {
                if (currentTime - lastMouseClickTime < cooldownDuration) {
                    cir.setReturnValue(true);
                    return;
                }

                CraftingRecipe recipe = optionalRecipe.get();
                ResourceLocation recipeId = findRecipeId(world.getRecipeManager(), recipe);

                if (recipeId != null) {
                    CraftSenseNetworking.INSTANCE.sendToServer(new CraftItemPayload(recipeId.toString(), isShiftPressed));
                    lastMouseClickTime = currentTime;
                    if (CraftSense.configManager.isFirstTime() && client.screen instanceof CraftingScreen) {
                        CraftSense.configManager.toggleFirstTime();
                    }

                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Unique
    private void renderShapedRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, RecipeBookMenu<?> handler, int screenX, int screenY, int mouseX, int mouseY, Inventory playerInventory, ItemStack cursorStack, Level world, CraftingPredictor predictor) {
        ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
        ItemStack resultStack = recipe.getResultItem(world.registryAccess());
        renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

        int recipeWidth = shapedRecipe.getWidth();
        int recipeHeight = shapedRecipe.getHeight();
        List<Ingredient> ingredients = shapedRecipe.getIngredients();

        int bestOffsetX = -1;
        int bestOffsetY = -1;
        boolean bestMirrored = false;
        int bestScore = -1;

        for (int offsetX = 0; offsetX <= input.getWidth() - recipeWidth; offsetX++) {
            for (int offsetY = 0; offsetY <= input.getHeight() - recipeHeight; offsetY++) {
                Pair<Integer, Boolean> matchResult = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack, input), offsetX, offsetY);
                int alignmentScore = matchResult.getFirst();
                boolean mirrored = matchResult.getSecond();
                if (alignmentScore > bestScore) {
                    bestScore = alignmentScore;
                    bestOffsetX = offsetX;
                    bestOffsetY = offsetY;
                    bestMirrored = mirrored;
                }
            }
        }

        if (bestOffsetX != -1 && bestOffsetY != -1) {
            for (int recipeY = 0; recipeHeight > recipeY; recipeY++) {
                for (int recipeX = 0; recipeWidth > recipeX; recipeX++) {
                    int index = recipeY * recipeWidth + recipeX;
                    Ingredient ingredient = ingredients.get(bestMirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);

                    int gridX = bestOffsetX + recipeX;
                    int gridY = bestOffsetY + recipeY;

                    int gridIndex = gridY * input.getWidth() + gridX;
                    Slot slot = handler.slots.get(gridIndex + 1);
                    int slotX = screenX + slot.x;
                    int slotY = screenY + slot.y;

                    ItemStack[] matchingStacks = ingredient.getItems();
                    if (matchingStacks.length > 0) {
                        ItemStack ghostStack = matchingStacks[0];
                        renderGhostItem(context, ghostStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
                    }
                }
            }
        }
    }

    @Unique
    private void renderShapelessRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, RecipeBookMenu<?> handler, int screenX, int screenY, int mouseX, int mouseY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[][] usedGrid = new boolean[input.getHeight()][input.getWidth()];
        Map<Integer, Integer> placedItemCounts = new HashMap<>();

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                placedItemCounts.put(i, stack.getCount());
                int gridX = i % input.getWidth();
                int gridY = i / input.getWidth();
                usedGrid[gridY][gridX] = true;
            }
        }

        List<ItemStack> remainingIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;

            for (Map.Entry<Integer, Integer> entry : placedItemCounts.entrySet()) {
                int slotIndex = entry.getKey();
                int count = entry.getValue();
                ItemStack placedItem = input.getItem(slotIndex);

                if (ingredient.test(placedItem)) {
                    matched = true;

                    if (count > 1) {
                        placedItemCounts.put(slotIndex, count - 1);
                    } else {
                        placedItemCounts.remove(slotIndex);
                    }
                    break;
                }
            }

            if (!matched) {
                remainingIngredients.add(ingredient.getItems()[0]);
            }
        }

        for (Map.Entry<Integer, Integer> entry : placedItemCounts.entrySet()) {
            int slotIndex = entry.getKey();
            Slot slot = handler.slots.get(slotIndex + 1);
            int slotX = screenX + slot.x;
            int slotY = screenY + slot.y;
            renderGhostItem(context, input.getItem(slotIndex), slotX, slotY, 0.2f, mouseX, mouseY, false);
        }

        int ingredientIndex = 0;
        for (int gridY = 0; gridY < input.getHeight(); gridY++) {
            for (int gridX = 0; gridX < input.getWidth(); gridX++) {
                if (usedGrid[gridY][gridX] || ingredientIndex >= remainingIngredients.size()) {
                    continue;
                }

                int gridIndex = gridY * input.getWidth() + gridX;
                Slot slot = handler.slots.get(gridIndex + 1);
                int slotX = screenX + slot.x;
                int slotY = screenY + slot.y;

                ItemStack ingredientStack = remainingIngredients.get(ingredientIndex++);
                renderGhostItem(context, ingredientStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
            }
        }
    }

    @Unique
    private void renderGhostItem(GuiGraphics context, ItemStack stack, int x, int y, float opacity, int mouseX, int mouseY, boolean isLastCrafted) {
        context.pose().pushPose();
        context.pose().translate(0, 0, -100);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.renderFakeItem(stack, x, y);

        drawTransparentRectangle(context, x, y, x + 16, y + 16, 200, opacity);

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<FormattedCharSequence> tooltip = new ArrayList<>();
            tooltip.add(stack.getHoverName().getVisualOrderText());
            if (isLastCrafted) {
                tooltip.add(Component.translatable("tooltip.craftsense.last_crafted_item").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC).getVisualOrderText());
            }
            context.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }

        RenderSystem.disableBlend();
        context.pose().popPose();
    }

    @Unique
    private void drawTransparentRectangle(GuiGraphics context, int x1, int y1, int x2, int y2, int z, float alpha) {
        Matrix4f matrix = context.pose().last().pose();
        VertexConsumer vertexConsumer = context.bufferSource().getBuffer(RenderType.gui());

        vertexConsumer.vertex(matrix, x1, y1, z).color(255, 255, 255, (int)(alpha * 255)).endVertex();
        vertexConsumer.vertex(matrix, x1, y2, z).color(255, 255, 255, (int)(alpha * 255)).endVertex();
        vertexConsumer.vertex(matrix, x2, y2, z).color(255, 255, 255, (int)(alpha * 255)).endVertex();
        vertexConsumer.vertex(matrix, x2, y1, z).color(255, 255, 255, (int)(alpha * 255)).endVertex();

        context.flush();
    }

    @Unique
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    @Unique
    @Nullable
    private ResourceLocation findRecipeId(RecipeManager recipeManager, CraftingRecipe targetRecipe) {
        List<? extends Recipe<?>> craftingRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        for (Recipe<?> recipe : craftingRecipes) {
            if (recipe instanceof CraftingRecipe && recipe == targetRecipe) {
                return recipe.getId();
            }
        }
        return null;
    }

    @Unique
    private void renderTooltip(GuiGraphics context, String title, String description, int x, int y) {
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(title).withStyle(ChatFormatting.WHITE).getVisualOrderText());

        String[] descriptionLines = description.split("\n");
        for (String line : descriptionLines) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        
        ClientTooltipPositioner fixedPositioner = (screenWidth, screenHeight, tooltipX, tooltipY, tooltipWidth, tooltipHeight) -> new Vector2i(tooltipX, tooltipY);
        context.renderTooltip(Minecraft.getInstance().font, tooltip, fixedPositioner, x + 30, y - 7);
    }
}