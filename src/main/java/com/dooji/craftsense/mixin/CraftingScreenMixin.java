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
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.context.ContextType;
import net.minecraft.world.World;

import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.opengl.GlStateManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(RecipeBookScreen.class)
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
    private static final ContextParameterMap EMPTY_CONTEXT_PARAMETER_MAP = new ContextParameterMap.Builder().build(new ContextType.Builder().build());

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

    @Inject(method = "render", at = @At("HEAD"))
    private void renderCraftingPrediction(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen craftingScreen) || !CraftSense.configManager.isEnabled() || this.recipeBook.isOpen()) {
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
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
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
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                ItemStack resultStack = getRecipeResult(recipe);
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, false);

                if (recipe instanceof ShapedRecipe) {
                    renderShapedRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY, playerInventory, cursorStack, predictor);
                } else if (recipe instanceof ShapelessRecipe) {
                    renderShapelessRecipeIngredients(context, recipe, input, handler, screenX, screenY, mouseX, mouseY);
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onSuggestedRecipeClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        boolean isShiftPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), InputUtil.GLFW_KEY_LEFT_SHIFT);

        if (!(MinecraftClient.getInstance().currentScreen instanceof CraftingScreen craftingScreen) || !CraftSense.configManager.isEnabled() || this.recipeBook.isOpen()) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMouseClickTime < cooldownDuration) {
            cir.setReturnValue(false);
            return;
        }

        lastMouseClickTime = currentTime;

        MinecraftClient client = MinecraftClient.getInstance();
        CraftingScreenHandler handler = craftingScreen.getScreenHandler();
        ItemStack cursorStack = handler.getCursorStack();

        if (isMouseOverSlot((int) click.x(), (int) click.y(), resultSlotX, resultSlotY)) {
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

                    if (!cursorStack.isEmpty() && !isShiftPressed && (!cursorStack.isStackable() || !areStacksEqualWithComponents(cursorStack, resultStack)) || resultStack.getItem() == Items.AIR) {
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
    private void onKeyPressed(KeyInput keyInput, CallbackInfoReturnable<Boolean> cir) {
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

        if (CraftSenseKeyBindings.quickCraftKey.matchesKey(new KeyInput(keyInput.getKeycode(), keyInput.scancode(), keyInput.modifiers()))) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player != null && client.world != null) {
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
    private void renderShapedRecipeIngredients(DrawContext context, CraftingRecipe recipe, RecipeInputInventory input, CraftingScreenHandler handler, int screenX, int screenY, int mouseX, int mouseY, PlayerInventory playerInventory, ItemStack cursorStack, CraftingPredictor predictor) {
        ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
        
        int recipeWidth = shapedRecipe.getWidth();
        int recipeHeight = shapedRecipe.getHeight();
        List<Optional<Ingredient>> ingredients = shapedRecipe.getIngredients();

        int bestOffsetX = -1;
        int bestOffsetY = -1;
        boolean bestMirrored = false;
        int bestScore = -1;

        for (int offsetX = 0; offsetX <= 3 - recipeWidth; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - recipeHeight; offsetY++) {
                Pair<Integer, Boolean> matchResult = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack, input), offsetX, offsetY);
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
            for (int recipeY = 0; recipeY < recipeHeight; recipeY++) {
                for (int recipeX = 0; recipeX < recipeWidth; recipeX++) {
                    int index = recipeY * recipeWidth + recipeX;
                    Optional<Ingredient> optionalIngredient = ingredients.get(bestMirrored ? (recipeWidth - recipeX - 1) + recipeY * recipeWidth : index);

                    if (optionalIngredient.isPresent()) {
                        Ingredient ingredient = optionalIngredient.get();
                        List<RegistryEntry<Item>> matchingItems = ingredient.getMatchingItems().collect(Collectors.toList());

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

    @Unique
    private void renderShapelessRecipeIngredients(DrawContext context, CraftingRecipe recipe, RecipeInputInventory input, CraftingScreenHandler handler, int screenX, int screenY, int mouseX, int mouseY) {
        List<Ingredient> ingredients = ((ShapelessRecipeAccessor) recipe).getIngredients();
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
                List<RegistryEntry<Item>> matchingItems = ingredient.getMatchingItems().collect(Collectors.toList());
                if (!matchingItems.isEmpty()) {
                    remainingIngredients.add(new ItemStack(matchingItems.get(0).value()));
                }
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
        context.getMatrices().pushMatrix();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

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

        GlStateManager._disableBlend();
        context.getMatrices().popMatrix();
    }

    @Unique
    private void drawTransparentRectangle(DrawContext context, int x1, int y1, int x2, int y2, int z, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24;
        int argb = a | 0xFFFFFF;
        context.fill(x1, y1, x2, y2, argb);
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

    @Unique
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
        context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltip, fixedPositioner, x + 30, y - 7, false);
    }
}