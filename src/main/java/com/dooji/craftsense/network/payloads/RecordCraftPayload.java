package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RecordCraftPayload(ItemStack itemStack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecordCraftPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CraftSense.MOD_ID, "record_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordCraftPayload> CODEC = CustomPacketPayload.codec(RecordCraftPayload::write, RecordCraftPayload::read);

    public static RecordCraftPayload read(RegistryFriendlyByteBuf buf) {
        return new RecordCraftPayload(ItemStack.STREAM_CODEC.decode(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ItemStack.STREAM_CODEC.encode(buf, itemStack);
    }

    @Override
    public CustomPacketPayload.Type<RecordCraftPayload> type() {
        return ID;
    }
}