package com.betterlocatorbar;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.gui.PlayerTrackerScreen;
import com.betterlocatorbar.network.PlayerDataPacket;
import com.betterlocatorbar.network.TrackerDataStore;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import com.betterlocatorbar.util.TrackedPlayerData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BetterLocatorBarClient implements ClientModInitializer {

    public static final String MOD_ID = "betterlocatorbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openTrackerKey;

    // Vanilla locator bar constants
    private static final float BAR_FOV_DEGREES = 60.0f;
    private static final int BAR_WIDTH = 182;
    private static final int HEAD_SIZE = 7;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BetterLocatorBar] Initializing...");

        BLBConfig.load();
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

        // Head rendering via HudRenderCallback — always fires, independent of mixin.
        // Mixin only cancels vanilla dot/arrow; this draws our heads on top.
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!BLBConfig.get().showPlayerHeads) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) return;
            if (mc.getNetworkHandler() == null) return;

            // Only render when locator bar would normally show (no screen open)
            if (mc.currentScreen != null) return;

            ClientPlayerEntity localPlayer = mc.player;

            // Build UUID -> PlayerListEntry map for skins
            Map<UUID, PlayerListEntry> entryMap = new HashMap<>();
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                if (e.getProfile() != null && e.getProfile().id() != null) {
                    entryMap.put(e.getProfile().id(), e);
                }
            }

            // Live entities in render distance (most accurate positions)
            Map<UUID, AbstractClientPlayerEntity> liveEntities = new HashMap<>();
            for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
                if (!entity.getUuid().equals(localPlayer.getUuid())) {
                    liveEntities.put(entity.getUuid(), entity);
                }
            }

            // Server data for players outside render distance
            Map<UUID, TrackedPlayerData> serverMap = new HashMap<>();
            for (TrackedPlayerData d : TrackerDataStore.getPlayers()) {
                serverMap.put(d.uuid(), d);
            }

            // Union of all candidates
            Set<UUID> candidates = new HashSet<>(liveEntities.keySet());
            for (TrackedPlayerData d : serverMap.values()) {
                if (d.isOnline() && !d.uuid().equals(localPlayer.getUuid())) {
                    candidates.add(d.uuid());
                }
            }

            if (candidates.isEmpty()) return;

            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();
            int barStartX = (screenW - BAR_WIDTH) / 2;

            // Bar.VERTICAL_OFFSET = 32, bar height = 5px
            // Bar center Y = screenH - 32 + 2 = screenH - 30
            int barCenterY = screenH - 30;
            int headY = barCenterY - HEAD_SIZE / 2;

            float tickDelta = tickCounter.getTickProgress(false);
            float cameraYaw = localPlayer.getLerpedYaw(tickDelta);
            String localDim = mc.world.getRegistryKey().getValue().toString();

            for (UUID uuid : candidates) {
                double targetX, targetZ;

                AbstractClientPlayerEntity liveEntity = liveEntities.get(uuid);
                if (liveEntity != null) {
                    targetX = liveEntity.getX();
                    targetZ = liveEntity.getZ();
                } else {
                    TrackedPlayerData data = serverMap.get(uuid);
                    if (data == null || !data.isOnline()) continue;
                    if (!data.dimension().equals(localDim)) continue;
                    targetX = data.x();
                    targetZ = data.z();
                }

                double dx = targetX - localPlayer.getX();
                double dz = targetZ - localPlayer.getZ();

                // Bearing: MC yaw convention south=0, west=90
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float yawDelta = MathHelper.wrapDegrees(targetYaw - cameraYaw);

                if (yawDelta < -BAR_FOV_DEGREES || yawDelta > BAR_FOV_DEGREES) continue;

                // Map [-60..+60] to bar X position
                float normalized = (yawDelta + BAR_FOV_DEGREES) / (BAR_FOV_DEGREES * 2.0f);
                int dotCenterX = barStartX + Math.round(normalized * BAR_WIDTH);
                int iconX = Math.clamp(dotCenterX - HEAD_SIZE / 2, barStartX, barStartX + BAR_WIDTH - HEAD_SIZE);

                PlayerListEntry entry = entryMap.get(uuid);
                if (entry == null) continue;

                float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
                PlayerHeadRenderer.drawPlayerHead(drawContext, entry, iconX, headY, HEAD_SIZE, alpha);

                if (BLBConfig.get().showNameTag) {
                    String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                    PlayerHeadRenderer.drawPlayerName(
                            drawContext, entry, iconX + HEAD_SIZE / 2, headY + HEAD_SIZE, badge);
                }
            }
        });

        LOGGER.info("[BetterLocatorBar] Initialized!");
    }

    public static KeyBinding getOpenTrackerKey() {
        return openTrackerKey;
    }
}
