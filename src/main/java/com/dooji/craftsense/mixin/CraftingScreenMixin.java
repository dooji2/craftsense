package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static com.dooji.craftsense.manager.CategoryManager.getCategory;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin {

    @Unique
    private int resultSlotX;

    @Unique
    private int resultSlotY;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CraftSense.configManager.isEnabled()) {
            return;
        }

        CraftingScreen craftingScreen = (CraftingScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory playerInventory = client.player.getInventory();
        World world = client.world;

        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();

        ItemStack cursorStack = handler.getCursorStack();

        CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
        Optional<CraftingRecipe> optionalRecipe = predictor.suggestRecipe(input, playerInventory, cursorStack, world);
        if (optionalRecipe.isEmpty()) {
            return;
        }

        CraftingRecipe recipe = optionalRecipe.get();

        int screenX = ((HandledScreenAccessor) this).getX();
        int screenY = ((HandledScreenAccessor) this).getY();

        ItemStack resultStack = recipe.getOutput(world.getRegistryManager());
        resultSlotX = screenX + 124;
        resultSlotY = screenY + 35;

        renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f);

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
                            renderGhostItem(context, ghostStack, slotX, slotY, 0.2f);
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
                        renderGhostItem(context, ghostStack, slotX, slotY, 0.2f);
                    }
                    ingredientIndex++;
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (isMouseOverSlot((int) mouseX, (int) mouseY, resultSlotX, resultSlotY)) {
            MinecraftClient client = MinecraftClient.getInstance();
            PlayerInventory playerInventory = client.player.getInventory();
            World world = client.world;

            CraftingScreenHandler handler = ((CraftingScreen) (Object) this).getScreenHandler();
            RecipeInputInventory input = ((CraftingScreenHandlerAccessor) handler).getInput();
            CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
            Optional<CraftingRecipe> optionalRecipe = predictor.suggestRecipe(input, playerInventory, handler.getCursorStack(), world);

            if (optionalRecipe.isPresent()) {
                CraftingRecipe recipe = optionalRecipe.get();
                Identifier recipeId = findRecipeId(world.getRecipeManager(), recipe);

                if (recipeId != null) {
                    ItemStack resultStack = recipe.getOutput(world.getRegistryManager()).copy();
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
    private void renderGhostItem(DrawContext context, ItemStack stack, int x, int y, float opacity) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, -100);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.drawItemWithoutEntity(stack, x, y);

        drawTransparentRectangle(context, x, y, x + 16, y + 16, 200, opacity);

        RenderSystem.disableBlend();
        context.getMatrices().pop();
    }

    @Unique
    private void drawTransparentRectangle(DrawContext context, int x1, int y1, int x2, int y2, int z, float alpha) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertexConsumer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());

        vertexConsumer.vertex(matrix, x1, y1, z).color(255, 255, 255, (int)(alpha * 255)).next();
        vertexConsumer.vertex(matrix, x1, y2, z).color(255, 255, 255, (int)(alpha * 255)).next();
        vertexConsumer.vertex(matrix, x2, y2, z).color(255, 255, 255, (int)(alpha * 255)).next();
        vertexConsumer.vertex(matrix, x2, y1, z).color(255, 255, 255, (int)(alpha * 255)).next();

        context.draw();
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
}