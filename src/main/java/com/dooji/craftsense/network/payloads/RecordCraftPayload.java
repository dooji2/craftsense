package com.dooji.craftsense.network.payloads;

import com.dooji.craftsense.CraftSense;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RecordCraftPayload(ItemStack itemStack) implements CustomPayload {
    public static final CustomPayload.Id<RecordCraftPayload> ID = new CustomPayload.Id<>(Identifier.of(CraftSense.MOD_ID, "record_craft"));

    public static final PacketCodec<RegistryByteBuf, RecordCraftPayload> CODEC = PacketCodec.tuple(
            ItemStack.PACKET_CODEC,
            RecordCraftPayload::itemStack,
            RecordCraftPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}