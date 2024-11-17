package com.dooji.craftsense.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper.Argb;

import java.util.List;

public class CustomTooltip {
    private final String categoryTitle;
    private final List<ItemStack> itemStacks;
    private final List<Text> textList;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 8;
    private static final int LINE_SPACING = 4;
    private static final int BACKGROUND_COLOR = Argb.getArgb(150, 60, 60, 60);

    public CustomTooltip(String categoryTitle, List<ItemStack> itemStacks, List<Text> textList) {
        this.categoryTitle = categoryTitle;
        this.itemStacks = itemStacks;
        this.textList = textList;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int x, int y) {
        int tooltipWidth = getTooltipWidth(textRenderer);
        int tooltipHeight = getTooltipHeight();

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1);

        drawRoundedBackground(context, x, y, tooltipWidth, tooltipHeight);

        int yOffset = PADDING;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(categoryTitle).styled(style -> style.withBold(true)),
                x + PADDING,
                y + yOffset,
                0xFFFFFF
        );
        yOffset += ICON_SIZE + LINE_SPACING;

        for (int i = 0; i < textList.size(); i++) {
            if (i < itemStacks.size()) {
                ItemStack itemStack = itemStacks.get(i);
                context.drawItem(itemStack, x + PADDING, y + yOffset);
            }

            context.drawTextWithShadow(
                    textRenderer,
                    textList.get(i),
                    x + ICON_SIZE + PADDING * 2,
                    y + yOffset + (ICON_SIZE / 2 - textRenderer.fontHeight / 2),
                    0xFFFFFF
            );

            yOffset += ICON_SIZE + LINE_SPACING;
        }

        context.getMatrices().pop();
    }

    private int getTooltipWidth(TextRenderer textRenderer) {
        int maxWidth = textRenderer.getWidth(Text.literal(categoryTitle).styled(style -> style.withBold(true)));
        for (Text text : textList) {
            int textWidth = textRenderer.getWidth(text);
            if (textWidth > maxWidth) {
                maxWidth = textWidth;
            }
        }
        return maxWidth + ICON_SIZE + PADDING * 3;
    }

    private int getTooltipHeight() {
        return (textList.size() + 1) * (ICON_SIZE + LINE_SPACING) - LINE_SPACING + PADDING * 2;
    }

    private void drawRoundedBackground(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - PADDING, y - PADDING, x + width + PADDING, y + height + PADDING, BACKGROUND_COLOR);
    }
}