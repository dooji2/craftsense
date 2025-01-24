package com.dooji.craftsense.network;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CraftSenseClientNetworking {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RecordCraftPayload.ID, (payload, context) -> {
            context.client().execute(() -> recordCraft(payload));
        });
    }

    private static void recordCraft(RecordCraftPayload payload) {
        CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
        String category = CategoryManager.getCategory(payload.itemStack().getItem());
        habitsConfig.recordCraft(category, payload.itemStack().getItem().getTranslationKey());
    }
}