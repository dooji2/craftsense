package com.dooji.craftsense.mixin;

import com.dooji.craftsense.network.CraftSenseNetworking;
import com.dooji.craftsense.network.payloads.RecordCraftPayload;

import net.minecraftforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class CraftingEventMixin {

    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void onCraftedBy(ItemStack stack, Level world, Player player, CallbackInfo ci) {
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer && (serverPlayer.containerMenu instanceof CraftingMenu || serverPlayer.containerMenu instanceof InventoryMenu)) {
            CraftSenseNetworking.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecordCraftPayload(stack.copy()));
        }
    }
}