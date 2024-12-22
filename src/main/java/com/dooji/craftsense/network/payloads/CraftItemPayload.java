package com.dooji.craftsense.network.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

public class CraftItemPayload implements CustomPayload {
    public static final CustomPayload.Id<CraftItemPayload> ID = new CustomPayload.Id<>(Identifier.of("craftsense", "craft_item"));
    public static final PacketCodec<PacketByteBuf, CraftItemPayload> CODEC = new PacketCodec<PacketByteBuf, CraftItemPayload>() {
        @Override
        public CraftItemPayload decode(PacketByteBuf buf) {
            return new CraftItemPayload(buf.readString());
        }
    
        @Override
        public void encode(PacketByteBuf buf, CraftItemPayload payload) {
            buf.writeString(payload.getRecipeId());
        }
    };    

    private final String recipeId;

    public CraftItemPayload(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}