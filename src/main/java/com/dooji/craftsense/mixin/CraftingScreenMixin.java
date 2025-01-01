package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftSenseKeyBindings;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.context.ContextType;
import net.minecraft.world.World;

import org.joml.Matrix4f;
import org.joml.Vector2i;
import com.mojang.blaze3d.systems.RenderSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(RecipeBookScreen.class)
public abstract class CraftingScreenMixin {
    @Unique
    private int resultSlotX;
    @Unique
    private int resultSlotY;
    @Unique
    private String lastGridHash = "";
    @Unique
    private static final ContextParameterMap EMPTY_CONTEXT_PARAMETER_MAP = new ContextParameterMap.Builder().build(new ContextType.Builder().build());
    @Unique
    private Optional<CraftingRecipe> cachedLastCraftedRecipe = Optional.empty();
    @Unique
    private Optional<CraftingRecipe> cachedSuggestedRecipe = Optional.empty();

    @Unique
    private boolean showFirstTimeTooltips = CraftSense.configManager.isFirstTime();

    @Unique
    private int progress = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CraftSense.configManager.isEnabled()) {
            return;
        }

        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen craftingScreen)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInventory = client.player.getInventory();
        World world = client.world;

        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
        ItemStack cursorStack = handler.getCursorStack();
        CraftingPredictor predictor = CraftingPredictor.getInstance();
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
                ItemStack resultStack = getRecipeResult(recipe);
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
                ItemStack resultStack = getRecipeResult(recipe);
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);
        
                if (recipe instanceof ShapedRecipe shapedRecipe) {
                    int recipeWidth = shapedRecipe.getWidth();
                    int recipeHeight = shapedRecipe.getHeight();
                    List<Optional<Ingredient>> ingredients = shapedRecipe.getIngredients();

                    int bestOffsetX = -1;
                    int bestOffsetY = -1;
                    int bestScore = -1;

                    for (int offsetX = 0; offsetX <= 3 - recipeWidth; offsetX++) {
                        for (int offsetY = 0; offsetY <= 3 - recipeHeight; offsetY++) {
                            int alignmentScore = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack), offsetX, offsetY);
                            if (alignmentScore > bestScore) {
                                bestScore = alignmentScore;
                                bestOffsetX = offsetX;
                                bestOffsetY = offsetY;
                            }
                        }
                    }

                    if (bestOffsetX != -1 && bestOffsetY != -1) {
                        for (int recipeY = 0; recipeY < recipeHeight; recipeY++) {
                            for (int recipeX = 0; recipeX < recipeWidth; recipeX++) {
                                int index = recipeY * recipeWidth + recipeX;
                                Optional<Ingredient> optionalIngredient = ingredients.get(index);

                                if (optionalIngredient.isPresent()) {
                                    Ingredient ingredient = optionalIngredient.get();
                                    List<RegistryEntry<Item>> matchingItems = ingredient.getMatchingItems();

                                    if (!matchingItems.isEmpty()) {
                                        ItemStack ghostStack = new ItemStack(matchingItems.get(0).value());

                                        int gridX = bestOffsetX + recipeX;
                                        int gridY = bestOffsetY + recipeY;
                                        
                                        int gridIndex = gridY * 3 + gridX;
                                        Slot slot = handler.slots.get(gridIndex + 1);
                                        int slotX = screenX + slot.x;
                                        int slotY = screenY + slot.y;

                                        renderGhostItem(context, ghostStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        boolean isShiftPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_SHIFT);

        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen craftingScreen)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        ItemStack cursorStack = handler.getCursorStack();

        if (isMouseOverSlot((int) mouseX, (int) mouseY, resultSlotX, resultSlotY)) {
            if (showFirstTimeTooltips) {
                progress++;
                if (progress >= 2) {
                    showFirstTimeTooltips = false;
                    CraftSense.configManager.toggleFirstTime();
                }
            }

            PlayerInventory playerInventory = client.player.getInventory();
            World world = client.world;
            RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();
            CraftingPredictor predictor = CraftingPredictor.getInstance();
            Optional<CraftingRecipe> lastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, handler.getCursorStack(), world);
            Optional<CraftingRecipe> optionalRecipe = lastCraftedRecipe.isPresent() ? lastCraftedRecipe : predictor.suggestRecipe(input, playerInventory, handler.getCursorStack(), world);

            if (optionalRecipe.isPresent()) {
                CraftingRecipe recipe = optionalRecipe.get();
                Identifier recipeId = findRecipeId(recipe);

                if (recipeId != null) {
                    ItemStack resultStack = getRecipeResult(recipe);

                    if (!cursorStack.isEmpty() && (!cursorStack.isStackable() || !areStacksEqualWithComponents(cursorStack, resultStack))) {
                        return;
                    }

                    CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                    String category = CategoryManager.getCategory(resultStack.getItem());
                    habitsConfig.recordCraft(category, resultStack.getItem().getTranslationKey());

                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(recipeId.toString());

                    ClientPlayNetworking.getSender().sendPacket(new CraftItemPayload(recipeId.toString(), isShiftPressed));

                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (CraftSenseKeyBindings.quickCraftKey.matchesKey(keyCode, scanCode)) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player != null && client.world != null && client.currentScreen instanceof CraftingScreen) {
                CraftingScreenHandler handler = ((CraftingScreen) (Object) this).getScreenHandler();
                PlayerInventory inventory = client.player.getInventory();
                RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getCraftingInventory();

                ItemStack cursorStack = handler.getCursorStack();
                CraftingPredictor predictor = CraftingPredictor.getInstance();
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
                    Identifier recipeId = findRecipeId(r);
                    if (recipeId != null) {
                        ItemStack resultStack = getRecipeResult(r);
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
        VertexConsumerProvider.Immediate vertexConsumers = ((DrawContextAccessor) context).getVertexConsumers();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getGui());

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
        return java.util.Objects.equals(stack1.getComponents(), stack2.getComponents());
    }

    @Unique
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    @Unique
    private Identifier findRecipeId(CraftingRecipe targetRecipe) {
        ContextParameterMap emptyMap = EMPTY_CONTEXT_PARAMETER_MAP;
        List<RecipeDisplay> displays = targetRecipe.getDisplays();
        if (displays.isEmpty()) {
            return null;
        }

        ItemStack targetResult = displays.get(0).result().getStacks(emptyMap).get(0);

        for (Map.Entry<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> entry : CraftingPredictor.getInstance().getRecipesByKey().entrySet()) {
            Identifier recipeId = entry.getKey().getValue();

            for (ServerRecipeManager.ServerRecipe serverRecipe : entry.getValue()) {
                Recipe<?> rawRecipe = serverRecipe.parent().value();

                if (!(rawRecipe instanceof CraftingRecipe recipe)) {
                    continue;
                }

                List<RecipeDisplay> serverDisplays = recipe.getDisplays();
                if (serverDisplays.isEmpty()) {
                    continue;
                }

                ItemStack serverResult = serverDisplays.get(0).result().getStacks(emptyMap).get(0);

                if (ItemStack.areItemsEqual(targetResult, serverResult) &&
                        targetResult.getCount() == serverResult.getCount()) {
                    return recipeId;
                }
            }
        }

        return null;
    }

    private ItemStack getRecipeResult(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipeAccessor shaped) {
            return shaped.getResult().copy();
        } else if (recipe instanceof ShapelessRecipeAccessor shapeless) {
            return shapeless.getResult().copy();
        } else {
            return ItemStack.EMPTY;
        }
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