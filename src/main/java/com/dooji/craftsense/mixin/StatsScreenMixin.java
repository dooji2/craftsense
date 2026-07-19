package com.dooji.craftsense.mixin;

import com.dooji.craftsense.ui.CraftSenseStatsListWidget;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
        super(Text.empty());
    }

    @Inject(method = "createLists", at = @At("TAIL"))
    private void createCraftSenseStats(CallbackInfo ci) {
        craftSenseStats = new CraftSenseStatsListWidget(this.client, this.width, this.height);
    }

    @Inject(method = "createButtons", at = @At("TAIL"))
    private void addCraftSenseTab(CallbackInfo ci) {
        int index = 0;
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget button && button.getY() == this.height - 52 && button.getWidth() == 80) {
                button.setX(this.width / 2 - 150 + index * 75);
                button.setWidth(75);
                index++;
            }
        }

        ButtonWidget craftSenseButton = ButtonWidget.builder(Text.translatable("screen.craftsense.stats"), button -> ((StatsScreen) (Object) this).selectStatList(craftSenseStats)).dimensions(this.width / 2 + 75, this.height - 52, 75, 20).build();
        craftSenseButton.active = !craftSenseStats.children().isEmpty();
        this.addDrawableChild(craftSenseButton);
    }
}