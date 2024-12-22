package com.dooji.craftsense.network.payloads;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipesPayload implements CustomPayload {
    public static final Id<RecipesPayload> ID = new Id<>(Identifier.of("craftsense", "all_recipes_response"));

    private static final PacketCodec<RegistryByteBuf, List<RecipeEntry<?>>> RECIPE_LIST_CODEC =
            PacketCodecs.collection(ArrayList::new, RecipeEntry.PACKET_CODEC);

    private static final PacketCodec<RegistryByteBuf, List<ServerRecipeManager.ServerRecipe>> SERVER_RECIPE_LIST_CODEC =
            PacketCodecs.collection(ArrayList::new, new PacketCodec<>() {
                @Override
                public ServerRecipeManager.ServerRecipe decode(RegistryByteBuf buf) {
                    RecipeEntry<?> parent = RecipeEntry.PACKET_CODEC.decode(buf);
                    RecipeDisplayEntry display = RecipeDisplayEntry.PACKET_CODEC.decode(buf);

                    return new ServerRecipeManager.ServerRecipe(display, parent);
                }

                @Override
                public void encode(RegistryByteBuf buf, ServerRecipeManager.ServerRecipe recipe) {
                    RecipeEntry.PACKET_CODEC.encode(buf, recipe.parent());
                    RecipeDisplayEntry.PACKET_CODEC.encode(buf, recipe.display());
                }
            });

    private static final PacketCodec<RegistryByteBuf, Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>>> RECIPES_BY_KEY_CODEC =
            PacketCodecs.map(HashMap::new, RegistryKey.createPacketCodec(RegistryKeys.RECIPE), SERVER_RECIPE_LIST_CODEC);

    public static final PacketCodec<RegistryByteBuf, RecipesPayload> CODEC = new PacketCodec<>() {
        @Override
        public RecipesPayload decode(RegistryByteBuf buf) {
            List<RecipeEntry<?>> recipes = RECIPE_LIST_CODEC.decode(buf);
            Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey = RECIPES_BY_KEY_CODEC.decode(buf);

            return new RecipesPayload(recipes, recipesByKey);
        }

        @Override
        public void encode(RegistryByteBuf buf, RecipesPayload payload) {
            RECIPE_LIST_CODEC.encode(buf, payload.getRecipes());
            RECIPES_BY_KEY_CODEC.encode(buf, payload.getRecipesByKey());
        }
    };

    private final List<RecipeEntry<?>> recipes;
    private final Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey;

    public RecipesPayload(List<RecipeEntry<?>> recipes, Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> recipesByKey) {
        this.recipes = recipes;
        this.recipesByKey = recipesByKey;
    }

    public List<RecipeEntry<?>> getRecipes() {
        return recipes;
    }

    public Map<RegistryKey<Recipe<?>>, List<ServerRecipeManager.ServerRecipe>> getRecipesByKey() {
        return recipesByKey;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}