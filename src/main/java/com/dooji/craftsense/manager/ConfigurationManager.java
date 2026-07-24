package com.dooji.craftsense.manager;

import com.dooji.craftsense.CraftSense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config/CraftSense/config.json");
    private static final String FIRST_TIME_KEY = "firstTime";

    private boolean firstTime;

    public ConfigurationManager() {
        loadConfig();
    }

    public boolean isFirstTime() {
        return firstTime;
    }

    public void toggleFirstTime() {
        this.firstTime = false;
        saveConfig();
    }

    private void loadConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    Map<String, Boolean> config = GSON.fromJson(reader, HashMap.class);
                    this.firstTime = (Boolean) config.getOrDefault(FIRST_TIME_KEY, true);
                }
            } else {
                this.firstTime = true;
                saveConfig();
            }
        } catch (IOException e) {
            CraftSense.LOGGER.error("Failed to load config", e);
            this.firstTime = true;
        }
    }

    private void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            Map<String, Boolean> config = new HashMap<>();
            config.put(FIRST_TIME_KEY, firstTime);
            GSON.toJson(config, writer);
        } catch (IOException e) {
            CraftSense.LOGGER.error("Failed to save config", e);
        }
    }
}