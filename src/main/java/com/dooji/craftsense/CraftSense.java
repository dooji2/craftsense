package com.dooji.craftsense;

import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.ui.CustomToast;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftSense implements ModInitializer {
	public static final String MOD_ID = "craftsense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ConfigurationManager configManager = new ConfigurationManager();

	@Override
	public void onInitialize() {
		CraftSenseKeyBindings.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (CraftSenseKeyBindings.toggleKey.wasPressed()) {
				configManager.toggleEnabled();
				boolean enabled = configManager.isEnabled();

				createToast("CraftSense " + (enabled ? "Enabled" : "Disabled"), "CraftSense has been " + (enabled ? "enabled" : "disabled"));

				client.player.playSound(enabled ? SoundEvents.BLOCK_LEVER_CLICK : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF);
			}
		});

		LOGGER.info("Welcome to CraftSense!");
	}

	public static void createToast(String titleKey, String messageKey) {
		MinecraftClient client = MinecraftClient.getInstance();
		ToastManager toastManager = client.getToastManager();

		Identifier backgroundTexture = Identifier.of(MOD_ID, "textures/gui/toast.png");
		Identifier iconTexture = configManager.isEnabled() ?
				Identifier.of("minecraft", "textures/block/redstone_lamp_on.png") :
				Identifier.of("minecraft", "textures/block/redstone_lamp.png");

		CustomToast customToast = new CustomToast(
				Text.translatable(titleKey),
				Text.translatable(messageKey),
				5000,
				0xFFFFFF,
				0xAAAAAA,
				backgroundTexture,
				iconTexture,
				16,
				170,
				32
		);
		toastManager.add(customToast);
	}
}