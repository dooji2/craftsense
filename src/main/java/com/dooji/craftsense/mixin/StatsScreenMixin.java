package com.dooji.craftsense.mixin;

import com.dooji.craftsense.ui.CraftSenseStatsListWidget;
import com.dooji.craftsense.ui.CraftSenseStatsTab;

import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen {
    @Shadow
    private TabNavigationBar tabNavigationBar;

    @Unique
    private CraftSenseStatsListWidget craftSenseStats;

    @Unique
    private Tab craftSenseTab;

    protected StatsScreenMixin() {
        super(Component.empty());
    }

    @ModifyArg(method = "onStatsUpdated", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"), index = 0)
    private Tab[] addCraftSenseTab(Tab[] tabs) {
        craftSenseStats = new CraftSenseStatsListWidget(this.minecraft, this.width, this.height);
        Tab[] statsTabs = Arrays.copyOf(tabs, tabs.length + 1);
        craftSenseTab = new CraftSenseStatsTab(craftSenseStats);
        statsTabs[tabs.length] = craftSenseTab;
        return statsTabs;
    }

    @Inject(method = "onStatsUpdated", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar;selectTab(IZ)V", shift = At.Shift.AFTER))
    private void setCraftSenseTabActive(CallbackInfo ci) {
        tabNavigationBar.setTabActiveState(tabNavigationBar.getTabs().indexOf(craftSenseTab), !craftSenseStats.children().isEmpty());
    }
}