package com.dooji.craftsense.mixin;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.AbstractCraftingScreenHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractCraftingScreenHandler.class)
public interface CraftingScreenHandlerAccessor {
    @Accessor("craftingInventory")
    RecipeInputInventory getCraftingInventory();
}