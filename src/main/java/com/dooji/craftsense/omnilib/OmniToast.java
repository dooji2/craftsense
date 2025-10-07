package com.dooji.craftsense.omnilib;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class OmniToast implements Toast {
    private static final Identifier DEFAULT_BACKGROUND_TEXTURE = Identifier.of("omnilib", "textures/gui/toast.png");
    private static final Identifier DEFAULT_ICON_TEXTURE = Identifier.of("minecraft", "icon.png");
    private static final int DEFAULT_ICON_SIZE = 16;
    private static final int DEFAULT_TEXTURE_WIDTH = 160;
    private static final int DEFAULT_TEXTURE_HEIGHT = 32;
    private static final long DEFAULT_DURATION = 5000;
    private static final int DEFAULT_TITLE_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_DESCRIPTION_COLOR = 0xAAAAAA;

    private final Identifier backgroundTexture;
    private final Identifier iconTexture;
    private final ItemStack iconItemStack;
    private final int iconSize;
    private int textureWidth;
    private final int configTextureWidth;
    private final int textureHeight;
    private Text title;
    private Text description;
    private long duration;
    private long time;
    private boolean hidden;
    private long lastElapsed = System.currentTimeMillis();
    private final int titleColor;
    private final int descriptionColor;

    public OmniToast(
            Text title,
            Text description,
            long duration,
            int titleColor,
            int descriptionColor,
            Identifier backgroundTexture,
            Identifier iconTexture,
            ItemStack iconItemStack,
            int iconSize,
            int textureWidth,
            int textureHeight) {
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.titleColor = titleColor;
        this.descriptionColor = descriptionColor;
        this.backgroundTexture = backgroundTexture != null ? backgroundTexture : DEFAULT_BACKGROUND_TEXTURE;
        this.iconTexture = iconTexture != null ? iconTexture : DEFAULT_ICON_TEXTURE;
        this.iconItemStack = iconItemStack;
        this.iconSize = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        this.configTextureWidth = textureWidth > 0 ? textureWidth : DEFAULT_TEXTURE_WIDTH;
        this.textureWidth = this.configTextureWidth;
        this.textureHeight = textureHeight > 0 ? textureHeight : DEFAULT_TEXTURE_HEIGHT;
        this.time = 0;
        this.hidden = false;
    }

    public OmniToast(Text title, Text description) {
        this(title, description, DEFAULT_DURATION, DEFAULT_TITLE_COLOR, DEFAULT_DESCRIPTION_COLOR,
                DEFAULT_BACKGROUND_TEXTURE, DEFAULT_ICON_TEXTURE, null,
                DEFAULT_ICON_SIZE, DEFAULT_TEXTURE_WIDTH, DEFAULT_TEXTURE_HEIGHT);
    }

    public void setTitle(Text title) {
        this.title = title;
    }

    public void setDescription(Text description) {
        this.description = description;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public Visibility getVisibility() {
        return hidden ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void draw(DrawContext drawContext, TextRenderer textRenderer, long currentTime) {
        updateWidth();
        drawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, backgroundTexture, 0, 0, getWidth(), getHeight());

        if (iconItemStack != null) {
            drawContext.drawItem(iconItemStack, 10, (textureHeight - iconSize) / 2);
        } else {
            drawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, iconTexture, 10, (textureHeight - iconSize) / 2, iconSize, iconSize);
        }

        drawContext.drawText(textRenderer, this.title, 38, 7, this.titleColor, false);
        drawContext.drawText(textRenderer, this.description, 38, 18, this.descriptionColor, false);

        if (!hidden) {
            time += System.currentTimeMillis() - lastElapsed;
            lastElapsed = System.currentTimeMillis();
        }

        if (time >= duration) {
            hidden = true;
        }
    }

    @Override
    public void update(ToastManager manager, long currentTime) {
        if (time >= duration) {
            hidden = true;
        }
    }

    @Override
    public int getWidth() {
        return textureWidth;
    }

    @Override
    public int getHeight() {
        return textureHeight;
    }

    @Override
    public int getRequiredSpaceCount() {
        return 1;
    }

    private void updateWidth() {
        int titleLength = countCharacters(title);
        int descriptionLength = countCharacters(description);
        int contentLength = Math.max(titleLength, descriptionLength);

        if (contentLength > 22 && textureWidth < (contentLength - 22) * 5 + configTextureWidth) {
            int extraWidth = contentLength - 22;
            textureWidth += extraWidth * 5;
        }
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