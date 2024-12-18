package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CategoryGenerator {
    public static final String MOD_ID = "craftsense";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CATEGORIES_PATH = Path.of("config/CraftSense/categories.json");
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[_\\s]");

    public static void generateCategories() {
        Map<String, List<String>> categorizedItems = loadExistingCategories();

        Map<String, List<String>> specificCategories = new HashMap<>();
        specificCategories.put("TOOL", Arrays.asList("AXE", "PICKAXE", "SWORD", "SHOVEL"));
        specificCategories.put("ARMOR", Arrays.asList("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        specificCategories.put("SIGN", Collections.singletonList("SIGN"));
        specificCategories.put("STAIR", Collections.singletonList("STAIR"));
        specificCategories.put("DOOR", Collections.singletonList("DOOR"));
        specificCategories.put("FENCE", Arrays.asList("FENCE", "FENCE_GATE"));
        specificCategories.put("BUTTON", Collections.singletonList("BUTTON"));
        specificCategories.put("PRESSURE_PLATE", Collections.singletonList("PRESSURE_PLATE"));
        specificCategories.put("SLAB", Collections.singletonList("SLAB"));
        specificCategories.put("TRAPDOOR", Collections.singletonList("TRAPDOOR"));
        specificCategories.put("BOAT", Collections.singletonList("BOAT"));

        for (Item item : Registries.ITEM) {
            Identifier itemId = Registries.ITEM.getId(item);
            String itemName = itemId.getPath().toUpperCase();

            if (isItemCategorized(categorizedItems, itemName)) {
                continue;
            }

            List<String> keywords = Arrays.stream(SPLIT_PATTERN.split(itemName))
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.toList());

            boolean isInSpecificCategory = false;

            for (Map.Entry<String, List<String>> entry : specificCategories.entrySet()) {
                String category = entry.getKey();
                List<String> categoryKeywords = entry.getValue();

                if (keywords.stream().anyMatch(categoryKeywords::contains)) {
                    categorizedItems.computeIfAbsent(category, k -> new ArrayList<>()).add(itemName);
                    isInSpecificCategory = true;
                }
            }

            if (!isInSpecificCategory) {
                for (String keyword : keywords) {
                    categorizedItems.computeIfAbsent(keyword, k -> new ArrayList<>()).add(itemName);
                }
            }
        }

        saveCategoriesToFile(categorizedItems);
    }

    private static Map<String, List<String>> loadExistingCategories() {
        if (Files.exists(CATEGORIES_PATH)) {
            try (FileReader reader = new FileReader(CATEGORIES_PATH.toFile())) {
                Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
                return GSON.fromJson(reader, type);
            } catch (IOException e) {
                LOGGER.error("Failed to load existing categories", e);
            }
        }
        return new HashMap<>();
    }

    private static boolean isItemCategorized(Map<String, List<String>> categorizedItems, String itemName) {
        return categorizedItems.values().stream().anyMatch(list -> list.contains(itemName));
    }

    private static void saveCategoriesToFile(Map<String, List<String>> categorizedItems) {
        try {
            Files.createDirectories(CATEGORIES_PATH.getParent());
            try (FileWriter writer = new FileWriter(CATEGORIES_PATH.toFile())) {
                GSON.toJson(categorizedItems, writer);
                LOGGER.info("Categories successfully saved to {}", CATEGORIES_PATH);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save categories to file", e);
        }
    }
}