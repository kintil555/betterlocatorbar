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

    // Bounce state per player UUID: current bounce offset in pixels (float for smooth)
    private static final Map<UUID, Float> bounceOffset = new HashMap<>();
    // Direction: +1 = moving toward peak, -1 = moving back to center
    private static final Map<UUID, Integer> bounceDir = new HashMap<>();
    // Which way to bounce: -1 = up (negative Y in screen), +1 = down
    private static final Map<UUID, Integer> bounceSign = new HashMap<>();

    // Bounce speed in pixels per tick, max offset = 2px (more visible)
    private static final float BOUNCE_SPEED = 0.15f;
    private static final float BOUNCE_MAX = 2.0f;

    // Last known position for players who are sneaking or invisible (frozen icon)
    private static final Map<UUID, double[]> frozenPos = new HashMap<>();

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
                if (e.getProfile() == null || e.getProfile().id() == null) continue;
                // NPC filter: tiru logika vanilla LocatorBar (hanya tampilkan real players)
                if (!PlayerHeadRenderer.shouldShowInLocatorBar(e)) continue;
                entryMap.put(e.getProfile().id(), e);
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

            float tickDelta = tickCounter.getTickProgress(false);
            float cameraYaw = localPlayer.getLerpedYaw(tickDelta);
            double localY = localPlayer.getY();
            String localDim = mc.world.getRegistryKey().getValue().toString();

            for (UUID uuid : candidates) {
                double targetX, targetZ, targetY;

                AbstractClientPlayerEntity liveEntity = liveEntities.get(uuid);

                // ── Source priority: live entity → server packet → skip ────────
                // Bug fix: vanilla client entity list drops players ~64–72 blocks away.
                // We prefer server-broadcast coords (TrackerDataStore) when available,
                // falling back to live entity only when server data is absent.
                // This mirrors how vanilla LocatorBar works — it uses server-side data,
                // not the local entity list, so range is unlimited.
                TrackedPlayerData serverData = serverMap.get(uuid);

                if (serverData != null && serverData.isOnline() && serverData.dimension().equals(localDim)) {
                    // Server data is authoritative — always use it (works at any distance)
                    targetX = serverData.x();
                    targetY = serverData.y();
                    targetZ = serverData.z();
                    // Sync live entity position for sneak detection if available
                    if (liveEntity != null) {
                        targetX = liveEntity.getX();
                        targetY = liveEntity.getY();
                        targetZ = liveEntity.getZ();
                    }
                } else if (liveEntity != null) {
                    // Fallback: no server data, use live entity
                    targetX = liveEntity.getX();
                    targetY = liveEntity.getY();
                    targetZ = liveEntity.getZ();
                } else {
                    continue; // no data at all
                }

                // ── Sneak / Invisible detection ───────────────────────────────
                boolean isGhost = false;
                if (liveEntity != null) {
                    isGhost = liveEntity.isSneaking() || liveEntity.isInvisible();
                }

                if (isGhost) {
                    // Use frozen position if we have one, else store current
                    double[] frozen = frozenPos.get(uuid);
                    if (frozen == null) {
                        frozenPos.put(uuid, new double[]{targetX, targetY, targetZ});
                        frozen = frozenPos.get(uuid);
                    }
                    targetX = frozen[0];
                    targetY = frozen[1];
                    targetZ = frozen[2];
                } else {
                    frozenPos.remove(uuid);
                }

                double dx = targetX - localPlayer.getX();
                double dz = targetZ - localPlayer.getZ();
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float yawDelta = MathHelper.wrapDegrees(targetYaw - cameraYaw);

                if (yawDelta < -BAR_FOV_DEGREES || yawDelta > BAR_FOV_DEGREES) continue;

                float normalized = (yawDelta + BAR_FOV_DEGREES) / (BAR_FOV_DEGREES * 2.0f);
                int dotCenterX = barStartX + Math.round(normalized * BAR_WIDTH);

                // ── Distance-based scaling ────────────────────────────────────
                // Vanilla dot scales from full size at close range to small at far range.
                // Distance where icon is full size (blocks)
                double dist2D = Math.sqrt(dx * dx + dz * dz);
                float MIN_SIZE = 3f;
                float MAX_SIZE = HEAD_SIZE;
                float FULL_SIZE_DIST  = 16.0f;   // full size within 16 blocks
                float MIN_SIZE_DIST   = 128.0f;  // smallest at 128+ blocks
                float scaleFactor;
                if (dist2D <= FULL_SIZE_DIST) {
                    scaleFactor = 1.0f;
                } else if (dist2D >= MIN_SIZE_DIST) {
                    scaleFactor = MIN_SIZE / MAX_SIZE;
                } else {
                    float t = (float)(dist2D - FULL_SIZE_DIST) / (MIN_SIZE_DIST - FULL_SIZE_DIST);
                    scaleFactor = 1.0f - t * (1.0f - MIN_SIZE / MAX_SIZE);
                }
                int iconSize = Math.max((int) MIN_SIZE, Math.round(MAX_SIZE * scaleFactor));
                int iconX = Math.clamp(dotCenterX - iconSize / 2, barStartX, barStartX + BAR_WIDTH - iconSize);

                // ── Vertical-based bounce (arrow indicator) ───────────────────
                // Bounce triggers when target's height differs from local player by >=30%
                // of the estimated vertical view frustum at that distance.
                // This avoids over-sensitivity from slight pitch differences on flat terrain.
                // Minimum 5 block absolute height difference required (noise filter).
                double dist2DForBounce = Math.sqrt(dx * dx + dz * dz);
                double dy = targetY - localY;
                // Approx vertical FOV half-tan: Minecraft default ~70 deg VFOV -> half=35 deg -> tan~0.70
                double vertFovHalfTan = 0.70;
                double verticalFraction = (dist2DForBounce > 1.0)
                        ? Math.abs(dy) / (dist2DForBounce * vertFovHalfTan)
                        : 0.0;

                int newSign;
                if (verticalFraction >= 0.30 && dy > 5.0) {
                    newSign = -1; // target significantly above -> bounce up
                } else if (verticalFraction >= 0.30 && dy < -5.0) {
                    newSign = 1;  // target significantly below -> bounce down
                } else {
                    newSign = 0;  // close enough in height -> no bounce
                }

                // Update bounce sign if changed
                int currentSign = bounceSign.getOrDefault(uuid, 0);
                if (newSign != currentSign) {
                    bounceSign.put(uuid, newSign);
                    bounceOffset.put(uuid, 0f);
                    bounceDir.put(uuid, 1);
                }

                // Apply bounce offset to Y (no bounce while ghost — icon is frozen)
                int pixelOffset = (!isGhost && newSign != 0)
                        ? Math.round(bounceOffset.getOrDefault(uuid, 0f) * newSign)
                        : 0;

                int headY = barCenterY - iconSize / 2 + pixelOffset;

                PlayerListEntry entry = entryMap.get(uuid);
                if (entry == null) continue;

                // ── Alpha ─────────────────────────────────────────────────────
                // Ghost (sneaking/invisible): 10% opacity — just a faint hint of position
                float alpha = isGhost ? 0.10f : (PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f);

                PlayerHeadRenderer.drawPlayerHead(drawContext, entry, iconX, headY, iconSize, alpha);

                if (BLBConfig.get().showNameTag && localPlayer.isSneaking()) {
                    String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                    PlayerHeadRenderer.drawPlayerName(
                            drawContext, entry, iconX + iconSize / 2, headY + iconSize, badge);
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
