package com.dooji.craftsense.mixin;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractCraftingMenu.class)
public interface CraftingScreenHandlerAccessor {
    
    @Accessor("craftSlots")
    CraftingContainer getCraftingInventory();
}