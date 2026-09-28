package com.dooji.craftsense;

import com.dooji.craftsense.manager.CategoryGenerator;
import com.dooji.craftsense.manager.CraftSenseTracker;

import net.minecraft.client.Minecraft;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CraftSense.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CraftSenseClient {
    private static boolean hasEnteredWorld = false;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CategoryGenerator.generateCategories();

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent tickEvent) -> {
            if (tickEvent.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null && !hasEnteredWorld) {
                hasEnteredWorld = true;
                CraftSenseTracker.checkPlayerConditions();
            }
        });
    }
}