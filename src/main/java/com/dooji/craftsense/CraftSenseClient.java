package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.network.CraftSenseClientNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

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
                    SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                        Text.literal("Welcome to CraftSense"),
                        Text.literal("Toggle CraftSense with " + toggleKeyText));
                }
            }

            while (CraftSenseKeyBindings.toggleKey.wasPressed()) {
                ConfigurationManager configManager = CraftSense.configManager;
                configManager.toggleEnabled();
                boolean enabled = configManager.isEnabled();

                SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("CraftSense " + (enabled ? "Enabled" : "Disabled")),
                    Text.literal("CraftSense has been " + (enabled ? "enabled" : "disabled")));

                client.player.playSound(enabled ? SoundEvents.BLOCK_LEVER_CLICK : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF, 1.0F, 1.0F);
            }

        });
    }
}