package com.dooji.craftsense.omnilib;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class OmniButton extends ClickableWidget {
    private final Runnable onPress;
    private final int color;
    private final int hoverColor;
    private final int textColor;
    private final int textHoverColor;
    private final Identifier texture;
    private final Identifier hoverTexture;
    private final boolean isImageButton;

    public OmniButton(
            int x, 
            int y, 
            int width, 
            int height, 
            Text message, 
            int color, 
            int hoverColor, 
            int textColor, 
            int textHoverColor, 
            Runnable onPress) {
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

    public OmniButton(int x, int y, int width, int height, Identifier texture, int color, int hoverColor, Runnable onPress) {
        super(x, y, width, height, Text.empty());
        this.onPress = onPress;
        this.color = color;
        this.hoverColor = hoverColor;
        this.textColor = 0;
        this.textHoverColor = 0;
        this.texture = texture;
        this.hoverTexture = null;
        this.isImageButton = true;
    }

    public OmniButton(int x, int y, int width, int height, Identifier texture, Identifier hoverTexture, int color, int hoverColor, Runnable onPress) {
        super(x, y, width, height, Text.empty());
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
        MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onPress.run();
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();

        if (isImageButton) {
            renderImageButton(matrices, hovered);
        } else {
            renderTextButton(matrices, hovered);
        }
    }

    private void renderImageButton(MatrixStack matrices, boolean hovered) {
        Identifier currentTexture = hovered && hoverTexture != null ? hoverTexture : texture;
        int currentColor = hovered ? hoverColor : color;
    
        DrawableHelper.fill(matrices, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, currentColor);
    
        if (currentTexture != null) {
            int iconSize = Math.min(this.width, this.height) / 2;
            int iconX = this.getX() + (this.width - iconSize) / 2;
            int iconY = this.getY() + (this.height - iconSize) / 2;
    
            RenderSystem.setShaderTexture(0, currentTexture);
            DrawableHelper.drawTexture(matrices, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }    

    private void renderTextButton(MatrixStack matrices, boolean hovered) {
        int currentColor = hovered ? hoverColor : color;
        int currentTextColor = hovered ? textHoverColor : textColor;

        DrawableHelper.fill(matrices, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, currentColor);

        MinecraftClient client = MinecraftClient.getInstance();
        Text message = this.getMessage();
        int textWidth = client.textRenderer.getWidth(message);
        int textHeight = client.textRenderer.fontHeight;

        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - textHeight) / 2 + 1;

        client.textRenderer.draw(matrices, message, textX, textY, currentTextColor);
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}