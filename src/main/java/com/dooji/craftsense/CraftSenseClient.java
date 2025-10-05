package com.dooji.craftsense;

import java.util.List;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.network.CraftSenseClientNetworking;
import com.dooji.craftsense.omnilib.OmniButton;
import com.dooji.craftsense.omnilib.OmniToast;
import com.dooji.craftsense.omnilib.OmniTooltip;
import com.dooji.craftsense.ui.CraftSenseStatsScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CraftSenseClient implements ClientModInitializer {
    private static boolean hasEnteredWorld = false;

    @Override
    public void onInitializeClient() {
        CraftSenseKeyBindings.register();
        CategoryGenerator.generateCategories();
        CraftSenseClientNetworking.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !hasEnteredWorld) {
                hasEnteredWorld = true;

                ConfigurationManager configManager = CraftSense.configManager;
                CraftSenseTracker.checkPlayerConditions();

                if (configManager.isFirstTime()) {
                    String toggleKeyText = CraftSenseKeyBindings.toggleKey.getBoundKeyLocalizedText().getString();
                    createToast("Welcome to CraftSense", "Toggle CraftSense with " + toggleKeyText);
                }
            }

            while (CraftSenseKeyBindings.toggleKey.wasPressed()) {
                ConfigurationManager configManager = CraftSense.configManager;
                configManager.toggleEnabled();
                boolean enabled = configManager.isEnabled();

                createToast("CraftSense " + (enabled ? "Enabled" : "Disabled"), "CraftSense has been " + (enabled ? "enabled" : "disabled"));

                client.player.playSound(enabled ? SoundEvents.BLOCK_LEVER_CLICK : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF, 1.0F, 1.0F);
            }

            while (CraftSenseKeyBindings.openStatsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CraftSenseStatsScreen());
                }
            }
        });
    }

    public static void createToast(String titleKey, String messageKey) {
        Text title = Text.translatable(titleKey);
        Text description = Text.translatable(messageKey);
        Identifier iconTexture = CraftSense.configManager.isEnabled()
                ? Identifier.of("minecraft", "textures/block/redstone_lamp_on.png")
                : Identifier.of("minecraft", "textures/block/redstone_lamp.png");

        showToast(
            title,
            description,
            5000,
            0xFFFFFF,
            0xAAAAAA,
            null,
            iconTexture,
            null,
            16,
            170,
            32
        );
    }

	public static void showTooltip(
			MatrixStack matrices,
			TextRenderer textRenderer,
			String categoryTitle,
			List<ItemStack> itemStacks,
			List<Text> textList,
			int backgroundColor,
			Identifier backgroundTexture,
			int textColor,
			Identifier customTexture,
			int x,
			int y) {

		OmniTooltip tooltip = new OmniTooltip(
				categoryTitle,
				itemStacks,
				textList,
				16,
				8,
				4,
				backgroundColor,
				backgroundTexture,
				textColor,
				customTexture,
				16,
				16
		);

		tooltip.render(matrices, textRenderer, x, y);
	}

	public static void showToast(
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

		OmniToast toast = new OmniToast(
				title,
				description,
				duration,
				titleColor,
				descriptionColor,
				backgroundTexture,
				iconTexture,
				iconItemStack,
				iconSize,
				textureWidth,
				textureHeight
		);

		MinecraftClient.getInstance().getToastManager().add(toast);
	}

	public static OmniButton createOmniButton(
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
		return new OmniButton(x, y, width, height, message, color, hoverColor, textColor, textHoverColor, onPress);
	}
}