package com.dooji.craftsense.ui;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public class CraftSenseStatsTab extends GridLayoutTab {
    private final CraftSenseStatsListWidget widget;

    public CraftSenseStatsTab(CraftSenseStatsListWidget widget) {
        super(Component.translatable("screen.craftsense.stats"));
        this.widget = widget;
        this.layout.addChild(widget, 1, 1);
    }

    @Override
    public void doLayout(ScreenRectangle screenRect) {
        widget.updateSizeAndPosition(screenRect.width(), screenRect.height(), screenRect.top());
        super.doLayout(screenRect);
    }
}