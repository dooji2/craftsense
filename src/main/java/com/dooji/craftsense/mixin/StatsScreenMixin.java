package com.dooji.craftsense.mixin;

import com.dooji.craftsense.ui.CraftSenseStatsListWidget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen {
    @Shadow
    private HeaderAndFooterLayout layout;

    @Unique
    private CraftSenseStatsListWidget craftSenseStats;

    protected StatsScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "initLists", at = @At("TAIL"))
    private void createCraftSenseStats(CallbackInfo ci) {
        craftSenseStats = new CraftSenseStatsListWidget(this.minecraft, this.width, this.height);
    }

    @Inject(method = "initButtons", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;visitWidgets(Ljava/util/function/Consumer;)V"))
    private void addCraftSenseTab(CallbackInfo ci) {
        layout.visitChildren(widget -> {
            if (widget instanceof LinearLayout footer) {
                footer.visitChildren(child -> {
                    if (child instanceof LinearLayout tabs) {
                        tabs.visitChildren(tab -> {
                            if (tab instanceof Button button) {
                                button.setWidth(75);
                            }
                        });

                        Button craftSenseButton = Button.builder(Component.translatable("screen.craftsense.stats"), button -> ((StatsScreen) (Object) this).setActiveList(craftSenseStats)).width(75).build();
                        craftSenseButton.active = !craftSenseStats.children().isEmpty();
                        tabs.addChild(craftSenseButton);
                    }
                });
            }
        });
    }
}