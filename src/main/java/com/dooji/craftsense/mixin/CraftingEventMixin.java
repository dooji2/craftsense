package com.dooji.craftsense.mixin;

import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class CraftingEventMixin {

    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void onCraft(ItemStack stack, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && (serverPlayer.containerMenu instanceof CraftingMenu || serverPlayer.containerMenu instanceof InventoryMenu)) {
            RecordCraftPayload payload = new RecordCraftPayload(stack.copy());
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }
}