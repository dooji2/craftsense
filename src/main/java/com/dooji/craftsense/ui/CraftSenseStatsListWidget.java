package com.dooji.craftsense.ui;

import com.dooji.craftsense.manager.CategoryHabitsTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Comparator;

public class CraftSenseStatsListWidget extends ObjectSelectionList<CraftSenseStatsListWidget.Entry> {
    public CraftSenseStatsListWidget(Minecraft client, int width, int height) {
        super(client, width, height - 91, 33, 22);

        CategoryHabitsTracker tracker = CategoryHabitsTracker.getInstance();
        for (Item item : BuiltInRegistries.ITEM) {
            int count = tracker.itemCraftCount.getOrDefault(item.getDescriptionId(), 0);
            if (count > 0) {
                this.addEntry(new Entry(item, count));
            }
        }

        this.children().sort(Comparator.comparingInt((Entry entry) -> entry.count).reversed());
    }

    @Override
    public int getRowWidth() {
        return 280;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Item item;
        private final int count;

        Entry(Item item, int count) {
            this.item = item;
            this.count = count;
        }

        @Override
        public void render(GuiGraphics context, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            int textY = y + height / 2 - 9 / 2;
            int color = index % 2 == 0 ? -1 : 0xFFBABABA;
            String value = String.valueOf(count);
            int valueX = x + width - minecraft.font.width(value) - 4;

            context.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), x, y, 0, 18, 18);
            context.renderFakeItem(item.getDefaultInstance(), x + 1, y + 1);
            context.enableScissor(x + 24, y, valueX - 4, y + height);
            context.drawString(minecraft.font, item.getDescription(), x + 24, textY, color);
            context.disableScissor();
            context.drawString(minecraft.font, value, valueX, textY, color);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", Component.empty().append(item.getDescription()).append(" " + count));
        }
    }
}