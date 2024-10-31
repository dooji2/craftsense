package com.dooji.craftsense.manager;

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
    private static final String ENABLE_KEY = "enabled";
    private boolean enabled;

    public ConfigurationManager() {
        loadConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
        saveConfig();
    }

    private void loadConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    Map<String, Boolean> config = GSON.fromJson(reader, HashMap.class);
                    this.enabled = config.getOrDefault(ENABLE_KEY, true);
                }
            } else {
                this.enabled = true;
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.enabled = true;
        }
    }

    private void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            Map<String, Boolean> config = new HashMap<>();
            config.put(ENABLE_KEY, enabled);
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}