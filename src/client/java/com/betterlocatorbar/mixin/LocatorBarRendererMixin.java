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
 * Injects into BOTH renderBar AND renderAddons on LocatorBar to:
 * 1. Cancel vanilla dot rendering (renderBar)
 * 2. Cancel vanilla arrow rendering (renderAddons) — we draw our own heads instead
 *
 * Using require=0 on both so if method names change, mod degrades gracefully.
 * The actual head drawing is done via HudRenderCallback in BetterLocatorBarClient
 * which always fires regardless of mixin success.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.LocatorBar")
public class LocatorBarRendererMixin {

    @Inject(
            method = "renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$cancelVanillaBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (BLBConfig.get().showPlayerHeads) {
            ci.cancel(); // Cancel vanilla dot rendering
        }
    }

    @Inject(
            method = "renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void blb$cancelVanillaAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (BLBConfig.get().showPlayerHeads) {
            ci.cancel(); // Cancel vanilla arrow (up/down) rendering
        }
    }
}
