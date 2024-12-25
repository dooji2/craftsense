package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.network.payloads.CraftItemPayload;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import org.jetbrains.annotations.Nullable;

import static com.dooji.craftsense.manager.CategoryManager.getCategory;

@Mixin(CraftingScreen.class)
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
    private boolean showFirstTimeTooltips = CraftSense.configManager.isFirstTime();

    @Unique
    private int progress = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CraftSense.configManager.isEnabled()) {
            return;
        }

        CraftingScreen craftingScreen = (CraftingScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInventory = client.player.getInventory();
        World world = client.world;

        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        CraftingInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
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
                renderTooltip(matrices, Text.translatable("tooltip.craftsense.click_here").getString(), Text.translatable("tooltip.craftsense.lastCraftedSuggest").getString(), resultSlotX, resultSlotY);
            }

            CraftingRecipe recipe = cachedLastCraftedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack))) {
                ItemStack resultStack = recipe.getOutput();
                renderGhostItem(matrices, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, true);
                return;
            }
        }

        if (cachedSuggestedRecipe.isPresent()) {
            if (showFirstTimeTooltips) {
                renderTooltip(matrices, Text.translatable("tooltip.craftsense.click_here").getString(), Text.translatable("tooltip.craftsense.suggest").getString(), resultSlotX, resultSlotY);
            }

            CraftingRecipe recipe = cachedSuggestedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack))) {
                ItemStack resultStack = recipe.getOutput();
                renderGhostItem(matrices, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

                if (recipe instanceof ShapedRecipe shapedRecipe) {
                    int recipeWidth = shapedRecipe.getWidth();
                    int recipeHeight = shapedRecipe.getHeight();
                    List<Ingredient> ingredients = shapedRecipe.getIngredients();

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
                                Ingredient ingredient = ingredients.get(index);

                                int gridX = bestOffsetX + recipeX;
                                int gridY = bestOffsetY + recipeY;

                                int gridIndex = gridY * 3 + gridX;
                                Slot slot = handler.slots.get(gridIndex + 1);
                                int slotX = screenX + slot.x;
                                int slotY = screenY + slot.y;

                                ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                                if (matchingStacks.length > 0) {
                                    ItemStack ghostStack = matchingStacks[0];
                                    renderGhostItem(matrices, ghostStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
                                }
                            }
                        }
                    }
                } else {
                    List<Ingredient> ingredients = recipe.getIngredients();
                    List<Ingredient> ingredientsToPlace = new ArrayList<>(ingredients);

                    for (int i = 0; i < input.size(); i++) {
                        ItemStack placedItem = input.getStack(i);
                        if (!placedItem.isEmpty()) {
                            ingredientsToPlace.removeIf(ingredient -> ingredient.test(placedItem));
                        }
                    }

                    int ingredientIndex = 0;
                    for (int i = 0; i < input.size() && ingredientIndex < ingredientsToPlace.size(); i++) {
                        ItemStack placedItem = input.getStack(i);
                        if (placedItem.isEmpty()) {
                            Ingredient ingredient = ingredientsToPlace.get(ingredientIndex);
                            ItemStack[] matchingStacks = ingredient.getMatchingStacks();
                            if (matchingStacks.length > 0) {
                                ItemStack ghostStack = matchingStacks[0];
                                Slot slot = handler.slots.get(i + 1);
                                int slotX = screenX + slot.x;
                                int slotY = screenY + slot.y;
                                renderGhostItem(matrices, ghostStack, slotX, slotY, 0.2f, mouseX, mouseY, false);
                            }
                            ingredientIndex++;
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
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
            CraftingInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
            CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
            Optional<CraftingRecipe> lastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, handler.getCursorStack(), world);
            Optional<CraftingRecipe> optionalRecipe = lastCraftedRecipe.isPresent()
                    ? lastCraftedRecipe
                    : predictor.suggestRecipe(input, playerInventory, handler.getCursorStack(), world);

            if (optionalRecipe.isPresent()) {
                CraftingRecipe recipe = optionalRecipe.get();
                Identifier recipeId = findRecipeId(world.getRecipeManager(), recipe);

                if (recipeId != null) {
                    ItemStack resultStack = recipe.getOutput().copy();
                    ItemStack cursorStack = handler.getCursorStack();

                    if (cursorStack.isEmpty()) {
                        handler.setCursorStack(resultStack);
                    } else if (areStacksEqualWithComponents(cursorStack, resultStack) && resultStack.isStackable()) {
                        cursorStack.increment(resultStack.getCount());
                        handler.setCursorStack(cursorStack);
                    } else {
                        return;
                    }

                    CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                    String category = getCategory(resultStack.getItem());
                    habitsConfig.recordCraft(category, resultStack.getItem().getTranslationKey());

                    Identifier channelId = new Identifier("craftsense", "craft_item");
                    PacketByteBuf packetBuffer = CraftItemPayload.createPacket(recipeId.toString());

                    ClientPlayNetworking.send(channelId, packetBuffer);

                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Unique
    private void renderGhostItem(MatrixStack matrices, ItemStack stack, int x, int y, float opacity, int mouseX, int mouseY, boolean isLastCrafted) {
        matrices.push();
        matrices.translate(0, 0, -100);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawItem(stack, x, y);

        drawTransparentRectangle(matrices, x, y, x + 16, y + 16, 300, opacity);

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(stack.getName());
            if (isLastCrafted) {
                tooltip.add(Text.translatable("tooltip.craftsense.last_crafted_item").formatted(Formatting.GRAY, Formatting.ITALIC));
            }
            ((HandledScreen<?>) (Object) this).renderTooltip(matrices, tooltip, mouseX, mouseY);
        }

        RenderSystem.disableBlend();
        matrices.pop();
    }

    @Unique
    private void drawTransparentRectangle(MatrixStack matrices, int x1, int y1, int x2, int y2, int z, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x1, y1, z).color(255, 255, 255, (int) (alpha * 255)).next();
        buffer.vertex(matrix, x1, y2, z).color(255, 255, 255, (int) (alpha * 255)).next();
        buffer.vertex(matrix, x2, y2, z).color(255, 255, 255, (int) (alpha * 255)).next();
        buffer.vertex(matrix, x2, y1, z).color(255, 255, 255, (int) (alpha * 255)).next();

        BufferRenderer.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    @Unique
    private boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.areItemsEqual(stack1, stack2)) {
            return false;
        }

        if (stack1.hasNbt() && stack2.hasNbt()) {
            return Objects.equals(stack1.getNbt(), stack2.getNbt());
        }
        return !stack1.hasNbt() && !stack2.hasNbt();
    }

    @Unique
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
    }

    @Unique
    @Nullable
    private Identifier findRecipeId(RecipeManager recipeManager, CraftingRecipe targetRecipe) {
        List<? extends Recipe<?>> craftingRecipes = recipeManager.listAllOfType(RecipeType.CRAFTING);
        for (Recipe<?> recipe : craftingRecipes) {
            if (recipe instanceof CraftingRecipe && recipe.equals(targetRecipe)) {
                return recipe.getId();
            }
        }
        return null;
    }

    @Unique
    private void renderTooltip(MatrixStack matrices, String title, String description, int x, int y) {
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(Text.literal(title).formatted(Formatting.WHITE));

        String[] descriptionLines = description.split("\n");
        for (String line : descriptionLines) {
            tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
        }

        ((HandledScreen<?>) (Object) this).renderTooltip(matrices, tooltip, x + 20, y + 3);
    }

    @Unique
    private void drawItem(ItemStack itemStack, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getItemRenderer().renderInGui(itemStack, x, y);
    }
}