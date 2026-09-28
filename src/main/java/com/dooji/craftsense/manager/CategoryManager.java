package com.dooji.craftsense.manager;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Map;

public class CategoryManager {
    private static Map<String, List<String>> categoryMap = CategoryGenerator.loadExistingCategories();

    public static String getCategory(Item item) {
        String itemName = BuiltInRegistries.ITEM.getKey(item).toString();
        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            if (entry.getValue().contains(itemName)) {
                return entry.getKey();
            }
        }
        return "MISC";
    }
}