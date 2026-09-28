package com.dooji.craftsense.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingMenu.class)
public interface CraftingScreenHandlerAccessor {
    
    @Accessor("craftSlots")
    CraftingContainer getInput();
}