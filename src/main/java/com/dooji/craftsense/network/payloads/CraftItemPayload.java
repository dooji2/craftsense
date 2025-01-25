package com.dooji.craftsense.network.payloads;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.network.PacketByteBuf;

public record CraftItemPayload(String recipeId, Boolean isShiftPressed) {

    public static CraftItemPayload read(PacketByteBuf buf) {
        return new CraftItemPayload(buf.readString(), buf.readBoolean());
    }

    public static void write(PacketByteBuf buf, CraftItemPayload payload) {
        buf.writeString(payload.recipeId());
        buf.writeBoolean(payload.isShiftPressed());
    }

    public static PacketByteBuf createPacket(String recipeId, Boolean isShiftPressed) {
        PacketByteBuf buf = PacketByteBufs.create();
        write(buf, new CraftItemPayload(recipeId, isShiftPressed));
        return buf;
    }
}