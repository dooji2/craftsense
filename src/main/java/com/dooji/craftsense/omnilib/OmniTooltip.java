package com.dooji.craftsense.omnilib;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper.Argb;

import java.util.Collections;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

public class OmniTooltip {
    private final String categoryTitle;
    private final List<ItemStack> itemStacks;
    private final List<Text> textList;

    private static final int DEFAULT_ICON_SIZE = 16;
    private static final int DEFAULT_PADDING = 8;
    private static final int DEFAULT_LINE_SPACING = 4;
    private static final int DEFAULT_BACKGROUND_COLOR = Argb.getArgb(150, 60, 60, 60);
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;
    private static final int DEFAULT_MAX_HEIGHT = 140;
    private static final double DEFAULT_SCROLL_SPEED = 25.0;

    private final int iconSize;
    private final int padding;
    private final int lineSpacing;
    private final int backgroundColor;
    private final Identifier backgroundTexture;
    private final int textColor;
    private final Identifier customIconTexture;
    private final int customIconWidth;
    private final int customIconHeight;
    private final int maxHeight;
    private final double scrollSpeed;

    public OmniTooltip(
            String categoryTitle,
            List<ItemStack> itemStacks,
            List<Text> textList,
            int iconSize,
            int padding,
            int lineSpacing,
            int backgroundColor,
            Identifier backgroundTexture,
            int textColor,
            Identifier customIconTexture,
            int customIconWidth,
            int customIconHeight,
            Integer maxHeight,
            Double scrollSpeed) {
        if (backgroundColor == 0 && backgroundTexture == null) {
            throw new IllegalArgumentException("Either backgroundColor or backgroundTexture must be specified.");
        }
        this.categoryTitle = categoryTitle;
        this.itemStacks = itemStacks != null ? itemStacks : Collections.emptyList();
        this.textList = textList != null ? textList : Collections.emptyList();
        this.iconSize = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        this.padding = padding > 0 ? padding : DEFAULT_PADDING;
        this.lineSpacing = lineSpacing > 0 ? lineSpacing : DEFAULT_LINE_SPACING;
        this.backgroundColor = backgroundColor > 0 ? backgroundColor : DEFAULT_BACKGROUND_COLOR;
        this.backgroundTexture = backgroundTexture;
        this.textColor = textColor > 0 ? textColor : DEFAULT_TEXT_COLOR;
        this.customIconTexture = customIconTexture;
        this.customIconWidth = customIconWidth > 0 ? customIconWidth : iconSize;
        this.customIconHeight = customIconHeight > 0 ? customIconHeight : iconSize;
        this.maxHeight = maxHeight != null && maxHeight > 0 ? maxHeight : DEFAULT_MAX_HEIGHT;
        this.scrollSpeed = scrollSpeed != null && scrollSpeed > 0 ? scrollSpeed : DEFAULT_SCROLL_SPEED;
    }

    public OmniTooltip(
            String categoryTitle,
            List<ItemStack> itemStacks,
            List<Text> textList,
            int iconSize,
            int padding,
            int lineSpacing,
            int backgroundColor,
            Identifier backgroundTexture,
            int textColor,
            Identifier customIconTexture,
            int customIconWidth,
            int customIconHeight) {
        this(categoryTitle, itemStacks, textList, iconSize, padding, lineSpacing, backgroundColor, backgroundTexture, textColor, customIconTexture, customIconWidth, customIconHeight, null, null);
    }

    public void render(MatrixStack matrices, TextRenderer textRenderer, int x, int y) {
        int tooltipWidth = getTooltipWidth(textRenderer);
        int tooltipHeight = getTooltipHeight();
        boolean requiresScrolling = tooltipHeight > maxHeight;

        matrices.push();
        matrices.translate(0, 0, 1);

        int displayHeight = requiresScrolling ? maxHeight : tooltipHeight;
        drawBackground(matrices, x, y, tooltipWidth, displayHeight);

        int yOffset = padding;

        DrawableHelper.drawTextWithShadow(
                matrices,
                textRenderer,
                Text.literal(categoryTitle).styled(style -> style.withBold(true)),
                x + padding,
                y + yOffset,
                textColor
        );
        yOffset += iconSize + lineSpacing;

        if (requiresScrolling) {
            renderScrollableContent(matrices, textRenderer, x, y + yOffset, tooltipWidth, displayHeight - yOffset);
        } else {
            renderContent(matrices, textRenderer, x, y + yOffset);
        }

        matrices.pop();
    }

    private void renderScrollableContent(MatrixStack matrices, TextRenderer textRenderer, int x, int y, int width, int height) {
        int contentHeight = getTooltipHeight() - padding;
        if (contentHeight <= 0) return;
    
        double time = Util.getMeasuringTimeMs() / 1000.0;
        double scrollAmount = (time * scrollSpeed) % (contentHeight + DEFAULT_LINE_SPACING);
    
        int yOffset = -((int) scrollAmount);
    
        DrawableHelper.enableScissor(x, y, x + width, y + height - padding);
    
        renderContent(matrices, textRenderer, x, y + yOffset);
    
        int dividerY = y + yOffset + contentHeight + (DEFAULT_LINE_SPACING / 2);
        renderDivider(matrices, x, dividerY, width);
    
        renderContent(matrices, textRenderer, x, y + yOffset + contentHeight + DEFAULT_LINE_SPACING);
    
        DrawableHelper.disableScissor();
    }    
    
    private void renderDivider(MatrixStack matrices, int x, int y, int width) {
        int lineWidth = (int) (width * 0.75);
        int lineStartX = x + (width - lineWidth) / 2;
        int adjustedY = y - (iconSize + lineSpacing * 4) / 2;
    
        DrawableHelper.fill(matrices, lineStartX, adjustedY, lineStartX + lineWidth, adjustedY + 1, 0xFFFFFFFF);
    }      

    private void renderContent(MatrixStack matrices, TextRenderer textRenderer, int x, int y) {
        int yOffset = 0;

        for (int i = 0; i < textList.size(); i++) {
            if (customIconTexture != null) {
                drawCustomIcon(matrices, x + padding, y + yOffset);
            } else if (i < itemStacks.size()) {
                ItemStack itemStack = itemStacks.get(i);
                drawItem(itemStack, x + padding, y + yOffset);
            }

            DrawableHelper.drawTextWithShadow(
                    matrices,
                    textRenderer,
                    textList.get(i),
                    x + iconSize + padding * 2,
                    y + yOffset + (iconSize / 2 - textRenderer.fontHeight / 2),
                    textColor
            );

            yOffset += iconSize + lineSpacing;
        }
    }

    private int getTooltipWidth(TextRenderer textRenderer) {
        int maxWidth = textRenderer.getWidth(Text.literal(categoryTitle).styled(style -> style.withBold(true)));
        for (Text text : textList) {
            int textWidth = textRenderer.getWidth(text);
            if (textWidth > maxWidth) {
                maxWidth = textWidth;
            }
        }
        return maxWidth + iconSize + padding * 3;
    }

    private int getTooltipHeight() {
        return (textList.size() + 1) * (iconSize + lineSpacing) - lineSpacing + padding * 2;
    }

    private void drawBackground(MatrixStack matrices, int x, int y, int width, int height) {
        if (backgroundTexture != null) {
            RenderSystem.setShaderTexture(0, backgroundTexture);
            DrawableHelper.drawTexture(matrices, x - padding, y - padding, 0, 0, width + padding * 2, height + padding * 2, width + padding * 2, height + padding * 2);
        } else {
            DrawableHelper.fill(matrices, x - padding, y - padding, x + width + padding, y + height + padding, backgroundColor);
        }
    }

    private void drawCustomIcon(MatrixStack matrices, int x, int y) {
        RenderSystem.setShaderTexture(0, customIconTexture);
        DrawableHelper.drawTexture(
                matrices,
                x,
                y,
                0,
                0,
                customIconWidth,
                customIconHeight,
                customIconWidth,
                customIconHeight
        );
    }

    private void drawItem(ItemStack itemStack, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getItemRenderer().renderInGui(itemStack, x, y);
    }
}