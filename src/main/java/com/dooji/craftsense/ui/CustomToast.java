package com.dooji.craftsense.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CustomToast implements Toast {
    private final Identifier backgroundTexture;
    private final Identifier iconTexture;
    private final int iconSize;
    private int textureWidth;
    private final int configTextureWidth;
    private final int textureHeight;
    private Text title;
    private Text description;
    public long duration;
    private long time;
    private boolean hidden;
    private long lastElapsed = System.currentTimeMillis();
    private final int titleColor;
    private final int descriptionColor;

    public CustomToast(Text title, Text description, long duration, int titleColor, int descriptionColor,
                       Identifier backgroundTexture, Identifier iconTexture, int iconSize, int textureWidth, int textureHeight) {
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.titleColor = titleColor;
        this.descriptionColor = descriptionColor;
        this.backgroundTexture = backgroundTexture;
        this.iconTexture = iconTexture;
        this.iconSize = iconSize;
        this.configTextureWidth = textureWidth;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.time = 0;
        this.hidden = false;
    }

    @Override
    public Visibility draw(DrawContext drawContext, ToastManager manager, long currentTime) {
        RenderSystem.setShaderTexture(0, backgroundTexture);
        drawContext.drawTexture(backgroundTexture, 0, 0, 0, 0, getWidth(), getHeight(), textureWidth, textureHeight);

        RenderSystem.setShaderTexture(0, iconTexture);
        drawContext.drawTexture(iconTexture, 10, (textureHeight - iconSize) / 2, 0, 0, iconSize, iconSize, iconSize, iconSize);

        drawContext.drawText(manager.getClient().textRenderer, this.title, 38, 7, this.titleColor, false);
        drawContext.drawText(manager.getClient().textRenderer, this.description, 38, 18, this.descriptionColor, false);

        if (!hidden) {
            time += System.currentTimeMillis() - lastElapsed;
            lastElapsed = System.currentTimeMillis();
        }

        if (time >= duration) {
            hidden = true;
            return Visibility.HIDE;
        }

        return Visibility.SHOW;
    }

    @Override
    public int getWidth() {
        int titleLength = countCharacters(title);
        int descriptionLength = countCharacters(description);
        int contentLength = Math.max(titleLength, descriptionLength);

        if (contentLength > 22 && this.textureWidth < (contentLength - 22) * 5 + configTextureWidth) {
            int extraWidth = contentLength - 22;
            this.textureWidth += extraWidth * 5;
        }

        return this.textureWidth;
    }

    @Override
    public int getHeight() {
        return textureHeight;
    }

    @Override
    public int getRequiredSpaceCount() {
        return 1;
    }

    private int countCharacters(Text text) {
        int count = 0;
        String string = text.getString();
        for (int i = 0; i < string.length(); i++) {
            if (string.codePointAt(i) < 128) {
                count++;
            }
        }
        return count;
    }
}