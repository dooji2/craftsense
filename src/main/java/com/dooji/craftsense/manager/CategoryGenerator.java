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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CategoryGenerator {
    public static final String MOD_ID = "craftsense";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CATEGORIES_PATH = Path.of("config/CraftSense/categories.json");
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[_\\s]+");
    private static final double POSITION_THRESHOLD = 0.5;
    private static final List<String> UNCOUNTABLE = Arrays.asList("WOOL", "DIRT", "SAND", "WATER", "MILK", "LAVA", "FLESH", "ICE");
    private static final double DYNAMIC_BONUS = 0.1;

    public static void generateCategories() {
        List<ItemData> items = new ArrayList<>();

        for (Item item : Registries.ITEM) {
            Identifier itemId = Registries.ITEM.getId(item);
            String itemName = itemId.getPath().toUpperCase();

            if (itemName.equals("AIR")) continue;

            List<String> tokens;

            if (itemName.startsWith("MUSIC_DISC") || itemName.startsWith("DISC_")) {
                tokens = new ArrayList<>();
                tokens.add("MUSIC_DISC");
            } else {
                tokens = Arrays.stream(SPLIT_PATTERN.split(itemName))
                        .filter(s -> s.length() > 2)
                        .collect(Collectors.toList());
            }

            items.add(new ItemData(itemName, tokens));
        }

        Map<String, GlobalStat> globalStats = new HashMap<>();
        for (ItemData item : items) {
            item.generateNGrams();

            for (NGramCandidate ng : item.ngrams) {
                globalStats.computeIfAbsent(ng.phrase, k -> new GlobalStat()).add(ng.normalizedPosition);
            }
        }

        double maxCount = globalStats.values().stream().mapToDouble(gs -> gs.count).max().orElse(1);

        Map<String, String> itemCategoryMap = new HashMap<>();
        Map<String, List<String>> existingCategories = loadExistingCategories();
        List<String> knownCategories = existingCategories.keySet().stream().map(String::toUpperCase).collect(Collectors.toList());

        for (ItemData item : items) {
            NGramCandidate bestCandidate = null;
            double bestScore = -1;

            for (NGramCandidate ng : item.ngrams) {
                GlobalStat stat = globalStats.getOrDefault(ng.phrase, new GlobalStat());
                double globalAvg = stat.getAverage();
                double bonus = 0;
                String formattedCandidate = formatCategoryName(ng.phrase);

                if (knownCategories.contains(formattedCandidate.toUpperCase())) {
                    bonus += DYNAMIC_BONUS;
                }

                double frequencyFactor = (stat.count / maxCount) * 0.2;
                double candidateScore = globalAvg >= POSITION_THRESHOLD ? ng.normalizedPosition + bonus + frequencyFactor : ng.normalizedPosition * 0.5;

                if (candidateScore > bestScore) {
                    bestScore = candidateScore;
                    bestCandidate = ng;
                }
            }

            if (bestCandidate == null && !item.ngrams.isEmpty()) {
                bestCandidate = item.ngrams.get(item.ngrams.size() - 1);
            }

            if (bestCandidate != null) {
                itemCategoryMap.put(item.name, bestCandidate.phrase);
            }
        }

        Map<String, List<String>> categorizedItems = loadExistingCategories();
        for (Map.Entry<String, String> entry : itemCategoryMap.entrySet()) {
            String itemName = entry.getKey();
            String label = formatCategoryName(entry.getValue());
            categorizedItems.computeIfAbsent(label, k -> new ArrayList<>()).add(itemName);
        }

        saveCategoriesToFile(categorizedItems);
    }

    private static String formatCategoryName(String candidate) {
        String[] words = candidate.toLowerCase().split("[_\\s]+");
        if (words.length == 0) return "";

        for (int i = 0; i < words.length - 1; i++) {
            words[i] = capitalize(words[i]);
        }

        String last = words[words.length - 1];
        if (!UNCOUNTABLE.contains(last.toUpperCase())) {
            last = pluralize(last);
        }

        words[words.length - 1] = capitalize(last);
        return String.join(" ", words);
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

    private static String pluralize(String word) {
        if (word.endsWith("s")) return word;
        if (word.endsWith("oo")) return word + "s";
        if (word.endsWith("ch") || word.endsWith("sh") || word.endsWith("x") || word.endsWith("z")) return word + "es";
        if (word.endsWith("o")) {
            char before = word.charAt(word.length() - 2);
            if (!isVowel(before)) return word + "es";
            return word + "s";
        }

        if (word.endsWith("y") && word.length() > 1 && !isVowel(word.charAt(word.length() - 2))) return word.substring(0, word.length() - 1) + "ies";
        return word + "s";
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) != -1;
    }

    private static Map<String, List<String>> loadExistingCategories() {
        Map<String, List<String>> map = new HashMap<>();

        try {
            Files.createDirectories(CATEGORIES_PATH.getParent());
            if (Files.exists(CATEGORIES_PATH)) {
                try (FileReader reader = new FileReader(CATEGORIES_PATH.toFile())) {
                    Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
                    map = GSON.fromJson(reader, type);

                    if (map == null) {
                        map = new HashMap<>();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load existing categories", e);
        }

        return map;
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

    private static class ItemData {
        String name;
        List<String> tokens;
        List<NGramCandidate> ngrams;

        ItemData(String name, List<String> tokens) {
            this.name = name;
            this.tokens = tokens;
            this.ngrams = new ArrayList<>();
        }

        void generateNGrams() {
            int len = tokens.size();
            for (int n = 1; n <= 3; n++) {
                if (n > len) break;

                for (int i = 0; i <= len - n; i++) {
                    double norm = (i + ((n + 1) / 2.0)) / (double) len;
                    String phrase = String.join("_", tokens.subList(i, i + n));

                    ngrams.add(new NGramCandidate(phrase, norm));
                }
            }
        }
    }

    private static class NGramCandidate {
        String phrase;
        double normalizedPosition;

        NGramCandidate(String phrase, double normalizedPosition) {
            this.phrase = phrase;
            this.normalizedPosition = normalizedPosition;
        }
    }

    private static class GlobalStat {
        double sum = 0;
        int count = 0;

        void add(double value) {
            sum += value;
            count++;
        }

        double getAverage() {
            return count == 0 ? 0 : sum / count;
        }
    }
}