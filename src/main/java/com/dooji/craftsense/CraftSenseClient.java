package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.ui.CustomToast;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.ToastManager;
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
        });
    }

    public static void createToast(String titleKey, String messageKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        ToastManager toastManager = client.getToastManager();

        Identifier backgroundTexture = Identifier.of(CraftSense.MOD_ID, "textures/gui/toast.png");
        Identifier iconTexture = CraftSense.configManager.isEnabled() ?
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