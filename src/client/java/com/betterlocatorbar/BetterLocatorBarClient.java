package com.betterlocatorbar;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.gui.PlayerTrackerScreen;
import com.betterlocatorbar.network.PlayerDataPacket;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

        // HudRenderCallback: draw heads at correct XP bar position.
        // Acts as the primary render since mixin cancel may or may not hook
        // depending on exact Yarn method name at runtime.
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!BLBConfig.get().showPlayerHeads) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) return;
            if (mc.getNetworkHandler() == null) return;

            ClientPlayerEntity localPlayer = mc.player;

            List<PlayerListEntry> tracked = mc.getNetworkHandler()
                    .getPlayerList()
                    .stream()
                    .filter(e -> e.getProfile() != null
                            && !e.getProfile().id().equals(localPlayer.getUuid())
                            && PlayerHeadRenderer.shouldShowInLocatorBar(e))
                    .toList();

            if (tracked.isEmpty()) return;

            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();

            float headScale = BLBConfig.get().headScale;
            int headSize = Math.max(8, (int) (9 * headScale));

            // XP bar: 182px wide, centered, top at screenH-32, bottom at screenH-27.
            // Center Y = screenH-29. Place heads centered on bar vertically.
            int barCenterY = screenH - 29;
            int headY = barCenterY - headSize / 2;
            int barWidth = 182;
            int barStartX = (screenW - barWidth) / 2;
            int count = tracked.size();

            for (int i = 0; i < count; i++) {
                PlayerListEntry entry = tracked.get(i);

                int dotX = (count == 1)
                        ? barStartX + barWidth / 2
                        : barStartX + (i * barWidth) / (count - 1);
                int iconX = Math.clamp(dotX - headSize / 2, barStartX, barStartX + barWidth - headSize);

                float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
                PlayerHeadRenderer.drawPlayerHead(drawContext, entry, iconX, headY, headSize, alpha);

                if (BLBConfig.get().showNameTag) {
                    String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                    PlayerHeadRenderer.drawPlayerName(
                            drawContext, entry, iconX + headSize / 2, headY + headSize, badge);
                }
            }
        });

        LOGGER.info("[BetterLocatorBar] Initialized!");
    }

    public static KeyBinding getOpenTrackerKey() {
        return openTrackerKey;
    }
}
