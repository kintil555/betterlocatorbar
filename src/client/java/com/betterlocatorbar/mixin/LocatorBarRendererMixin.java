package com.betterlocatorbar.mixin;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.network.TrackerDataStore;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import com.betterlocatorbar.util.TrackedPlayerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * Mixin targeting LocatorBar to replace vanilla colored dots with player skin heads.
 *
 * Vanilla LocatorBar positions each dot based on the horizontal bearing (yaw delta)
 * from the local player's camera to each target, within a +-60deg (120deg total) FOV.
 * We replicate that exact logic so heads appear at the SAME X positions as the dots.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

    // Vanilla locator bar FOV: +-60 degrees horizontal
    private static final float BAR_FOV_DEGREES = 60.0f;
    // Vanilla bar width in pixels (same as XP bar)
    private static final int BAR_WIDTH = 182;
    // Vanilla bar Y: center of bar is screenH - 29
    private static final int BAR_Y_FROM_BOTTOM = 29;

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
        renderHeadBar(context, mc);
    }

    private static void renderHeadBar(DrawContext context, MinecraftClient mc) {
        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null) return;

        // Build UUID -> PlayerListEntry map for skin lookup
        Map<UUID, PlayerListEntry> entryMap = new HashMap<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile() != null && e.getProfile().id() != null) {
                entryMap.put(e.getProfile().id(), e);
            }
        }

        // Get tracked players WITH real coordinates from server
        List<TrackedPlayerData> trackedData = TrackerDataStore.getPlayers()
                .stream()
                .filter(p -> !p.uuid().equals(localPlayer.getUuid()) && p.isOnline())
                .toList();

        if (trackedData.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float headScale = BLBConfig.get().headScale;
        int headSize = Math.max(8, (int) (9 * headScale));

        int barStartX = (screenW - BAR_WIDTH) / 2;
        int barCenterY = screenH - BAR_Y_FROM_BOTTOM;
        int headY = barCenterY - headSize / 2;

        // Local player's camera yaw (Minecraft: south=0, west=90, north=180, east=-90)
        float cameraYaw = localPlayer.getYaw();

        for (TrackedPlayerData data : trackedData) {
            // Only same dimension
            String localDim = mc.world.getRegistryKey().getValue().toString();
            if (!data.dimension().equals(localDim)) continue;

            // Calculate horizontal bearing from local player to target
            double dx = data.x() - localPlayer.getX();
            double dz = data.z() - localPlayer.getZ();

            // Convert to Minecraft yaw convention: south(+Z)=0, west(-X)=90, north(-Z)=180, east(+X)=-90
            // atan2(dx, dz) gives angle from south (+Z) axis, clockwise = west
            float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));

            // Yaw delta: positive = target is to the right of where camera looks
            float yawDelta = MathHelper.wrapDegrees(targetYaw - cameraYaw);

            // Only render if within +-60 degrees FOV
            if (yawDelta < -BAR_FOV_DEGREES || yawDelta > BAR_FOV_DEGREES) continue;

            // Map yawDelta [-60..+60] to bar position [0..BAR_WIDTH]
            float normalizedPos = (yawDelta + BAR_FOV_DEGREES) / (BAR_FOV_DEGREES * 2.0f);
            int dotCenterX = barStartX + Math.round(normalizedPos * BAR_WIDTH);
            int iconX = Math.clamp(dotCenterX - headSize / 2, barStartX, barStartX + BAR_WIDTH - headSize);

            // Get PlayerListEntry for skin
            PlayerListEntry entry = entryMap.get(data.uuid());
            if (entry == null) continue;

            float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
            PlayerHeadRenderer.drawPlayerHead(context, entry, iconX, headY, headSize, alpha);

            if (BLBConfig.get().showNameTag) {
                String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                PlayerHeadRenderer.drawPlayerName(
                        context, entry, iconX + headSize / 2, headY + headSize, badge);
            }
        }
    }
}
