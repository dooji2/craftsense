package com.dooji.craftsense.omnilib;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class OmniToast implements Toast {

    private static final ResourceLocation DEFAULT_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("omnilib", "textures/gui/toast.png");
    private static final int DEFAULT_ICON_SIZE = 16;
    private static final int DEFAULT_TEXTURE_WIDTH = 160;
    private static final int DEFAULT_TEXTURE_HEIGHT = 32;
    private static final long DEFAULT_DURATION = 5000;
    private static final int DEFAULT_TITLE_COLOR = 0xFFFFFF;
    private static final int DEFAULT_DESCRIPTION_COLOR = 0xAAAAAA;

    private final ResourceLocation backgroundTexture;
    private final ResourceLocation iconTexture;
    private final ItemStack iconItemStack;
    private final int iconSize;
    private int textureWidth;
    private final int configTextureWidth;
    private final int textureHeight;
    private Component title;
    private Component description;
    private final long duration;
    private long time;
    private boolean hidden;
    private long lastElapsed = System.currentTimeMillis();
    private final int titleColor;
    private final int descriptionColor;

    public OmniToast(Component title, Component description, long duration, int titleColor, int descriptionColor,
                     ResourceLocation backgroundTexture, ResourceLocation iconTexture, ItemStack iconItemStack,
                     int iconSize, int textureWidth, int textureHeight) {
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.titleColor = titleColor;
        this.descriptionColor = descriptionColor;
        this.backgroundTexture = backgroundTexture != null ? backgroundTexture : DEFAULT_BACKGROUND_TEXTURE;
        this.iconTexture = iconTexture;
        this.iconItemStack = iconItemStack;
        this.iconSize = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        this.configTextureWidth = textureWidth > 0 ? textureWidth : DEFAULT_TEXTURE_WIDTH;
        this.textureWidth = this.configTextureWidth;
        this.textureHeight = textureHeight > 0 ? textureHeight : DEFAULT_TEXTURE_HEIGHT;
        this.time = 0;
        this.hidden = false;
    }

    public OmniToast(Component title, Component description) {
        this(title, description, DEFAULT_DURATION, DEFAULT_TITLE_COLOR, DEFAULT_DESCRIPTION_COLOR,
                DEFAULT_BACKGROUND_TEXTURE, null, null, DEFAULT_ICON_SIZE, DEFAULT_TEXTURE_WIDTH, DEFAULT_TEXTURE_HEIGHT);
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        updateWidth();

        guiGraphics.blit(backgroundTexture, 0, 0, 0, 0, width(), height(), textureWidth, textureHeight);

        if (iconItemStack != null) {
            guiGraphics.renderItem(iconItemStack, 10, (textureHeight - iconSize) / 2);
        } else if (iconTexture != null) {
            guiGraphics.blit(iconTexture, 10, (textureHeight - iconSize) / 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }

        guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 38, 7, this.titleColor, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.description, 38, 18, this.descriptionColor, false);

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
    public int width() {
        return textureWidth;
    }

    @Override
    public int height() {
        return textureHeight;
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

    private int countCharacters(Component text) {
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
