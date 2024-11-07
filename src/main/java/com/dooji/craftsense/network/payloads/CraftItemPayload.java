package com.dooji.craftsense.network.payloads;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

public record CraftItemPayload(String recipeId) {

    public static CraftItemPayload read(PacketByteBuf buf) {
        return new CraftItemPayload(buf.readString());
    }

    public static void write(PacketByteBuf buf, CraftItemPayload payload) {
        buf.writeString(payload.recipeId());
    }

    public static PacketByteBuf createPacket(String recipeId) {
        PacketByteBuf buf = PacketByteBufs.create();
        write(buf, new CraftItemPayload(recipeId));
        return buf;
    }
}