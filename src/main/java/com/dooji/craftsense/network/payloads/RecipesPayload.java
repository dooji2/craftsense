package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

public record RecipesPayload(List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CraftSense.MOD_ID, "all_recipes_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, RecipeHolder.STREAM_CODEC),
            RecipesPayload::recipes,
            RecipesPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}