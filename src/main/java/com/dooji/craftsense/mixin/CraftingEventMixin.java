package com.dooji.craftsense.mixin;

import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class CraftingEventMixin {

    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void onCraft(ItemStack stack, Level world, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof CraftingMenu) {
            PacketDistributor.sendToPlayer(serverPlayer, new RecordCraftPayload(stack.copy()));
        }
    }
}
