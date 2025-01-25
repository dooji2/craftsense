package com.dooji.craftsense.network;

import com.dooji.craftsense.CraftingPredictor;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.network.payloads.RecipesPayload;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.registry.RegistryKey;

import java.util.List;
import java.util.Map;

public class CraftSenseClientNetworking {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RecipesPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                List<RecipeEntry<?>> recipes = payload.getRecipes();
                Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey = payload.getRecipesByKey();

                CraftingPredictor predictor = CraftingPredictor.getInstance();
                predictor.setRecipes(recipes);
                predictor.setRecipesByKey(recipesByKey);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RecordCraftPayload.ID, (payload, context) -> {
            context.client().execute(() -> recordCraft(payload));
        });
    }

    private static void recordCraft(RecordCraftPayload payload) {
        CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
        String category = CategoryManager.getCategory(payload.itemStack().getItem());
        habitsConfig.recordCraft(category, payload.itemStack().getItem().getTranslationKey());
    }
}