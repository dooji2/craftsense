package com.dooji.craftsense;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class CraftSenseKeyBindings {
    public static KeyMapping toggleKey;
    public static KeyMapping openStatsKey;
    public static KeyMapping quickCraftKey;

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        toggleKey = new KeyMapping("key.craftsense.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.craftsense");
        openStatsKey = new KeyMapping("key.craftsense.open_stats", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.craftsense");
        quickCraftKey = new KeyMapping("key.craftsense.quick_craft", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.craftsense");
        event.register(toggleKey);
        event.register(openStatsKey);
        event.register(quickCraftKey);
    }
}
