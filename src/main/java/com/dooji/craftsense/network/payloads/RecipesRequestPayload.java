package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RecipesRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipesRequestPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CraftSense.MOD_ID, "request_all_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipesRequestPayload> CODEC = StreamCodec.unit(new RecipesRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}