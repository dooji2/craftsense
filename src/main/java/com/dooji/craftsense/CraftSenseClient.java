package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.CraftSenseTracker;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = CraftSense.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class CraftSenseClient {
    private static boolean hasEnteredWorld = false;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CategoryGenerator.generateCategories();

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tickEvent) -> {
            if (Minecraft.getInstance().player != null && !hasEnteredWorld) {
                hasEnteredWorld = true;
                CraftSenseTracker.checkPlayerConditions();
            }
        });
    }
}