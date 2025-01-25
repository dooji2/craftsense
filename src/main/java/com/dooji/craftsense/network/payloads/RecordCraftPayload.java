package com.dooji.craftsense.network.payloads;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

public record RecordCraftPayload(ItemStack itemStack) {

    public static RecordCraftPayload read(PacketByteBuf buf) {
        return new RecordCraftPayload(buf.readItemStack());
    }

    public static void write(PacketByteBuf buf, RecordCraftPayload payload) {
        buf.writeItemStack(payload.itemStack());
    }

    public static PacketByteBuf createPacket(ItemStack itemStack) {
        PacketByteBuf buf = PacketByteBufs.create();
        write(buf, new RecordCraftPayload(itemStack));
        return buf;
    }
}