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
 * Mixin target: net.minecraft.client.gui.hud.bar.LocatorBar
 *
 * Strategy:
 * - Inject at HEAD of the render/renderBar method and cancel it entirely.
 *   Then draw our own head icons in the EXACT same position vanilla would use.
 *
 * The vanilla locator bar renders player dots along the XP bar (same Y, same X range).
 * XP bar: width=182, centered, bottom at screenH - 29 (above hotbar which ends at screenH).
 * XP bar top = screenH - 29 - 5 = screenH - 34.
 * Locator dot Y = screenH - 34 - 4 = screenH - 38 (dots sit 4px above XP bar top).
 *
 * We try multiple method name candidates to handle Yarn mapping variance.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

    /**
     * Primary injection — tries method name "render" first.
     * If this method doesn't exist, Mixin will warn but not crash (require=0).
     */
    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$cancelAndReplaceRender(DrawContext context,
                                              RenderTickCounter tickCounter,
                                              CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        ci.cancel();
        renderHeadBar(context, mc);
    }

    /**
     * Fallback — tries "renderBar" in case Yarn names it differently.
     */
    @Inject(
            method = "renderBar",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$cancelAndReplaceRenderBar(DrawContext context,
                                                 RenderTickCounter tickCounter,
                                                 CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        ci.cancel();
        renderHeadBar(context, mc);
    }

    /**
     * Fallback 2 — single-arg render variant.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$cancelAndReplaceRenderSingle(DrawContext context,
                                                    CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        ci.cancel();
        renderHeadBar(context, mc);
    }

    // ─── Head bar rendering ───────────────────────────────────────────────────

    private static void renderHeadBar(DrawContext context, MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null) return;

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

        // Vanilla XP bar: 182px wide, centered, top at screenH - 32, bottom at screenH - 27.
        // Locator dots appear INSIDE the XP bar area (same horizontal span, vertically centered).
        // We place heads so their bottom aligns with the XP bar top: barY = screenH - 32 - headSize.
        // But to overlay correctly ON the bar (like dots), put center at screenH - 29 (bar center).
        int barCenterY = screenH - 29;  // vertical center of XP bar
        int headY = barCenterY - headSize / 2;  // center head vertically on bar

        // Horizontal: 182px wide XP bar, centered
        int barWidth = 182;
        int barStartX = (screenW - barWidth) / 2;

        int count = tracked.size();

        for (int i = 0; i < count; i++) {
            PlayerListEntry entry = tracked.get(i);

            // Map player index to a position within the 182px bar
            // (same linear mapping vanilla uses for dots)
            int dotX;
            if (count == 1) {
                dotX = barStartX + barWidth / 2;
            } else {
                dotX = barStartX + (i * barWidth) / (count - 1);
            }
            int iconX = dotX - headSize / 2;
            iconX = Math.clamp(iconX, barStartX, barStartX + barWidth - headSize);

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
