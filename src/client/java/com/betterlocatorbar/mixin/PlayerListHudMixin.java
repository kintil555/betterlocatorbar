package com.betterlocatorbar.mixin;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional mixin for the PlayerListHud — used to ensure skin textures
 * are preloaded when players join, improving head display quality
 * in the locator bar.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    /**
     * When the tab list is updated/rendered, ensure skin textures are
     * requested for all listed players so they appear correctly in
     * our custom locator bar rendering.
     *
     * <p>This is essentially a warm-up call; Minecraft caches skin
     * textures automatically after the first {@link PlayerListEntry#getSkinTextures()}
     * call, so this ensures the cache is populated.</p>
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void blb$prewarmSkins(CallbackInfo ci) {
        if (!BLBConfig.get().showPlayerHeads) return;
        // Skin texture loading is triggered lazily by PlayerListEntry.getSkinTextures()
        // The mixin on InGameHud already calls that, so this hook is a no-op
        // left here for future expansion (e.g. force-loading offline player skins).
    }
}
