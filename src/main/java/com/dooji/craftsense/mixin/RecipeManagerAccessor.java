package com.dooji.craftsense.mixin;

import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.ServerRecipeManager.ServerRecipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.recipe.Recipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ServerRecipeManager.class)
public interface RecipeManagerAccessor {
    
    @Accessor("recipesByKey")
    Map<RegistryKey<Recipe<?>>, List<ServerRecipe>> getRecipesByKey();
}