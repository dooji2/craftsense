package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class CategoryHabitsTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config/CraftSense/habits.json");

    private Map<String, Integer> categoryCraftCounts;

    public CategoryHabitsTracker() {
        categoryCraftCounts = new HashMap<>();
        load();
    }

    private void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    categoryCraftCounts = GSON.fromJson(reader, new TypeToken<Map<String, Integer>>() {}.getType());
                }
            } else {
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(categoryCraftCounts, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void recordCraft(String itemCategory) {
        categoryCraftCounts.put(itemCategory, categoryCraftCounts.getOrDefault(itemCategory, 0) + 1);
        save();
    }

    public int getCraftCount(String category) {
        return categoryCraftCounts.getOrDefault(category, 0);
    }
}