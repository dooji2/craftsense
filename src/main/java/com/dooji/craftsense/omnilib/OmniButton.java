package com.dooji.craftsense.omnilib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public class OmniButton extends AbstractWidget {
    private final Runnable onPress;
    private final int color;
    private final int hoverColor;
    private final int textColor;
    private final int textHoverColor;
    private final ResourceLocation texture;
    private final ResourceLocation hoverTexture;
    private final boolean isImageButton;

    public OmniButton(int x, int y, int width, int height, Component message, int color, int hoverColor, int textColor, int textHoverColor, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.color = color;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
        this.textHoverColor = textHoverColor;
        this.texture = null;
        this.hoverTexture = null;
        this.isImageButton = false;
    }

    public OmniButton(int x, int y, int width, int height, ResourceLocation texture, int color, int hoverColor, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
        this.color = color;
        this.hoverColor = hoverColor;
        this.textColor = 0;
        this.textHoverColor = 0;
        this.texture = texture;
        this.hoverTexture = null;
        this.isImageButton = true;
    }

    public OmniButton(int x, int y, int width, int height, ResourceLocation texture, ResourceLocation hoverTexture, int color, int hoverColor, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
        this.color = color;
        this.hoverColor = hoverColor;
        this.textColor = 0;
        this.textHoverColor = 0;
        this.texture = texture;
        this.hoverTexture = hoverTexture;
        this.isImageButton = true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();

        if (isImageButton) {
            renderImageButton(context, hovered);
        } else {
            renderTextButton(context, hovered);
        }
    }

    private void renderImageButton(GuiGraphics context, boolean hovered) {
        ResourceLocation currentTexture = hovered && hoverTexture != null ? hoverTexture : texture;
        int currentColor = hovered ? hoverColor : color;

        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, currentColor);

        if (currentTexture != null) {
            int iconSize = Math.min(this.width, this.height) / 2;
            int iconX = this.getX() + (this.width - iconSize) / 2;
            int iconY = this.getY() + (this.height - iconSize) / 2;
            context.blit(currentTexture, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }

    private void renderTextButton(GuiGraphics context, boolean hovered) {
        int currentColor = hovered ? hoverColor : color;
        int currentTextColor = hovered ? textHoverColor : textColor;

        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, currentColor);

        Minecraft client = Minecraft.getInstance();
        Component message = this.getMessage();
        int textWidth = client.font.width(message);
        int textHeight = client.font.lineHeight;

        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - textHeight) / 2 + 1;

        context.drawString(client.font, message, textX, textY, currentTextColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}
