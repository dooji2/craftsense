package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.omnilib.OmniButton;
import com.dooji.craftsense.omnilib.OmniToast;
import com.dooji.craftsense.omnilib.OmniTooltip;
import com.dooji.craftsense.ui.CraftSenseStatsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CraftSenseClient {
    private static boolean hasEnteredWorld = false;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CraftSenseKeyBindings::onRegisterKeyMappings);
        CategoryGenerator.generateCategories();
        NeoForge.EVENT_BUS.addListener(CraftSenseClient::onClientTick);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

        if (client.player != null && !hasEnteredWorld) {
            hasEnteredWorld = true;
            ConfigurationManager configManager = CraftSense.configManager;
            CraftSenseTracker.checkPlayerConditions();

            if (configManager.isFirstTime()) {
                String toggleKeyText = CraftSenseKeyBindings.toggleKey.getTranslatedKeyMessage().getString();
                createToast("Welcome to CraftSense", "Toggle CraftSense with " + toggleKeyText);
            }
        }

        if (client.player != null) {
            while (CraftSenseKeyBindings.toggleKey.consumeClick()) {
                ConfigurationManager configManager = CraftSense.configManager;
                configManager.toggleEnabled();
                boolean enabled = configManager.isEnabled();

                createToast("CraftSense " + (enabled ? "Enabled" : "Disabled"),
                        "CraftSense has been " + (enabled ? "enabled" : "disabled"));

                client.player.playSound(
                        enabled ? SoundEvents.LEVER_CLICK : SoundEvents.WOODEN_BUTTON_CLICK_OFF,
                        1.0f, 1.0f);
            }

            while (CraftSenseKeyBindings.openStatsKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new CraftSenseStatsScreen());
                }
            }
        }
    }

    public static void createToast(String titleKey, String messageKey) {
        Component title = Component.translatable(titleKey);
        Component description = Component.translatable(messageKey);
        ResourceLocation iconTexture = CraftSense.configManager.isEnabled()
                ? ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp_on.png")
                : ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp.png");

        showToast(title, description, 5000, 0xFFFFFF, 0xAAAAAA, null, iconTexture, null, 16, 170, 32);
    }

    public static void showToast(
            Component title,
            Component description,
            long duration,
            int titleColor,
            int descriptionColor,
            ResourceLocation backgroundTexture,
            ResourceLocation iconTexture,
            ItemStack iconItemStack,
            int iconSize,
            int textureWidth,
            int textureHeight) {

        OmniToast toast = new OmniToast(title, description, duration, titleColor, descriptionColor,
                backgroundTexture, iconTexture, iconItemStack, iconSize, textureWidth, textureHeight);
        Minecraft.getInstance().getToasts().addToast(toast);
    }

    public static void showTooltip(
            GuiGraphics context,
            Font font,
            String categoryTitle,
            List<ItemStack> itemStacks,
            List<Component> textList,
            int backgroundColor,
            ResourceLocation backgroundTexture,
            int textColor,
            ResourceLocation customTexture,
            int x,
            int y) {

        OmniTooltip tooltip = new OmniTooltip(categoryTitle, itemStacks, textList, 16, 8, 4,
                backgroundColor, backgroundTexture, textColor, customTexture, 16, 16);
        tooltip.render(context, font, x, y);
    }

    public static OmniButton createOmniButton(
            int x, int y, int width, int height,
            Component message,
            int color, int hoverColor,
            int textColor, int textHoverColor,
            Runnable onPress) {
        return new OmniButton(x, y, width, height, message, color, hoverColor, textColor, textHoverColor, onPress);
    }
}
