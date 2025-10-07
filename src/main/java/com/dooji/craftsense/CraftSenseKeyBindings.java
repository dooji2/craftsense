package com.dooji.craftsense;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import org.lwjgl.glfw.GLFW;

public class CraftSenseKeyBindings {
    public static final KeyBinding.Category craftsenseCategory = KeyBinding.Category.create(Identifier.of("category.craftsense"));

    public static KeyBinding toggleKey;
    public static KeyBinding openStatsKey;
    public static KeyBinding quickCraftKey;

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craftsense.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                craftsenseCategory
        ));

        openStatsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craftsense.open_stats",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                craftsenseCategory
        ));

        quickCraftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.craftsense.quick_craft",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                craftsenseCategory
        ));
    }
}