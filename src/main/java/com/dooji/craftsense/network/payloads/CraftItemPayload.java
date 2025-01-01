package com.dooji.craftsense.network.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

public record CraftItemPayload(String recipeId, boolean isShiftPressed) implements CustomPayload {
    public static final Id<CraftItemPayload> ID = new Id<>(Identifier.of("craftsense", "craft_item"));
    public static final PacketCodec<PacketByteBuf, CraftItemPayload> CODEC = new PacketCodec<>() {
        @Override
        public CraftItemPayload decode(PacketByteBuf buf) {
            return new CraftItemPayload(buf.readString(), buf.readBoolean());
        }

        @Override
        public void encode(PacketByteBuf buf, CraftItemPayload payload) {
            buf.writeString(payload.recipeId());
            buf.writeBoolean(payload.isShiftPressed());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}