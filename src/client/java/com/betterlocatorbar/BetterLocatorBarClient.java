package com.betterlocatorbar;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.gui.PlayerTrackerScreen;
import com.betterlocatorbar.network.PlayerDataPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterLocatorBarClient implements ClientModInitializer {

    public static final String MOD_ID = "betterlocatorbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openTrackerKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BetterLocatorBar] Initializing...");

        BLBConfig.load();

        // Only register S2C receiver — C2S payload type is registered server-side
        PlayerDataPacket.registerS2C();

        KeyBinding.Category category = KeyBinding.Category.create(
                net.minecraft.util.Identifier.of("betterlocatorbar", "general")
        );
        openTrackerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.betterlocatorbar.open_tracker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openTrackerKey.wasPressed() && client.player != null) {
                client.setScreen(new PlayerTrackerScreen());
            }
        });

        // NOTE: Head rendering is handled entirely by LocatorBarRendererMixin.
        // No HudRenderCallback needed — that caused duplicate/wrong-position heads.

        LOGGER.info("[BetterLocatorBar] Initialized!");
    }

    public static KeyBinding getOpenTrackerKey() {
        return openTrackerKey;
    }
}
