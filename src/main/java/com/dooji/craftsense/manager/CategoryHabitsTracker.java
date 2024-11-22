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
                    Map<String, Object> data = GSON.fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());

                    if (data != null) {
                        Map<String, Number> rawCategoryCraftCount = (Map<String, Number>) data.getOrDefault("categoryCraftCount", new HashMap<>());
                        categoryCraftCount = new HashMap<>();
                        for (Map.Entry<String, Number> entry : rawCategoryCraftCount.entrySet()) {
                            categoryCraftCount.put(entry.getKey(), entry.getValue().intValue());
                        }

                        Map<String, Number> rawItemCraftCount = (Map<String, Number>) data.getOrDefault("itemCraftCount", new HashMap<>());
                        itemCraftCount = new HashMap<>();
                        for (Map.Entry<String, Number> entry : rawItemCraftCount.entrySet()) {
                            itemCraftCount.put(entry.getKey(), entry.getValue().intValue());
                        }

                        lastCraftedItem = (String) data.getOrDefault("lastCraftedItem", null);
                    }
                } catch (JsonSyntaxException | ClassCastException e) {
                    try (FileReader legacyReader = new FileReader(HABITS_PATH.toFile())) {
                        Map<String, Number> rawCategoryCraftCount = GSON.fromJson(legacyReader, new TypeToken<Map<String, Number>>() {}.getType());
                        categoryCraftCount = new HashMap<>();
                        for (Map.Entry<String, Number> entry : rawCategoryCraftCount.entrySet()) {
                            categoryCraftCount.put(entry.getKey(), entry.getValue().intValue());
                        }
                        itemCraftCount = new HashMap<>();
                        lastCraftedItem = null;
                    } catch (IOException innerException) {
                        innerException.printStackTrace();
                        categoryCraftCount = new HashMap<>();
                        itemCraftCount = new HashMap<>();
                        lastCraftedItem = null;
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
            Map<String, Object> data = new HashMap<>();
            data.put("categoryCraftCount", categoryCraftCount);
            data.put("itemCraftCount", itemCraftCount);
            data.put("lastCraftedItem", lastCraftedItem);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
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