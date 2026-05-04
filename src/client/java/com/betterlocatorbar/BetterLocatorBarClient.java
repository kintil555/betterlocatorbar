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

    private static final float BAR_FOV_DEGREES = 60.0f;
    private static final int BAR_WIDTH = 182;
    private static final int HEAD_SIZE = 7;

    // Threshold Y difference (blocks) before head starts bouncing
    private static final float BOUNCE_THRESHOLD_Y = 5.0f;

    // Bounce state per player UUID: current bounce offset in pixels (float for smooth)
    private static final Map<UUID, Float> bounceOffset = new HashMap<>();
    // Direction: +1 = moving toward peak, -1 = moving back to center
    private static final Map<UUID, Integer> bounceDir = new HashMap<>();
    // Which way to bounce: -1 = up (negative Y in screen), +1 = down
    private static final Map<UUID, Integer> bounceSign = new HashMap<>();

    // Bounce speed in pixels per tick, max offset = 1px
    private static final float BOUNCE_SPEED = 0.08f;
    private static final float BOUNCE_MAX = 1.0f;

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
            // Advance bounce animation each tick
            tickBounceAnimations(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!BLBConfig.get().showPlayerHeads) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) return;
            if (mc.getNetworkHandler() == null) return;
            if (mc.currentScreen != null) return;

            ClientPlayerEntity localPlayer = mc.player;

            Map<UUID, PlayerListEntry> entryMap = new HashMap<>();
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                if (e.getProfile() != null && e.getProfile().id() != null) {
                    entryMap.put(e.getProfile().id(), e);
                }
            }

            Map<UUID, AbstractClientPlayerEntity> liveEntities = new HashMap<>();
            for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
                if (!entity.getUuid().equals(localPlayer.getUuid())) {
                    liveEntities.put(entity.getUuid(), entity);
                }
            }

            Map<UUID, TrackedPlayerData> serverMap = new HashMap<>();
            for (TrackedPlayerData d : TrackerDataStore.getPlayers()) {
                serverMap.put(d.uuid(), d);
            }

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
            int barCenterY = screenH - 30;
            int baseHeadY = barCenterY - HEAD_SIZE / 2;

            float tickDelta = tickCounter.getTickProgress(false);
            float cameraYaw = localPlayer.getLerpedYaw(tickDelta);
            double localY = localPlayer.getY();
            String localDim = mc.world.getRegistryKey().getValue().toString();

            for (UUID uuid : candidates) {
                double targetX, targetZ, targetY;

                AbstractClientPlayerEntity liveEntity = liveEntities.get(uuid);
                if (liveEntity != null) {
                    targetX = liveEntity.getX();
                    targetY = liveEntity.getY();
                    targetZ = liveEntity.getZ();
                } else {
                    TrackedPlayerData data = serverMap.get(uuid);
                    if (data == null || !data.isOnline()) continue;
                    if (!data.dimension().equals(localDim)) continue;
                    targetX = data.x();
                    targetY = data.y();
                    targetZ = data.z();
                }

                double dx = targetX - localPlayer.getX();
                double dz = targetZ - localPlayer.getZ();
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float yawDelta = MathHelper.wrapDegrees(targetYaw - cameraYaw);

                if (yawDelta < -BAR_FOV_DEGREES || yawDelta > BAR_FOV_DEGREES) continue;

                float normalized = (yawDelta + BAR_FOV_DEGREES) / (BAR_FOV_DEGREES * 2.0f);
                int dotCenterX = barStartX + Math.round(normalized * BAR_WIDTH);
                int iconX = Math.clamp(dotCenterX - HEAD_SIZE / 2, barStartX, barStartX + BAR_WIDTH - HEAD_SIZE);

                // Determine bounce direction based on Y difference
                double yDiff = targetY - localY;
                int newSign;
                if (yDiff > BOUNCE_THRESHOLD_Y) {
                    newSign = -1; // target above → head bounces up (screen up = negative Y)
                } else if (yDiff < -BOUNCE_THRESHOLD_Y) {
                    newSign = 1;  // target below → head bounces down
                } else {
                    newSign = 0;  // same level → no bounce
                }

                // Update bounce sign if changed
                int currentSign = bounceSign.getOrDefault(uuid, 0);
                if (newSign != currentSign) {
                    bounceSign.put(uuid, newSign);
                    bounceOffset.put(uuid, 0f);
                    bounceDir.put(uuid, 1);
                }

                // Apply bounce offset to Y
                int pixelOffset = newSign != 0
                        ? Math.round(bounceOffset.getOrDefault(uuid, 0f) * newSign)
                        : 0;

                int headY = baseHeadY + pixelOffset;

                PlayerListEntry entry = entryMap.get(uuid);
                if (entry == null) continue;

                float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
                PlayerHeadRenderer.drawPlayerHead(drawContext, entry, iconX, headY, HEAD_SIZE, alpha);

                if (BLBConfig.get().showNameTag) {
                    // Name tag is shown only when the target player is sneaking/crouching.
                    // For server-tracked players (not in world), name stays hidden.
                    boolean targetSneaking = liveEntity != null && liveEntity.isSneaking();
                    if (targetSneaking) {
                        String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                        PlayerHeadRenderer.drawPlayerName(
                                drawContext, entry, iconX + HEAD_SIZE / 2, headY + HEAD_SIZE, badge);
                    }
                }
            }
        });

        LOGGER.info("[BetterLocatorBar] Initialized!");
    }

    private static void tickBounceAnimations(MinecraftClient client) {
        if (client.player == null) return;

        for (UUID uuid : new HashSet<>(bounceOffset.keySet())) {
            int sign = bounceSign.getOrDefault(uuid, 0);
            if (sign == 0) {
                // No bounce needed, reset to 0
                bounceOffset.put(uuid, 0f);
                continue;
            }

            float offset = bounceOffset.getOrDefault(uuid, 0f);
            int dir = bounceDir.getOrDefault(uuid, 1);

            // Move offset toward peak or back to 0
            offset += BOUNCE_SPEED * dir;

            if (offset >= BOUNCE_MAX) {
                offset = BOUNCE_MAX;
                dir = -1; // reverse: go back to 0
            } else if (offset <= 0f) {
                offset = 0f;
                dir = 1; // reverse: go to peak again
            }

            bounceOffset.put(uuid, offset);
            bounceDir.put(uuid, dir);
        }
    }

    public static KeyBinding getOpenTrackerKey() {
        return openTrackerKey;
    }
}
