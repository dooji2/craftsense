package com.dooji.craftsense.network.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

public class RecipesRequestPayload implements CustomPayload {
    public static final CustomPayload.Id<RecipesRequestPayload> ID = new CustomPayload.Id<>(Identifier.of("craftsense", "request_all_recipes"));

    public static final PacketCodec<PacketByteBuf, RecipesRequestPayload> CODEC = new PacketCodec<>() {
        @Override
        public RecipesRequestPayload decode(PacketByteBuf buf) {
            return new RecipesRequestPayload();
        }

        @Override
        public void encode(PacketByteBuf buf, RecipesRequestPayload payload) {
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}