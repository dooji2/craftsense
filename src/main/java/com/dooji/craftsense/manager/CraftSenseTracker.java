package com.dooji.craftsense.manager;

import net.minecraft.client.Minecraft;

public class CraftSenseTracker {
    private static boolean prioritizeCombatItems = false;

    public static void checkPlayerConditions() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (client.player.getHealth() < 10.0f) {
                triggerCombatCondition();
            } else if (client.player.getHealth() == client.player.getMaxHealth()) {
                prioritizeCombatItems = false;
            } else {
                prioritizeCombatItems = false;
            }
        }
    }

    private static void triggerCombatCondition() {
        prioritizeCombatItems = true;
    }

    public static boolean isPrioritizingCombatItems() {
        return prioritizeCombatItems;
    }
}