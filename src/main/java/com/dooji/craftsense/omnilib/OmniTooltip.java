package com.dooji.craftsense.omnilib;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper.Argb;

import java.util.Collections;
import java.util.List;

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

    public void render(DrawContext context, TextRenderer textRenderer, int x, int y) {
        int tooltipWidth = getTooltipWidth(textRenderer);
        int tooltipHeight = getTooltipHeight();
        boolean requiresScrolling = tooltipHeight > maxHeight;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1);

        int displayHeight = requiresScrolling ? maxHeight : tooltipHeight;
        drawBackground(context, x, y, tooltipWidth, displayHeight);

        int yOffset = padding;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(categoryTitle).styled(style -> style.withBold(true)),
                x + padding,
                y + yOffset,
                textColor
        );
        yOffset += iconSize + lineSpacing;

        if (requiresScrolling) {
            renderScrollableContent(context, textRenderer, x, y + yOffset, tooltipWidth, displayHeight - yOffset);
        } else {
            renderContent(context, textRenderer, x, y + yOffset);
        }

        context.getMatrices().pop();
    }

    private void renderScrollableContent(DrawContext context, TextRenderer textRenderer, int x, int y, int width, int height) {
        int contentHeight = getTooltipHeight() - padding;
        if (contentHeight <= 0) return;
    
        double time = Util.getMeasuringTimeMs() / 1000.0;
        double scrollAmount = (time * scrollSpeed) % (contentHeight + DEFAULT_LINE_SPACING);
    
        int yOffset = -((int) scrollAmount);
    
        context.enableScissor(x, y, x + width, y + height - padding);
    
        renderContent(context, textRenderer, x, y + yOffset);
    
        int dividerY = y + yOffset + contentHeight + (DEFAULT_LINE_SPACING / 2);
        renderDivider(context, x, dividerY, width);
    
        renderContent(context, textRenderer, x, y + yOffset + contentHeight + DEFAULT_LINE_SPACING);
    
        context.disableScissor();
    }    
    
    private void renderDivider(DrawContext context, int x, int y, int width) {
        int lineWidth = (int) (width * 0.75);
        int lineStartX = x + (width - lineWidth) / 2;
        int adjustedY = y - (iconSize + lineSpacing * 4) / 2;
    
        context.fill(lineStartX, adjustedY, lineStartX + lineWidth, adjustedY + 1, 0xFFFFFFFF);
    }      

    private void renderContent(DrawContext context, TextRenderer textRenderer, int x, int y) {
        int yOffset = 0;

        for (int i = 0; i < textList.size(); i++) {
            if (customIconTexture != null) {
                drawCustomIcon(context, x + padding, y + yOffset);
            } else if (i < itemStacks.size()) {
                ItemStack itemStack = itemStacks.get(i);
                context.drawItem(itemStack, x + padding, y + yOffset);
            }

            context.drawTextWithShadow(
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

    private void drawBackground(DrawContext context, int x, int y, int width, int height) {
        if (backgroundTexture != null) {
            context.drawTexture(backgroundTexture, x - padding, y - padding, 0, 0, width + padding * 2, height + padding * 2);
        } else {
            context.fill(x - padding, y - padding, x + width + padding, y + height + padding, backgroundColor);
        }
    }

    private void drawCustomIcon(DrawContext context, int x, int y) {
        context.drawTexture(
                customIconTexture,
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
}