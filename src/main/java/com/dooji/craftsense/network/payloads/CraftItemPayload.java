package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CraftItemPayload(String recipeId, boolean isShiftPressed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CraftItemPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CraftSense.MOD_ID, "craft_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CraftItemPayload::recipeId,
            ByteBufCodecs.BOOL,
            CraftItemPayload::isShiftPressed,
            CraftItemPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}