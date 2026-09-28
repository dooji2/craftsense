package com.dooji.craftsense.network.payloads;

import net.minecraft.network.FriendlyByteBuf;

public record CraftItemPayload(String recipeId, boolean isShiftPressed) {

    public static CraftItemPayload read(FriendlyByteBuf buf) {
        return new CraftItemPayload(buf.readUtf(), buf.readBoolean());
    }

    public static void write(CraftItemPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(payload.recipeId());
        buf.writeBoolean(payload.isShiftPressed());
    }
}