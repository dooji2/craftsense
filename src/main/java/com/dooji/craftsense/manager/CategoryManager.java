package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.Item;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryManager {
    private static final Map<String, List<String>> categoryMap = new HashMap<>();
    private static final String MOD_ID = "craftsense";
    private static final String FILE_NAME = "categories.json";

    static {
        loadCategories();
    }

    private static void loadCategories() {
        try {
            Identifier resourceId = Identifier.of(MOD_ID, FILE_NAME);
            ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
            Resource resource = resourceManager.getResource(resourceId)
                    .orElseThrow(() -> new RuntimeException("Resource not found: " + resourceId));

            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            categoryMap.putAll(new Gson().fromJson(new InputStreamReader(resource.getInputStream()), type));
        } catch (Exception e) {
            System.err.println("Failed to load categories.json: " + e.getMessage());
        }
    }

    public static String getCategory(Item item) {
        String itemName = getItemName(item);

        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            if (entry.getValue().contains(itemName)) {
                return entry.getKey();
            }
        }
        return "MISC";
    }

    private static String getItemName(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id != null ? id.getPath().toUpperCase() : "UNKNOWN";
    }
}