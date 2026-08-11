package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import org.joml.Vector2i;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class CraftingScreenMixin {

    @Unique
    private int resultSlotX;

    @Unique
    private int resultSlotY;

    @Unique
    private String lastGridHash = "";

    @Unique
    private Collection<RecipeHolder<?>> lastRecipes;

    @Unique
    private Optional<CraftingRecipe> cachedLastCraftedRecipe = Optional.empty();

    @Unique
    private Optional<CraftingRecipe> cachedSuggestedRecipe = Optional.empty();

    @Unique
    private long lastMouseClickTime = 0;

    @Inject(method = "renderSlots", at = @At("HEAD"))
    private void renderCraftingPrediction(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof CraftingScreen) && !(client.screen instanceof InventoryScreen)) {
            return;
        }

        if (((RecipeBookScreenAccessor) (Object) this).getRecipeBookWidget().isVisible()) {
            return;
        }

        Inventory playerInventory = client.player.getInventory();
        Level world = client.level;

        AbstractCraftingMenu handler = (AbstractCraftingMenu) ((AbstractContainerScreen<?>) (Object) this).getMenu();
        CraftingContainer input;
        if (handler instanceof InventoryMenu playerHandler) {
            input = playerHandler.getCraftSlots();
        } else {
            input = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
        }

        ItemStack cursorStack = handler.getCarried();
        CraftingPredictor predictor = CraftingPredictor.getInstance();
        String currentStateHash = predictor.calculateInputHash(input, playerInventory, cursorStack);
        Collection<RecipeHolder<?>> recipes = predictor.getRecipes();

        if (!currentStateHash.equals(lastGridHash) || recipes != lastRecipes) {
            lastGridHash = currentStateHash;
            lastRecipes = recipes;
            cachedLastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, cursorStack, world);
            
            if (cachedLastCraftedRecipe.isEmpty()) {
                cachedSuggestedRecipe = predictor.suggestRecipe(input, playerInventory, cursorStack, world);
            } else {
                cachedSuggestedRecipe = Optional.empty();
            }
        }

        int screenX = ((HandledScreenAccessor) this).getX();
        int screenY = ((HandledScreenAccessor) this).getY();
        Slot resultSlot = handler.getResultSlot();
        resultSlotX = screenX + resultSlot.x;
        resultSlotY = screenY + resultSlot.y;

        context.pose().pushMatrix();
        context.pose().translate(-screenX, -screenY);

        if (cachedLastCraftedRecipe.isPresent()) {
            CraftingRecipe recipe = cachedLastCraftedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                if (CraftSense.configManager.isFirstTime() && client.screen instanceof CraftingScreen) {
                    renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.lastCraftedSuggest").getString(), resultSlotX, resultSlotY);
                }

                ItemStack resultStack = CraftingPredictor.getResult(recipe, world);
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, true);
                context.pose().popMatrix();
                return;
            }
        }

        if (cachedSuggestedRecipe.isPresent()) {
            CraftingRecipe recipe = cachedSuggestedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                if (CraftSense.configManager.isFirstTime() && client.screen instanceof CraftingScreen) {
                    renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.suggest").getString(), resultSlotX, resultSlotY);
                }

                ItemStack resultStack = CraftingPredictor.getResult(recipe, world);
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

                if (recipe instanceof ShapedRecipe) {
                    renderShapedRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY, playerInventory, cursorStack, world, predictor);
                } else if (recipe instanceof ShapelessRecipe) {
                    renderShapelessRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY);
                }
            }
        }

        context.pose().popMatrix();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof CraftingScreen) && !(client.screen instanceof InventoryScreen)) {
            return;
        }

        boolean isShiftPressed = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT);

        if (((RecipeBookScreenAccessor) (Object) this).getRecipeBookWidget().isVisible()) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (isMouseOverSlot((int) click.x(), (int) click.y(), resultSlotX, resultSlotY)) {
            Inventory playerInventory = client.player.getInventory();
            Level world = client.level;

            AbstractCraftingMenu handler = (AbstractCraftingMenu) ((AbstractContainerScreen<?>) (Object) this).getMenu();

            CraftingContainer input;
            if (handler instanceof InventoryMenu playerHandler) {
                input = playerHandler.getCraftSlots();
            } else {
                input = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
            }

            CraftingPredictor predictor = CraftingPredictor.getInstance();
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
                Identifier recipeId = predictor.getRecipeId(recipe);

                if (recipeId != null) {
                    ClientPlayNetworking.send(new CraftItemPayload(recipeId.toString(), isShiftPressed));
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
    private void renderShapedRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, AbstractCraftingMenu handler, int screenX, int screenY, int mouseX, int mouseY, Inventory playerInventory, ItemStack cursorStack, Level world, CraftingPredictor predictor) {
        ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
        ItemStack resultStack = CraftingPredictor.getResult(recipe, world);
        renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

        int recipeWidth = shapedRecipe.getWidth();
        int recipeHeight = shapedRecipe.getHeight();
        List<Optional<Ingredient>> ingredients = shapedRecipe.getIngredients();

        int bestOffsetX = -1;
        int bestOffsetY = -1;
        boolean bestMirrored = false;
        int bestScore = -1;

        for (int offsetX = 0; offsetX <= input.getWidth() - recipeWidth; offsetX++) {
            for (int offsetY = 0; offsetY <= input.getHeight() - recipeHeight; offsetY++) {
                Tuple<Integer, Boolean> matchResult = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack, input), offsetX, offsetY);
                int alignmentScore = matchResult.getA();
                boolean mirrored = matchResult.getB();
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
                    Optional<Ingredient> ingredientOptional = ingredients.get(bestMirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);
                    if (ingredientOptional.isEmpty()) {
                        continue;
                    }

                    Ingredient ingredient = ingredientOptional.get();

                    int gridX = bestOffsetX + recipeX;
                    int gridY = bestOffsetY + recipeY;

                    int gridIndex = gridY * input.getWidth() + gridX;
                    Slot slot = handler.slots.get(gridIndex + 1);
                    int slotX = screenX + slot.x;
                    int slotY = screenY + slot.y;

                    Optional<ItemStack> matchingStack = ingredient.items().findFirst().map(item -> item.value().getDefaultInstance());
                    if (matchingStack.isPresent()) {
                        renderGhostItem(context, matchingStack.get(), slotX, slotY, 0.2f, mouseX, mouseY, false);
                    }
                }
            }
        }
    }

    @Unique
    private void renderShapelessRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, AbstractCraftingMenu handler, int screenX, int screenY, int mouseX, int mouseY) {
        List<Ingredient> ingredients = CraftingPredictor.getIngredients(recipe).stream().flatMap(Optional::stream).toList();
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
                remainingIngredients.add(ingredient.items().findFirst().get().value().getDefaultInstance());
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
        context.renderFakeItem(stack, x, y);

        drawTransparentRectangle(context, x, y, x + 16, y + 16, opacity);

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stack.getHoverName());
            if (isLastCrafted) {
                tooltip.add(Component.translatable("tooltip.craftsense.last_crafted_item").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            context.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }
    }

    @Unique
    private void drawTransparentRectangle(GuiGraphics context, int x1, int y1, int x2, int y2, float alpha) {
        int color = (int)(alpha * 255) << 24 | 0xFFFFFF;
        context.fill(x1, y1, x2, y2, color);
    }

    @Unique
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
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
        context.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, fixedPositioner, x + 30, y - 7, false);
    }
}