package com.dooji.craftsense.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapedRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {
    @Accessor("result")
    ItemStack getResult();
}