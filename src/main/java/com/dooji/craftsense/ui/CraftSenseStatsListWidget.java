package com.dooji.craftsense.ui;

import com.dooji.craftsense.manager.CategoryHabitsTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        List<Entry> entries = new ArrayList<>(this.children());
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.count).reversed());
        this.replaceEntries(entries);
    }

    @Override
    public int getRowWidth() {
        return 280;
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor context) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor context) {
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Item item;
        private final int count;

        Entry(Item item, int count) {
            this.item = item;
            this.count = count;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            return false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();
            int textY = getContentYMiddle() - 9 / 2;
            int index = CraftSenseStatsListWidget.this.children().indexOf(this);
            int color = index % 2 == 0 ? -1 : 0xFFBABABA;
            String value = String.valueOf(count);
            int valueX = x + width - minecraft.font.width(value) - 3;

            context.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("container/slot"), getContentX(), getContentY(), 18, 18);
            context.fakeItem(item.getDefaultInstance(), getContentX() + 1, getContentY() + 1);
            context.enableScissor(x + 24, y, valueX - 4, y + height);
            context.text(minecraft.font, item.getName(item.getDefaultInstance()), x + 24, textY, color);
            context.disableScissor();
            context.text(minecraft.font, value, valueX, textY, color);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", Component.empty().append(item.getName(item.getDefaultInstance())).append(" " + count));
        }
    }
}