package com.betterlocatorbar.mixin;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.network.TrackerDataStore;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import com.betterlocatorbar.util.TrackedPlayerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

/**
 * Replaces vanilla LocatorBar colored dots with player skin heads.
 *
 * Key design decisions:
 * - Use getLerpedYaw(tickDelta) for smooth camera rotation (no flickering)
 * - For players in render distance: use real entity positions (smooth)
 * - For players outside render distance: use server TrackerDataStore coords
 * - Head size = exactly the vanilla dot size (5x5 px area) so arrows stay visible
 * - Center head on dot position precisely
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

    // Vanilla locator bar: 120 degree total FOV (+-60 from center)
    private static final float BAR_FOV_DEGREES = 60.0f;
    // Vanilla XP bar width
    private static final int BAR_WIDTH = 182;
    // Head size - small enough to not cover arrows, matching dot size
    // Vanilla dot is ~5px. We use 7px as minimum sensible head size.
    private static final int HEAD_SIZE = 7;

    @Inject(
            method = "renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$replaceLocatorDots(DrawContext context,
                                         RenderTickCounter tickCounter,
                                         CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        if (mc.getNetworkHandler() == null) return;

        ci.cancel();
        renderHeadBar(context, mc, tickCounter.getTickDelta(false));
    }

    private static void renderHeadBar(DrawContext context, MinecraftClient mc, float tickDelta) {
        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null) return;

        // Build UUID -> PlayerListEntry map for skins
        Map<UUID, PlayerListEntry> entryMap = new HashMap<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile() != null && e.getProfile().id() != null) {
                entryMap.put(e.getProfile().id(), e);
            }
        }

        // Build UUID -> live entity map for players in render distance
        Map<UUID, AbstractClientPlayerEntity> liveEntities = new HashMap<>();
        for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
            if (!entity.getUuid().equals(localPlayer.getUuid())) {
                liveEntities.put(entity.getUuid(), entity);
            }
        }

        // Combine: prefer live entity positions, fallback to server data
        List<TrackedPlayerData> serverData = TrackerDataStore.getPlayers();
        Map<UUID, TrackedPlayerData> serverMap = new HashMap<>();
        for (TrackedPlayerData d : serverData) {
            serverMap.put(d.uuid(), d);
        }

        // Collect all candidates (union of live + server, excluding self)
        Set<UUID> candidates = new HashSet<>();
        candidates.addAll(liveEntities.keySet());
        for (TrackedPlayerData d : serverData) {
            if (d.isOnline() && !d.uuid().equals(localPlayer.getUuid())) {
                candidates.add(d.uuid());
            }
        }

        if (candidates.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        int barStartX = (screenW - BAR_WIDTH) / 2;

        // Vanilla bar Y positioning:
        // XP bar texture top = screenH - 32, height = 5px
        // Bar center Y = screenH - 32 + 2 = screenH - 30
        // We center our head on this Y
        int barCenterY = screenH - 30;
        int headY = barCenterY - HEAD_SIZE / 2;

        // Use lerped yaw for smooth rotation without flickering
        float cameraYaw = localPlayer.getLerpedYaw(tickDelta);

        String localDim = mc.world.getRegistryKey().getValue().toString();

        for (UUID uuid : candidates) {
            double targetX, targetZ;

            AbstractClientPlayerEntity liveEntity = liveEntities.get(uuid);
            if (liveEntity != null) {
                // Use smooth lerped position from live entity
                targetX = liveEntity.getX();
                targetZ = liveEntity.getZ();
            } else {
                // Fallback to server data
                TrackedPlayerData data = serverMap.get(uuid);
                if (data == null) continue;
                if (!data.dimension().equals(localDim)) continue;
                targetX = data.x();
                targetZ = data.z();
            }

            // Calculate bearing: Minecraft yaw convention
            // south (+Z) = 0deg, west (-X) = 90deg, north (-Z) = 180deg, east (+X) = -90deg
            double dx = targetX - localPlayer.getX();
            double dz = targetZ - localPlayer.getZ();

            // atan2(dx, dz) gives: south=0, east=90, north=180/-180, west=-90
            // But MC yaw: south=0, west=90 => need negative sign on dx
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

            // How far is target from where camera looks (positive = right of center)
            float yawDelta = MathHelper.wrapDegrees(targetYaw - cameraYaw);

            // Skip if outside 120 degree FOV
            if (yawDelta < -BAR_FOV_DEGREES || yawDelta > BAR_FOV_DEGREES) continue;

            // Map [-60..+60] to [0..BAR_WIDTH]
            float normalized = (yawDelta + BAR_FOV_DEGREES) / (BAR_FOV_DEGREES * 2.0f);
            int dotCenterX = barStartX + Math.round(normalized * BAR_WIDTH);

            // Center head on dot, clamp to bar bounds
            int iconX = Math.clamp(dotCenterX - HEAD_SIZE / 2, barStartX, barStartX + BAR_WIDTH - HEAD_SIZE);

            PlayerListEntry entry = entryMap.get(uuid);
            if (entry == null) continue;

            float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
            PlayerHeadRenderer.drawPlayerHead(context, entry, iconX, headY, HEAD_SIZE, alpha);

            if (BLBConfig.get().showNameTag) {
                String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                PlayerHeadRenderer.drawPlayerName(
                        context, entry, iconX + HEAD_SIZE / 2, headY + HEAD_SIZE, badge);
            }
        }
    }
}
