package com.dooji.craftsense;

import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.network.CraftSenseNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftSense implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("craftsense-client");

	public static final String MOD_ID = "craftsense";
	public static final ConfigurationManager configManager = new ConfigurationManager();

	@Override
	public void onInitialize() {
		CraftSenseNetworking.init();
		LOGGER.info("Welcome to CraftSense!");
	}
}