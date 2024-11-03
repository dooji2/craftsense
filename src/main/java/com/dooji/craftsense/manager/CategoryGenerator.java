package com.dooji.craftsense.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
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
        Map<String, List<String>> categorizedItems = new HashMap<>();

        for (Item item : Registries.ITEM) {
            Identifier itemId = Registries.ITEM.getId(item);
            String itemName = itemId.getPath().toUpperCase();

            List<String> keywords = Arrays.stream(SPLIT_PATTERN.split(itemName))
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.toList());

            for (String keyword : keywords) {
                categorizedItems.computeIfAbsent(keyword, k -> new ArrayList<>()).add(itemName);
            }
        }

        saveCategoriesToFile(categorizedItems);
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