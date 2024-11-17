package com.dooji.craftsense;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CraftSenseKeyBindings {
    public static KeyBinding toggleKey;
    public static KeyBinding openStatsKey;

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craftsense.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.craftsense"
        ));

        openStatsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftsense.open_stats",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "category.craftsense"
        ));
    }
}