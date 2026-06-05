package com.dooji.craftsense.mixin;

import com.dooji.craftsense.CraftSense;
import com.dooji.craftsense.CraftSenseKeyBindings;
import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.network.payloads.CraftItemPayload;
import com.dooji.craftsense.Pair;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;
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
    private RecipeBookComponent recipeBookComponent;

    @Unique private int resultSlotX;
    @Unique private int resultSlotY;
    @Unique private String lastGridHash = "";
    @Unique private Optional<CraftingRecipe> cachedLastCraftedRecipe = Optional.empty();
    @Unique private Optional<CraftingRecipe> cachedSuggestedRecipe = Optional.empty();
    @Unique private boolean showFirstTimeTooltips = CraftSense.configManager.isFirstTime();
    @Unique private int progress = 0;
    @Unique private long lastMouseClickTime = 0;
    @Unique private long lastKeyPressTime = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCraftingPrediction(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof CraftingScreen) || !CraftSense.configManager.isEnabled()) {
            return;
        }

        // Detect if the recipe book panel is actually showing by checking whether leftPos
        // has been shifted away from center (176 = crafting table imageWidth).
        int centeredLeftPos = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - 176) / 2;
        int actualLeftPos = ((AbstractContainerScreenAccessor) this).getLeftPos();
        if (Math.abs(actualLeftPos - centeredLeftPos) > 5) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        CraftingScreen craftingScreen = (CraftingScreen) client.screen;
        Inventory playerInventory = client.player.getInventory();
        Level world = client.level;

        CraftingMenu handler = craftingScreen.getMenu();
        CraftingContainer input = ((CraftingMenuAccessor) handler).getCraftSlots();
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

        int screenX = ((AbstractContainerScreenAccessor) this).getLeftPos();
        int screenY = ((AbstractContainerScreenAccessor) this).getTopPos();
        resultSlotX = screenX + 124;
        resultSlotY = screenY + 35;

        if (cachedLastCraftedRecipe.isPresent()) {
            if (showFirstTimeTooltips && isMouseOverSlot(mouseX, mouseY, resultSlotX, resultSlotY)) {
                renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.lastCraftedSuggest").getString(), resultSlotX, resultSlotY);
            }

            CraftingRecipe recipe = cachedLastCraftedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
                ItemStack resultStack = recipe.getResultItem(world.registryAccess());
                renderGhostItem(context, resultStack, resultSlotX, resultSlotY, 0.2f, mouseX, mouseY, true);
                return;
            }
        }

        if (cachedSuggestedRecipe.isPresent()) {
            if (showFirstTimeTooltips) {
                renderTooltip(context, Component.translatable("tooltip.craftsense.click_here").getString(), Component.translatable("tooltip.craftsense.suggest").getString(), resultSlotX, resultSlotY);
            }

            CraftingRecipe recipe = cachedSuggestedRecipe.get();
            if (predictor.hasRequiredIngredients(recipe, predictor.getAvailableItems(playerInventory, cursorStack, input))) {
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
        boolean isShiftPressed = Screen_hasShiftDown();

        if (!(Minecraft.getInstance().screen instanceof CraftingScreen) || !CraftSense.configManager.isEnabled() ) {
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

            Minecraft client = Minecraft.getInstance();
            Inventory playerInventory = client.player.getInventory();
            Level world = client.level;

            CraftingMenu handler = ((CraftingScreen) (Object) this).getMenu();
            ItemStack cursorStack = handler.getCarried();

            CraftingContainer input = ((CraftingMenuAccessor) handler).getCraftSlots();
            CraftingPredictor predictor = CraftingPredictor.getInstance(world.getRecipeManager());
            Optional<CraftingRecipe> lastCraftedRecipe = predictor.suggestLastCraftedItem(input, playerInventory, handler.getCarried(), world);

            Optional<CraftingRecipe> optionalRecipe = lastCraftedRecipe.isPresent()
                    ? lastCraftedRecipe
                    : predictor.suggestRecipe(input, playerInventory, handler.getCarried(), world);

            if (optionalRecipe.isPresent()) {
                CraftingRecipe recipe = optionalRecipe.get();
                ResourceLocation recipeId = findRecipeId(world, recipe);

                if (recipeId != null) {
                    ItemStack resultStack = recipe.getResultItem(world.registryAccess()).copy();

                    if (!cursorStack.isEmpty() && !isShiftPressed && (!cursorStack.isStackable() || !areStacksEqualWithComponents(cursorStack, resultStack)) || resultStack.getItem() == Items.AIR) {
                        return;
                    }

                    CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                    String category = CategoryManager.getCategory(resultStack.getItem());
                    habitsConfig.recordCraft(category, resultStack.getItem().getDescriptionId());

                    PacketDistributor.sendToServer(new CraftItemPayload(recipeId.toString(), isShiftPressed));
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(Minecraft.getInstance().screen instanceof CraftingScreen) || !CraftSense.configManager.isEnabled() ) {
            return;
        }

        long cooldownDuration = 100;
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastKeyPressTime < cooldownDuration) {
            cir.setReturnValue(false);
            return;
        }

        lastKeyPressTime = currentTime;

        if (CraftSenseKeyBindings.quickCraftKey.matches(keyCode, scanCode)) {
            Minecraft client = Minecraft.getInstance();

            if (client.player != null && client.level != null) {
                CraftingMenu handler = ((CraftingScreen) (Object) this).getMenu();
                Inventory inventory = client.player.getInventory();
                CraftingContainer input = ((CraftingMenuAccessor) handler).getCraftSlots();
                ItemStack cursorStack = handler.getCarried();

                CraftingPredictor predictor = CraftingPredictor.getInstance(client.level.getRecipeManager());
                String currentStateHash = predictor.calculateInputHash(input, inventory, cursorStack);

                if (!currentStateHash.equals(lastGridHash)) {
                    lastGridHash = currentStateHash;
                    cachedLastCraftedRecipe = predictor.suggestLastCraftedItem(input, inventory, cursorStack, client.level);
                    if (cachedLastCraftedRecipe.isEmpty()) {
                        cachedSuggestedRecipe = predictor.suggestRecipe(input, inventory, cursorStack, client.level);
                    } else {
                        cachedSuggestedRecipe = Optional.empty();
                    }
                }

                Optional<CraftingRecipe> recipe = cachedSuggestedRecipe.isPresent() ? cachedSuggestedRecipe : cachedLastCraftedRecipe;

                recipe.ifPresent(r -> {
                    ResourceLocation recipeId = findRecipeId(client.level, r);
                    if (recipeId != null) {
                        ItemStack resultStack = r.getResultItem(client.level.registryAccess());

                        if (resultStack.getItem() == Items.AIR) {
                            return;
                        }

                        String category = CategoryManager.getCategory(resultStack.getItem());
                        CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
                        habitsConfig.recordCraft(category, resultStack.getItem().getDescriptionId());

                        PacketDistributor.sendToServer(new CraftItemPayload(recipeId.toString(), true));
                    }
                });

                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void renderShapedRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, CraftingMenu handler, int screenX, int screenY, int mouseX, int mouseY, Inventory playerInventory, ItemStack cursorStack, Level world, CraftingPredictor predictor) {
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

        for (int offsetX = 0; offsetX <= 3 - recipeWidth; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - recipeHeight; offsetY++) {
                Pair<Integer, Boolean> matchResult = predictor.matchShapedRecipe(shapedRecipe, input, predictor.getAvailableItems(playerInventory, cursorStack, input), offsetX, offsetY);
                int alignmentScore = matchResult.left();
                boolean mirrored = matchResult.right();
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
    private void renderShapelessRecipeIngredients(GuiGraphics context, CraftingRecipe recipe, CraftingContainer input, CraftingMenu handler, int screenX, int screenY, int mouseX, int mouseY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[][] usedGrid = new boolean[3][3];
        Map<Integer, Integer> placedItemCounts = new HashMap<>();

        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
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
    private void renderGhostItem(GuiGraphics context, ItemStack stack, int x, int y, float opacity, int mouseX, int mouseY, boolean isLastCrafted) {
        context.pose().pushPose();
        context.pose().translate(0, 0, 200);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.renderFakeItem(stack, x, y);
        // Overlay with a high-opacity white to create a clearly ghost-like appearance.
        context.fill(x, y, x + 16, y + 16, (int)(0.65f * 255) << 24 | 0x00FFFFFF);

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stack.getHoverName());
            if (isLastCrafted) {
                tooltip.add(Component.translatable("tooltip.craftsense.last_crafted_item").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            context.renderTooltip(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
        }

        RenderSystem.disableBlend();
        context.pose().popPose();
    }

    @Unique
    private boolean areStacksEqualWithComponents(ItemStack stack1, ItemStack stack2) {
        if (!ItemStack.isSameItem(stack1, stack2)) {
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
    private ResourceLocation findRecipeId(Level world, CraftingRecipe targetRecipe) {
        return world.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream()
                .filter(h -> h.value() == targetRecipe)
                .map(h -> h.id())
                .findFirst()
                .orElse(null);
    }

    @Unique
    private void renderTooltip(GuiGraphics context, String title, String description, int x, int y) {
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(title).withStyle(ChatFormatting.WHITE).getVisualOrderText());

        for (String line : description.split("\n")) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }

        context.renderTooltip(Minecraft.getInstance().font, tooltip, x + 30, y - 7);
    }

    @Unique
    private boolean Screen_hasShiftDown() {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
