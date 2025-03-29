package com.dooji.craftsense.mixin;

import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class CraftingEventMixin {
    @Inject(method = "onCraftByPlayer", at = @At("HEAD"))
    private void onCraft(ItemStack stack, PlayerEntity player, CallbackInfo ci) {
        World world = player.getWorld();
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && serverPlayer.currentScreenHandler instanceof CraftingScreenHandler) {
            RecordCraftPayload payload = new RecordCraftPayload(stack.copy());
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }
}