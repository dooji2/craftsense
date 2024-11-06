package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
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
    private static final Path HABITS_PATH = Path.of("config/CraftSense/habits.json");

    private static CategoryHabitsTracker instance;

    public Map<String, Integer> categoryCraftCount;
    public Map<String, Integer> itemCraftCount;

    public CategoryHabitsTracker() {
        categoryCraftCount = new HashMap<>();
        itemCraftCount = new HashMap<>();
        load();
    }

    public static CategoryHabitsTracker getInstance() {
        if (instance == null) {
            instance = new CategoryHabitsTracker();
        }
        return instance;
    }

    private void load() {
        try {
            Files.createDirectories(HABITS_PATH.getParent());
            if (Files.exists(HABITS_PATH)) {
                try (FileReader reader = new FileReader(HABITS_PATH.toFile())) {
                    Map<String, Map<String, Integer>> data = GSON.fromJson(reader, new TypeToken<Map<String, Map<String, Integer>>>() {}.getType());

                    if (data != null && data.containsKey("categoryCraftCount") && data.containsKey("itemCraftCount")) {
                        categoryCraftCount = data.get("categoryCraftCount");
                        itemCraftCount = data.get("itemCraftCount");
                    } else {
                        categoryCraftCount = new HashMap<>();
                        itemCraftCount = new HashMap<>();
                    }
                } catch (JsonSyntaxException e) {
                    try (FileReader legacyReader = new FileReader(HABITS_PATH.toFile())) {
                        categoryCraftCount = GSON.fromJson(legacyReader, new TypeToken<Map<String, Integer>>() {}.getType());
                        itemCraftCount = new HashMap<>();
                    } catch (IOException innerException) {
                        innerException.printStackTrace();
                    }
                }
            } else {
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(HABITS_PATH.toFile())) {
            Map<String, Map<String, Integer>> data = new HashMap<>();
            data.put("categoryCraftCount", categoryCraftCount);
            data.put("itemCraftCount", itemCraftCount);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void recordCraft(String itemCategory, String itemName) {
        categoryCraftCount.put(itemCategory, categoryCraftCount.getOrDefault(itemCategory, 0) + 1);
        itemCraftCount.put(itemName, itemCraftCount.getOrDefault(itemName, 0) + 1);
        save();
    }

    public int getCraftCount(String category) {
        return categoryCraftCount.getOrDefault(category, 0);
    }

    public int getItemCraftCount(String itemName) {
        return itemCraftCount.getOrDefault(itemName, 0);
    }
}