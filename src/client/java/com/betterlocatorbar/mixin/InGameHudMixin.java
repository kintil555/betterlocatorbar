package com.betterlocatorbar.mixin;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Intercepts the vanilla locator bar rendering to replace colored dot icons
 * with player skin head icons.
 *
 * <p>The vanilla method {@code renderPlayerLocatorBar} in {@link InGameHud}
 * iterates through tracked players and draws a small colored circle for each.
 * We cancel that call and re-draw with our {@link PlayerHeadRenderer} instead.</p>
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow
    private MinecraftClient client;

    /**
     * Injected at the HEAD of the locator bar render method so we can optionally
     * cancel (and replace) the vanilla dots with skin heads.
     *
     * <p>Note: In 1.21.11 the method is {@code renderPlayerLocatorBar}.
     * Adjust the method descriptor if Yarn mappings differ.</p>
     */
    @Inject(
            method = "renderPlayerLocatorBar",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blb$replaceLocatorDots(DrawContext context, CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        if (client == null || client.world == null || client.player == null) return;

        // Cancel vanilla rendering — we draw ourselves
        ci.cancel();

        renderHeadLocatorBar(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Our replacement rendering logic
    // ─────────────────────────────────────────────────────────────────────────

    private void renderHeadLocatorBar(DrawContext context) {
        MinecraftClient mc = client;
        if (mc.getNetworkHandler() == null) return;

        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null) return;

        // Collect all tracked players (except self)
        List<PlayerListEntry> trackedPlayers = mc.getNetworkHandler()
                .getPlayerList()
                .stream()
                .filter(e -> e.getProfile() != null
                        && !e.getProfile().id().equals(localPlayer.getUuid()))
                .toList();

        if (trackedPlayers.isEmpty()) return;

        // Layout constants
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float headScale = BLBConfig.get().headScale;
        int headSize = Math.max(8, (int) (9 * headScale)); // min 8px, scaled
        int barY = screenHeight - 32 - headSize; // above XP bar

        int barWidth = Math.min(screenWidth - 40, trackedPlayers.size() * (headSize + 4));
        int startX = (screenWidth - barWidth) / 2;

        // Draw each player head
        for (int i = 0; i < trackedPlayers.size(); i++) {
            PlayerListEntry entry = trackedPlayers.get(i);

            // Horizontal position — evenly spaced, clamped to bar width
            int iconX;
            if (trackedPlayers.size() == 1) {
                iconX = startX + (barWidth / 2) - (headSize / 2);
            } else {
                iconX = startX + i * (barWidth / Math.max(1, trackedPlayers.size() - 1))
                        - (headSize / 2);
            }
            iconX = Math.clamp(iconX, 0, screenWidth - headSize);

            // Fade based on distance (future: pull real distance from server)
            float alpha = 1.0f;

            PlayerHeadRenderer.drawPlayerHead(context, entry, iconX, barY, headSize, alpha);

            if (BLBConfig.get().showNameTag) {
                PlayerHeadRenderer.drawPlayerName(context, entry,
                        iconX + headSize / 2, barY + headSize);
            }
        }
    }
}
