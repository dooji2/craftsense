package com.dooji.craftsense.ui;

import com.dooji.craftsense.manager.CategoryHabitsTracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Comparator;

public class CraftSenseStatsListWidget extends AlwaysSelectedEntryListWidget<CraftSenseStatsListWidget.Entry> {
    public CraftSenseStatsListWidget(MinecraftClient client, int width, int height) {
        super(client, width, height - 91, 33, 22);

        CategoryHabitsTracker tracker = CategoryHabitsTracker.getInstance();
        for (Item item : Registries.ITEM) {
            int count = tracker.itemCraftCount.getOrDefault(item.getTranslationKey(), 0);
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

    public class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        private final Item item;
        private final int count;

        Entry(Item item, int count) {
            this.item = item;
            this.count = count;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            int textY = y + height / 2 - 9 / 2;
            int color = index % 2 == 0 ? -1 : 0xFFBABABA;
            String value = String.valueOf(count);
            int valueX = x + width - client.textRenderer.getWidth(value) - 4;

            context.drawGuiTexture(Identifier.ofVanilla("container/slot"), x, y, 0, 18, 18);
            context.drawItemWithoutEntity(item.getDefaultStack(), x + 1, y + 1);
            context.enableScissor(x + 24, y, valueX - 4, y + height);
            context.drawTextWithShadow(client.textRenderer, item.getName(), x + 24, textY, color);
            context.disableScissor();
            context.drawTextWithShadow(client.textRenderer, value, valueX, textY, color);
        }

        @Override
        public Text getNarration() {
            return Text.translatable("narrator.select", Text.empty().append(item.getName()).append(" " + count));
        }
    }
}