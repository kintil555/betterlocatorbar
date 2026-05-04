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
 * Mixin targeting LocatorBar to replace vanilla colored dots with player skin heads.
 *
 * From Yarn 1.21.11 docs:
 * - Class: net.minecraft.client.gui.hud.bar.LocatorBar
 * - Method renderBar is DEFINED in interface Bar and IMPLEMENTED in LocatorBar.
 * - Mixin selector for renderBar: "renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"
 *
 * We inject into LocatorBar (the concrete class) at HEAD of renderBar and cancel it,
 * then draw our head icons instead.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

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
        ci.cancel();
        renderHeadBar(context, mc);
    }

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

        // From docs, LocatorBar renders at XP bar position.
        // XP bar: 182px wide, centered, bar center Y ≈ screenH - 29.
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
            PlayerHeadRenderer.drawPlayerHead(context, entry, iconX, headY, headSize, alpha);

            if (BLBConfig.get().showNameTag) {
                String badge = PlayerHeadRenderer.isBedrockPlayer(entry) ? "§7[BE]" : "";
                PlayerHeadRenderer.drawPlayerName(
                        context, entry, iconX + headSize / 2, headY + headSize, badge);
            }
        }
    }
}
