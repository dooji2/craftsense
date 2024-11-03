package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryManager {
    private static final Path CATEGORIES_PATH = Path.of("config/CraftSense/categories.json");
    private static Map<String, List<String>> categoryMap = new HashMap<>();

    static {
        loadCategories();
    }

    private static void loadCategories() {
        try {
            if (Files.exists(CATEGORIES_PATH)) {
                try (FileReader reader = new FileReader(CATEGORIES_PATH.toFile())) {
                    categoryMap = new Gson().fromJson(reader, new TypeToken<Map<String, List<String>>>() {}.getType());
                }
            } else {
                System.err.println("categories.json file not found; please restart the game.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getCategory(Item item) {
        String itemName = Registries.ITEM.getId(item).getPath().toUpperCase();
        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            if (entry.getValue().contains(itemName)) {
                return entry.getKey();
            }
        }
        return "MISC";
    }
}