package com.dooji.craftsense.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RecordCraftPayload(ItemStack itemStack) implements CustomPacketPayload {
    public static final Type<RecordCraftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftsense", "record_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordCraftPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, RecordCraftPayload::itemStack,
            RecordCraftPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
