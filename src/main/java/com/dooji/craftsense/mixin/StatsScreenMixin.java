package com.dooji.craftsense.mixin;

import com.dooji.craftsense.ui.CraftSenseStatsListWidget;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen {
    @Shadow
    private ThreePartsLayoutWidget layout;

    @Unique
    private CraftSenseStatsListWidget craftSenseStats;

    protected StatsScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "createLists", at = @At("TAIL"))
    private void createCraftSenseStats(CallbackInfo ci) {
        craftSenseStats = new CraftSenseStatsListWidget(this.client, this.width, this.height);
    }

    @Inject(method = "createButtons", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ThreePartsLayoutWidget;forEachChild(Ljava/util/function/Consumer;)V"))
    private void addCraftSenseTab(CallbackInfo ci) {
        layout.forEachElement(widget -> {
            if (widget instanceof DirectionalLayoutWidget footer) {
                footer.forEachElement(child -> {
                    if (child instanceof DirectionalLayoutWidget tabs) {
                        tabs.forEachElement(tab -> {
                            if (tab instanceof ButtonWidget button) {
                                button.setWidth(75);
                            }
                        });

                        ButtonWidget craftSenseButton = ButtonWidget.builder(Text.translatable("screen.craftsense.stats"), button -> ((StatsScreen) (Object) this).selectStatList(craftSenseStats)).width(75).build();
                        craftSenseButton.active = !craftSenseStats.children().isEmpty();
                        tabs.add(craftSenseButton);
                    }
                });
            }
        });
    }
}