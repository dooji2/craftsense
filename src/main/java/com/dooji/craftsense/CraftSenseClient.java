package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.CraftSenseTracker;
import com.dooji.craftsense.network.CraftSenseClientNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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
                CraftSenseTracker.checkPlayerConditions();
            }
        });
    }
}