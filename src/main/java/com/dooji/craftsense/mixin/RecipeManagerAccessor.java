package com.dooji.craftsense.mixin;

import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

    @Accessor("recipesById")
    Map<Identifier, RecipeEntry<?>> getRecipesById();
}