package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Renders a player's skin face in the locator bar.
 * Uses SkinTextures API (net.minecraft.client.util.SkinTextures) to resolve
 * the player's actual skin texture from their PlayerListEntry.
 */
public class PlayerHeadRenderer {

    private static final int SKIN_FACE_U    = 8;
    private static final int SKIN_FACE_V    = 8;
    private static final int SKIN_FACE_SIZE = 8;
    private static final int SKIN_HAT_U     = 40;
    private static final int SKIN_HAT_V     = 8;

    private static final Identifier DEFAULT_SKIN =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");

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

    private static Identifier resolveSkinId(PlayerListEntry entry) {
        SkinTextures skinTextures = entry.getSkinTextures();
        if (skinTextures == null) return DEFAULT_SKIN;
        var body = skinTextures.body();
        if (body == null) return DEFAULT_SKIN;
        Identifier texture = body.texturePath();
        return texture != null ? texture : DEFAULT_SKIN;
    }

    // ─── Head rendering ───────────────────────────────────────────────────────

    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;
        Identifier skinId = resolveSkinId(entry);

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderAlpha = (int) (alpha * 180f) << 24;
            int borderColor = (cfg.headBorderColor & 0x00FFFFFF) | borderAlpha;
            context.fill(x,     y - 1, x + size, y,          borderColor); // top
            context.fill(x,     y + size, x + size, y + size + 1, borderColor); // bottom
            context.fill(x - 1, y,     x,          y + size, borderColor); // left
            context.fill(x + size, y,  x + size + 1, y + size, borderColor); // right
        }

        drawSkinRegion(context, skinId, x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64, alpha);
        drawSkinRegion(context, skinId, x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64, alpha);
    }

    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH, float alpha) {
        // Use normalized UV floats so the region is correctly stretched to dstW x dstH.
        // drawTexture(RenderPipeline, Identifier, x1, y1, x2, y2, u1, u2, v1, v2) — 1.21.11
        float u1 = (float) srcX / texW;
        float u2 = (float) (srcX + srcW) / texW;
        float v1 = (float) srcY / texH;
        float v2 = (float) (srcY + srcH) / texH;
        int alphaInt = Math.clamp((int)(alpha * 255f), 0, 255);
        int color = (alphaInt << 24) | 0x00FFFFFF;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                dstX, dstY, dstX + dstW, dstY + dstH,
                u1, u2, v1, v2, color);
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
