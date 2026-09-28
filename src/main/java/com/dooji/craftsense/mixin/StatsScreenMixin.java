package com.dooji.craftsense.mixin;

import com.dooji.craftsense.ui.CraftSenseStatsListWidget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen {
    @Unique
    private CraftSenseStatsListWidget craftSenseStats;

    protected StatsScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "initLists", at = @At("TAIL"))
    private void createCraftSenseStats(CallbackInfo ci) {
        craftSenseStats = new CraftSenseStatsListWidget(this.minecraft, this.width, this.height);
    }

    @Inject(method = "initButtons", at = @At("TAIL"))
    private void addCraftSenseTab(CallbackInfo ci) {
        int index = 0;
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button && button.getY() == this.height - 52 && button.getWidth() == 80) {
                button.setX(this.width / 2 - 150 + index * 75);
                button.setWidth(75);
                index++;
            }
        }

        Button craftSenseButton = Button.builder(Component.translatable("screen.craftsense.stats"), button -> ((StatsScreen) (Object) this).setActiveList(craftSenseStats)).bounds(this.width / 2 + 75, this.height - 52, 75, 20).build();
        craftSenseButton.active = !craftSenseStats.children().isEmpty();
        this.addRenderableWidget(craftSenseButton);
    }
}