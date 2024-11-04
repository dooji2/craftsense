package com.dooji.craftsense.network.payloads;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CraftItemPayload(String recipeId) implements CustomPayload {
    public static final CustomPayload.Id<CraftItemPayload> ID = new CustomPayload.Id<>(Identifier.of("craftsense", "craft_item"));

    public static final PacketCodec<RegistryByteBuf, CraftItemPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CraftItemPayload::recipeId,
            CraftItemPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}