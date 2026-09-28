package com.dooji.craftsense;

import com.dooji.craftsense.manager.ConfigurationManager;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CraftSense.MOD_ID)
public class CraftSense {
	public static final String MOD_ID = "craftsense";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ConfigurationManager configManager = new ConfigurationManager();
}