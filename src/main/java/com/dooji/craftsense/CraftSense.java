package com.dooji.craftsense;

import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.network.CraftSenseNetworking;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CraftSense.MOD_ID)
public class CraftSense {
	public static final String MOD_ID = "craftsense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ConfigurationManager configManager = new ConfigurationManager();

	public CraftSense() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::commonSetup);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		event.enqueueWork(CraftSenseNetworking::init);
	}
}