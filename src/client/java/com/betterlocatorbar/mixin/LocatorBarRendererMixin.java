package com.betterlocatorbar.mixin;

import com.betterlocatorbar.config.BLBConfig;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Intercepts {@link net.minecraft.client.gui.hud.bar.LocatorBar} rendering
 * to replace the vanilla coloured-dot icons with player skin heads.
 *
 * <p>In 1.21.11 (Yarn 1.21.11+build.1) the locator bar lives in
 * {@code net.minecraft.client.gui.hud.bar.LocatorBar} and the two render
 * entry-points are {@code renderBar} and {@code renderAddons}, both with
 * the signature {@code (DrawContext, RenderTickCounter) void}.</p>
 *
 * <p>We cancel {@code renderBar} (which draws the bar background + dots)
 * and replace it entirely with our head-icon rendering.
 * {@code renderAddons} (which draws the XP level number overlay) is left
 * untouched so vanilla addons still render correctly.</p>
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

    /**
     * Cancel vanilla dot rendering and draw player heads instead.
     *
     * <p>We inject at HEAD of {@code renderBar} and cancel so that the
     * default coloured-dot bar is never drawn.</p>
     */
    @Inject(
            method = "renderBar",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blb$replaceLocatorDots(DrawContext context,
                                         RenderTickCounter tickCounter,
                                         CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        // Cancel vanilla rendering — we draw ourselves
        ci.cancel();

        renderHeadBar(context, mc);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void renderHeadBar(DrawContext context, MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;

        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null) return;

        // Collect all tracked players (excluding self, NPCs, Bedrock bots)
        List<PlayerListEntry> tracked = mc.getNetworkHandler()
                .getPlayerList()
                .stream()
                .filter(e -> e.getProfile() != null
                        && !e.getProfile().id().equals(localPlayer.getUuid())
                        && PlayerHeadRenderer.shouldShowInLocatorBar(e))
                .toList();

        if (tracked.isEmpty()) return;

        // ── Layout ──────────────────────────────────────────────────────────
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float headScale = BLBConfig.get().headScale;
        // Vanilla locator bar sits just above the XP bar (height = 9px).
        // We match that vertical position: bar bottom edge = screenH - 49.
        int headSize  = Math.max(8, (int) (9 * headScale));
        int barBottom = screenH - 49;              // same Y as vanilla bar bottom
        int barY      = barBottom - headSize;       // top of head icons

        // Horizontal span: up to 182 px wide (matches vanilla XP bar width),
        // but shrinks if there are few players so heads don't crowd.
        int maxBarW  = Math.min(182, screenW - 20);
        int startX   = (screenW - maxBarW) / 2;
        int spacing  = tracked.size() > 1 ? maxBarW / (tracked.size() - 1) : 0;

        for (int i = 0; i < tracked.size(); i++) {
            PlayerListEntry entry = tracked.get(i);

            int iconX;
            if (tracked.size() == 1) {
                iconX = startX + maxBarW / 2 - headSize / 2;
            } else {
                iconX = startX + i * spacing - headSize / 2;
            }
            iconX = Math.clamp(iconX, startX, startX + maxBarW - headSize);

            // Bedrock players get a slight visual tint so you can tell them apart
            float alpha = PlayerHeadRenderer.isBedrockPlayer(entry) ? 0.65f : 1.0f;
            PlayerHeadRenderer.drawPlayerHead(context, entry, iconX, barY, headSize, alpha);

            if (BLBConfig.get().showNameTag) {
                // Draw platform badge (☐ = Bedrock, ☑ = Java) above the name
                String platformBadge = PlayerHeadRenderer.isBedrockPlayer(entry)
                        ? "§7[BE]" : "";
                PlayerHeadRenderer.drawPlayerName(
                        context, entry, iconX + headSize / 2, barY + headSize,
                        platformBadge);
            }
        }
    }
}
