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
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterLocatorBarClient implements ClientModInitializer {

    public static final String MOD_ID = "betterlocatorbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Keybind to open the Player Tracker GUI (default: B) */
    private static KeyBinding openTrackerKey;
    private static final KeyBinding.Category TRACKER_CATEGORY =
            KeyBinding.Category.create(Identifier.of("betterlocatorbar", "general"));

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BetterLocatorBar] Initializing...");

        // Load config
        BLBConfig.load();

        // Register network packets (server → client)
        PlayerDataPacket.registerC2S();
        PlayerDataPacket.registerS2C();

        // Register keybinding for tracker GUI
        openTrackerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.betterlocatorbar.open_tracker",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                TRACKER_CATEGORY
        ));

        // Check keybind every tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openTrackerKey.wasPressed() && client.player != null) {
                client.setScreen(new PlayerTrackerScreen());
            }
        });

        LOGGER.info("[BetterLocatorBar] Initialized successfully!");
    }

    public static KeyBinding getOpenTrackerKey() {
        return openTrackerKey;
    }
}
