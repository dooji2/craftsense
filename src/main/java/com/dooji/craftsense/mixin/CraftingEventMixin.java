package com.dooji.craftsense.mixin;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.dooji.craftsense.manager.CategoryManager.getCategory;

@Mixin(Item.class)
public abstract class CraftingEventMixin {

    @Inject(method = "onCraft", at = @At("HEAD"))
    private void onCraft(ItemStack stack, World world, PlayerEntity player, CallbackInfo ci) {
        if (!world.isClient) {
            CategoryHabitsTracker habitsConfig = CategoryHabitsTracker.getInstance();
            String category = getCategory(stack.getItem());
            habitsConfig.recordCraft(category, stack.getItem().getTranslationKey());
        }
    }
}