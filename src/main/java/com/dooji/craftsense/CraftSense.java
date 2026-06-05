package com.dooji.craftsense;

import com.dooji.craftsense.manager.ConfigurationManager;
import com.dooji.craftsense.network.CraftSenseNetworking;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CraftSense.MOD_ID)
public class CraftSense {
    public static final Logger LOGGER = LoggerFactory.getLogger("craftsense");
    public static final String MOD_ID = "craftsense";
    public static final ConfigurationManager configManager = new ConfigurationManager();

    public CraftSense(IEventBus modEventBus) {
        CraftSenseNetworking.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            CraftSenseClient.init(modEventBus);
        }
        LOGGER.info("Welcome to CraftSense!");
    }
}
