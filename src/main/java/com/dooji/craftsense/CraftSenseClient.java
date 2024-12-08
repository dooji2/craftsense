package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.ui.CraftSenseStatsScreen;
import com.dooji.omnilib.OmnilibClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !hasEnteredWorld) {
                hasEnteredWorld = true;

                ConfigurationManager configManager = CraftSense.configManager;
                CraftSenseTracker.checkPlayerConditions();

                if (configManager.isFirstTime()) {
                    String toggleKeyText = CraftSenseKeyBindings.toggleKey.getBoundKeyLocalizedText().getString();
                    createToast("Welcome to CraftSense", "Toggle CraftSense with " + toggleKeyText);
                    configManager.toggleFirstTime();
                }
            }

            while (CraftSenseKeyBindings.toggleKey.wasPressed()) {
                ConfigurationManager configManager = CraftSense.configManager;
                configManager.toggleEnabled();
                boolean enabled = configManager.isEnabled();

                createToast("CraftSense " + (enabled ? "Enabled" : "Disabled"),
                        "CraftSense has been " + (enabled ? "enabled" : "disabled"));

                client.player.playSound(enabled ? SoundEvents.BLOCK_LEVER_CLICK : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF);
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

        OmnilibClient.showToast(
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
}