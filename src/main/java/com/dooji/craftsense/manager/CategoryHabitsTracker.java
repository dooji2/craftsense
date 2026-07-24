package com.dooji.craftsense.manager;

import com.dooji.craftsense.CraftSense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    public String lastCraftedItem;

    public CategoryHabitsTracker() {
        categoryCraftCount = new HashMap<>();
        itemCraftCount = new HashMap<>();
        lastCraftedItem = null;
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
                    JsonObject data = GSON.fromJson(reader, JsonObject.class);

                    if (data != null) {
                        if (data.has("categoryCraftCount")) {
                            categoryCraftCount = readCounts(data.getAsJsonObject("categoryCraftCount"));
                            itemCraftCount = readCounts(data.getAsJsonObject("itemCraftCount"));
                            JsonElement lastItem = data.get("lastCraftedItem");
                            if (lastItem != null && !lastItem.isJsonNull()) lastCraftedItem = lastItem.getAsString();
                        } else {
                            categoryCraftCount = readCounts(data);
                        }
                    }
                }
            } else {
                save();
            }
        } catch (IOException e) {
            CraftSense.LOGGER.error("Failed to load crafting habits", e);
        }
    }

    private Map<String, Integer> readCounts(JsonObject counts) {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : counts.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsInt());
        }

        return result;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(HABITS_PATH.toFile())) {
            Map<String, Object> data = new HashMap<>();
            data.put("categoryCraftCount", categoryCraftCount);
            data.put("itemCraftCount", itemCraftCount);
            data.put("lastCraftedItem", lastCraftedItem);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            CraftSense.LOGGER.error("Failed to save crafting habits", e);
        }
    }

    public void recordCraft(String itemCategory, String itemName) {
        categoryCraftCount.put(itemCategory, categoryCraftCount.getOrDefault(itemCategory, 0) + 1);
        itemCraftCount.put(itemName, itemCraftCount.getOrDefault(itemName, 0) + 1);
        lastCraftedItem = itemName;
        save();
    }

    public int getCraftCount(String category) {
        return categoryCraftCount.getOrDefault(category, 0);
    }

    public int getItemCraftCount(String itemName) {
        return itemCraftCount.getOrDefault(itemName, 0);
    }

    public String getLastCraftedItem() {
        return lastCraftedItem;
    }
}