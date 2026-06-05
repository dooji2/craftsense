package com.dooji.craftsense.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CraftItemPayload(String recipeId, boolean isShiftPressed) implements CustomPacketPayload {
    public static final Type<CraftItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftsense", "craft_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CraftItemPayload::recipeId,
            ByteBufCodecs.BOOL, CraftItemPayload::isShiftPressed,
            CraftItemPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
