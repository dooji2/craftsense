package com.dooji.craftsense.manager;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.List;
import java.util.Map;

public class CategoryManager {
    private static Map<String, List<String>> categoryMap = CategoryGenerator.loadExistingCategories();

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