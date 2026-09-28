package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CraftItemPayload(String recipeId, boolean isShiftPressed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CraftItemPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CraftSense.MOD_ID, "craft_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftItemPayload> CODEC = CustomPacketPayload.codec(CraftItemPayload::write, CraftItemPayload::read);

    public static CraftItemPayload read(RegistryFriendlyByteBuf buf) {
        return new CraftItemPayload(buf.readUtf(), buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(recipeId);
        buf.writeBoolean(isShiftPressed);
    }

    @Override
    public CustomPacketPayload.Type<CraftItemPayload> type() {
        return ID;
    }
}