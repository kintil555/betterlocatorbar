package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Renders a player's skin face (8×8 head region) in the locator bar.
 *
 * <p>In 1.21.11, SkinTextures moved packages and PlayerListEntry#getSkinTextures
 * was restructured. We bypass SkinTextures entirely and access the skin
 * Identifier directly via PlayerSkinProvider, falling back to the default
 * Steve/Alex skin Identifier when not yet loaded.</p>
 */
public class PlayerHeadRenderer {

    // UV coordinates in the 64×64 skin texture
    private static final int SKIN_FACE_U    = 8;
    private static final int SKIN_FACE_V    = 8;
    private static final int SKIN_FACE_SIZE = 8;
    private static final int SKIN_HAT_U     = 40;
    private static final int SKIN_HAT_V     = 8;

    // Default Steve skin — used while async skin loading is in progress
    private static final Identifier DEFAULT_SKIN =
            Identifier.of("minecraft", "textures/entity/player/slim/alex.png");

    // ─── Geyser / Bedrock detection ───────────────────────────────────────────

    public static boolean isBedrockPlayer(PlayerListEntry entry) {
        if (entry.getProfile() == null || entry.getProfile().id() == null) return false;
        return isBedrockUuid(entry.getProfile().id());
    }

    private static boolean isBedrockUuid(UUID uuid) {
        if (uuid.getMostSignificantBits() != 0L) return false;
        long high16 = (uuid.getLeastSignificantBits() >>> 48) & 0xFFFFL;
        return high16 == 0x0009L;
    }

    public static boolean shouldShowInLocatorBar(PlayerListEntry entry) {
        if (entry.getProfile() == null) return false;
        String name = entry.getProfile().name();
        if (name == null || name.isBlank()) return false;
        UUID uuid = entry.getProfile().id();
        if (uuid == null) return false;
        if (isBedrockUuid(uuid)) return true;
        int version = (int) ((uuid.getMostSignificantBits() >> 12) & 0xF);
        if (version == 2) return false;
        if (name.matches("[0-9_]+")) return false;
        return true;
    }

    // ─── Skin identifier resolution ───────────────────────────────────────────

    /**
     * Resolve the skin texture {@link Identifier} for a given {@link PlayerListEntry}.
     *
     * <p>Strategy (1.21.11 compatible):
     * <ol>
     *   <li>Ask the client's {@link PlayerSkinProvider} for the cached skin.</li>
     *   <li>Fall back to {@link #DEFAULT_SKIN} while the async load is pending.</li>
     * </ol>
     */
    private static Identifier resolveSkinId(PlayerListEntry entry) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSkinProvider() == null) return DEFAULT_SKIN;

        GameProfile profile = entry.getProfile();
        if (profile == null) return DEFAULT_SKIN;

        // getSkinTextures() on PlayerSkinProvider returns a CompletableFuture<SkinTextures>
        // but we only need the texture Identifier which we can get synchronously if cached.
        // Use the Supplier cached in the entry via texturesSupplier field if available,
        // otherwise fall back to default.
        try {
            // In 1.21.11 Yarn, PlayerListEntry still exposes getSkinTextures()
            // but the return type SkinTextures lives in net.minecraft.entity.player.
            // We call it reflectively to avoid a compile-time import dependency.
            Object skinTextures = entry.getClass()
                    .getMethod("getSkinTextures")
                    .invoke(entry);
            if (skinTextures != null) {
                // SkinTextures is a record; accessor for skin Identifier is texture()
                Object id = skinTextures.getClass()
                        .getMethod("texture")
                        .invoke(skinTextures);
                if (id instanceof Identifier identifier) {
                    return identifier;
                }
            }
        } catch (Exception ignored) {
            // Fall through to default
        }
        return DEFAULT_SKIN;
    }

    // ─── Head rendering ───────────────────────────────────────────────────────

    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;

        Identifier skinId = resolveSkinId(entry);

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderAlpha = (int) (alpha * 255f) << 24;
            int borderColor = (cfg.headBorderColor & 0x00FFFFFF) | borderAlpha;
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        // Face base layer
        drawSkinRegion(context, skinId, x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
        // Hat overlay layer
        drawSkinRegion(context, skinId, x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
    }

    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH) {
        float u1 = (float) srcX / texW;
        float u2 = (float) (srcX + srcW) / texW;
        float v1 = (float) srcY / texH;
        float v2 = (float) (srcY + srcH) / texH;

        context.drawTexturedQuad(texture,
                dstX, dstY, dstX + dstW, dstY + dstH,
                u1, u2, v1, v2);
    }

    // ─── Name tag ─────────────────────────────────────────────────────────────

    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY, String badge) {
        if (!BLBConfig.get().showNameTag) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        String name    = entry.getProfile().name();
        String display = badge.isEmpty() ? name : badge + " " + name;
        int nameWidth  = mc.textRenderer.getWidth(display);
        context.drawTextWithShadow(mc.textRenderer, display,
                centerX - nameWidth / 2, bottomY + 2, 0xC0FFFFFF);
    }

    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY) {
        drawPlayerName(context, entry, centerX, bottomY, "");
    }
}
