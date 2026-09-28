package com.dooji.craftsense.network.payloads;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;

public record RecordCraftPayload(ItemStack itemStack) {

    public static RecordCraftPayload read(FriendlyByteBuf buf) {
        return new RecordCraftPayload(buf.readItem());
    }

    public static void write(RecordCraftPayload payload, FriendlyByteBuf buf) {
        buf.writeItem(payload.itemStack());
    }
}