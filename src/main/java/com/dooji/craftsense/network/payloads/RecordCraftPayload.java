package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record RecordCraftPayload(ItemStack itemStack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecordCraftPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CraftSense.MOD_ID, "record_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordCraftPayload> CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC,
            RecordCraftPayload::itemStack,
            RecordCraftPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}