package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftSenseKeyBindings;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector2i;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin {

    @Final
    @Shadow
    private RecipeBookWidget recipeBook;

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
    private boolean showFirstTimeTooltips = CraftSense.configManager.isFirstTime();

    @Unique
    private int progress = 0;

    @Unique
    private long lastMouseClickTime = 0;

    @Unique
    private long lastKeyPressTime = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen craftingScreen) || !CraftSense.configManager.isEnabled() || this.recipeBook.isOpen()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInventory = client.player.getInventory();
        World world = client.world;

        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
        ItemStack cursorStack = handler.getCursorStack();

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
        resultSlotX = screenX + 124;
        resultSlotY = screenY + 35;

        if (cachedLastCraftedRecipe.isPresent()) {
            if (showFirstTimeTooltips) {
                renderTooltip(context, Text.translatable("tooltip.craftsense.click_here").getString(), Text.translatable("tooltip.craftsense.lastCraftedSuggest").getString(), resultSlotX, resultSlotY);
            }

            CraftingRecipe recipe = cachedLastCraftedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack))) {
                ItemStack resultStack = recipe.getResult(world.getRegistryManager());
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, true);
                return;
            }
        }

        if (cachedSuggestedRecipe.isPresent()) {
            if (showFirstTimeTooltips) {
                renderTooltip(context, Text.translatable("tooltip.craftsense.click_here").getString(), Text.translatable("tooltip.craftsense.suggest").getString(), resultSlotX, resultSlotY);
            }
            
            CraftingRecipe recipe = cachedSuggestedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack))) {
                ItemStack resultStack = recipe.getResult(world.getRegistryManager());
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
        boolean isShiftPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_SHIFT);

        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen) || !CraftSense.configManager.isEnabled() || this.recipeBook.isOpen()) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMouseClickTime < cooldownDuration) {
            cir.setReturnValue(false);
            return;
        }

        lastMouseClickTime = currentTime;

        if (isMouseOverSlot((int) mouseX, (int) mouseY, resultSlotX, resultSlotY)) {
            if (showFirstTimeTooltips) {
                progress++;
                if (progress >= 2) {
                    showFirstTimeTooltips = false;
                    CraftSense.configManager.toggleFirstTime();
                }
            }

            MinecraftClient client = MinecraftClient.getInstance();
            PlayerInventory playerInventory = client.player.getInventory();
            World world = client.world;

            CraftingScreenHandler handler = ((CraftingScreen) (Object) this).getScreenHandler();
            ItemStack cursorStack = handler.getCursorStack();

            RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
            CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
            Optional<CraftingRecipe> lastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, handler.getCursorStack(), world);

            Optional<CraftingRecipe> optionalRecipe = lastCraftedRecipe.isPresent()
                    ? lastCraftedRecipe
                    : predictor.suggestRecipe(input, playerInventory, handler.getCursorStack(), world);

            if (optionalRecipe.isPresent()) {
                CraftingRecipe recipe = optionalRecipe.get();
                Identifier recipeId = findRecipeId(world.getRecipeManager(), recipe);

                if (recipeId != null) {
                    ItemStack resultStack = recipe.getResult(world.getRegistryManager()).copy();

                    if (!cursorStack.isEmpty() && !isShiftPressed && (!cursorStack.isStackable() || !areStacksEqualWithComponents(cursorStack, resultStack)) || resultStack.getItem() == Items.AIR) {
                        return;
                    }

                    CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                    String category = CategoryManager.getCategory(resultStack.getItem());
                    habitsConfig.recordCraft(category, resultStack.getItem().getTranslationKey());

                    ClientPlayNetworking.send(new CraftItemPayload(recipeId.toString(), isShiftPressed));

                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen) || !CraftSense.configManager.isEnabled() || this.recipeBook.isOpen()) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastKeyPressTime < cooldownDuration) {
            cir.setReturnValue(false);
            return;
        }

        lastKeyPressTime = currentTime;

        if (CraftSenseKeyBindings.quickCraftKey.matchesKey(keyCode, scanCode)) {
            MinecraftClient client = MinecraftClient.getInstance();
            
            if (client.player != null && client.world != null) {
                CraftingScreenHandler handler = ((CraftingScreen) (Object) this).getScreenHandler();
                PlayerInventory inventory = client.player.getInventory();
                RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
                ItemStack cursorStack = handler.getCursorStack();

                CraftingPredictor predictor = CraftingPredictor.getInstance(client.world.getRecipeManager());
                String currentStateHash = predictor.calculateInputHash(input, inventory, cursorStack);

                if (!currentStateHash.equals(lastGridHash)) {
                    lastGridHash = currentStateHash;
                    cachedLastCraftedRecipe = predictor.suggestLastCraftedItem(input, inventory, cursorStack, client.world);
                    if (cachedLastCraftedRecipe.isEmpty()) {
                        cachedSuggestedRecipe = predictor.suggestRecipe(input, inventory, cursorStack, client.world);
                    } else {
                        cachedSuggestedRecipe = Optional.empty();
                    }
                }

                Optional<CraftingRecipe> recipe = cachedSuggestedRecipe.isPresent() ? cachedSuggestedRecipe : cachedLastCraftedRecipe;

                recipe.ifPresent(r -> {
                    Identifier recipeId = findRecipeId(client.world.getRecipeManager(), r);
                    if (recipeId != null) {
                        ItemStack resultStack = r.getResult(client.world.getRegistryManager());

                        if (resultStack.getItem() == Items.AIR) {
                            return;
                        }

                        String category = CategoryManager.getCategory(resultStack.getItem());

                        CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                        habitsConfig.recordCraft(category, resultStack.getItem().getTranslationKey());

                        ClientPlayNetworking.send(new CraftItemPayload(recipeId.toString(), true));
                    }
                });

                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void renderShapedRecipeIngredients(DrawContext context, CraftingRecipe recipe, RecipeInputInventory input, CraftingScreenHandler handler, int screenX, int screenY, int mouseX, int mouseY, PlayerInventory playerInventory, ItemStack cursorStack, World world, CraftingPredictor predictor) {
        ItemStack resultStack = recipe.getResult(world.getRegistryManager());
        renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int recipeWidth = shapedRecipe.getWidth();
            int recipeHeight = shapedRecipe.getHeight();
            List<Ingredient> ingredients = shapedRecipe.getIngredients();

            int bestOffsetX = -1;
            int bestOffsetY = -1;
            boolean bestMirrored = false;
            int bestScore = -1;

            for (int offsetX = 0; offsetX <= 3 - recipeWidth; offsetX++) {
                for (int offsetY = 0; offsetY <= 3 - recipeHeight; offsetY++) {
                    Pair<Integer, Boolean> matchResult = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack), offsetX, offsetY);
                    int alignmentScore = matchResult.getLeft();
                    boolean mirrored = matchResult.getRight();
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

                        int gridIndex = gridY * 3 + gridX;
                        Slot slot = handler.slots.get(gridIndex + 1);
                        int slotX = screenX + slot.x;
                        int slotY = screenY + slot.y;

                        ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                        if (matchingStacks.length > 0) {
                            ItemStack ghostStack = matchingStacks[0];
                            renderGhostItem(context, ghostStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void renderShapelessRecipeIngredients(DrawContext context, CraftingRecipe recipe, RecipeInputInventory input, CraftingScreenHandler handler, int screenX, int screenY, int mouseX, int mouseY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[][] usedGrid = new boolean[3][3];
        Map<Integer, Integer> placedItemCounts = new HashMap<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStack(i);
            if (!stack.isEmpty()) {
                placedItemCounts.put(i, stack.getCount());
                int gridX = i % 3;
                int gridY = i / 3;
                usedGrid[gridY][gridX] = true;
            }
        }

        List<ItemStack> remainingIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;

            for (Map.Entry<Integer, Integer> entry : placedItemCounts.entrySet()) {
                int slotIndex = entry.getKey();
                int count = entry.getValue();
                ItemStack placedItem = input.getStack(slotIndex);

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
                remainingIngredients.add(ingredient.getMatchingStacks()[0]);
            }
        }

        for (Map.Entry<Integer, Integer> entry : placedItemCounts.entrySet()) {
            int slotIndex = entry.getKey();
            Slot slot = handler.slots.get(slotIndex + 1);
            int slotX = screenX + slot.x;
            int slotY = screenY + slot.y;
            renderGhostItem(context, input.getStack(slotIndex), slotX, slotY, 0.2f, mouseX, mouseY, false);
        }

        int ingredientIndex = 0;
        for (int gridY = 0; gridY < 3; gridY++) {
            for (int gridX = 0; gridX < 3; gridX++) {
                if (usedGrid[gridY][gridX] || ingredientIndex >= remainingIngredients.size()) {
                    continue;
                }

                int gridIndex = gridY * 3 + gridX;
                Slot slot = handler.slots.get(gridIndex + 1);
                int slotX = screenX + slot.x;
                int slotY = screenY + slot.y;

                ItemStack ingredientStack = remainingIngredients.get(ingredientIndex++);
                renderGhostItem(context, ingredientStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
            }
        }
    }

    @Unique
    private void renderGhostItem(DrawContext context, ItemStack stack, int x, int y, float opacity, int mouseX, int mouseY, boolean isLastCrafted) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, -100);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.drawItemWithoutEntity(stack, x, y);

        drawTransparentRectangle(context, x, y, x + 16, y + 16, 200, opacity);

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(stack.getName());
            if (isLastCrafted) {
                tooltip.add(Text.translatable("tooltip.craftsense.last_crafted_item").formatted(Formatting.GRAY, Formatting.ITALIC));
            }
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltip, mouseX, mouseY);
        }

        RenderSystem.disableBlend();
        context.getMatrices().pop();
    }

    @Unique
    private void drawTransparentRectangle(DrawContext context, int x1, int y1, int x2, int y2, int z, float alpha) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertexConsumer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());

        int color = (int)(alpha * 255) << 24 | 0xFFFFFF;

        vertexConsumer.vertex(matrix, x1, y1, z).color(color);
        vertexConsumer.vertex(matrix, x1, y2, z).color(color);
        vertexConsumer.vertex(matrix, x2, y2, z).color(color);
        vertexConsumer.vertex(matrix, x2, y1, z).color(color);

        context.draw();
    }

    @Unique
    private boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.areItemsEqual(stack1, stack2)) {
            return false;
        }

        return Objects.equals(stack1.getComponents(), stack2.getComponents());
    }

    @Unique
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    @Unique
    @Nullable
    private Identifier findRecipeId(RecipeManager recipeManager, CraftingRecipe targetRecipe) {
        Map<Identifier, RecipeEntry<?>> recipesById = ((RecipeManagerAccessor) recipeManager).getRecipesById();

        for (Map.Entry<Identifier, RecipeEntry<?>> entry : recipesById.entrySet()) {
            Recipe<?> recipe = entry.getValue().value();
            if (recipe instanceof CraftingRecipe && recipe == targetRecipe) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Unique
    private void renderTooltip(DrawContext context, String title, String description, int x, int y) {
        List<OrderedText> tooltip = new ArrayList<>();
        tooltip.add(Text.literal(title).formatted(Formatting.WHITE).asOrderedText());

        String[] descriptionLines = description.split("\n");
        for (String line : descriptionLines) {
            tooltip.add(Text.literal(line).formatted(Formatting.GRAY).asOrderedText());
        }

        TooltipPositioner fixedPositioner = (screenWidth, screenHeight, tooltipX, tooltipY, tooltipWidth, tooltipHeight) -> new Vector2i(tooltipX, tooltipY);
        context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltip, fixedPositioner, x + 30, y - 7);
    }
}