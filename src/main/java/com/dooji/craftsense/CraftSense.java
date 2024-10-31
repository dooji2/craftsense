package com.dooji.craftsense;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftSense implements ModInitializer {
	public static final String MOD_ID = "craftsense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Welcome to CraftSense!");
	}
}