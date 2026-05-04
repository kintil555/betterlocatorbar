package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Renders a player's skin face (8×8 head region) in the locator bar,
 * replacing the vanilla coloured dot indicator.
 */
public class PlayerHeadRenderer {

    // UV coordinates of the face region in the 64×64 skin texture
    private static final int SKIN_FACE_U    = 8;
    private static final int SKIN_FACE_V    = 8;
    private static final int SKIN_FACE_SIZE = 8;
    // UV coordinates of the hat / overlay layer
    private static final int SKIN_HAT_U    = 40;
    private static final int SKIN_HAT_V    = 8;

    // ─── Bedrock / Geyser detection ───────────────────────────────────────────

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

    // ─── Head rendering ───────────────────────────────────────────────────────

    /**
     * Draw a player's head icon at (x, y) with the given pixel size.
     * Uses SkinTextures from net.minecraft.client.util (correct client-side package).
     * Accessor for the skin Identifier uses getSkin() which is the correct
     * method name in Yarn 1.21.11.
     */
    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;

        // getSkinTextures() is on PlayerListEntry — returns net.minecraft.client.util.SkinTextures
        // In Yarn 1.21.11+, the record accessor for the skin Identifier is getSkin()
        SkinTextures skinTextures = entry.getSkinTextures();
        Identifier skinId = skinTextures.texture();

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderAlpha = (int) (alpha * 255f) << 24;
            int borderColor = (cfg.headBorderColor & 0x00FFFFFF) | borderAlpha;
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);

        drawSkinRegion(context, skinId,
                x, y, size, size,
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
        int nameX      = centerX - nameWidth / 2;
        context.drawTextWithShadow(mc.textRenderer, display, nameX, bottomY + 2, 0xC0FFFFFF);
    }

    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY) {
        drawPlayerName(context, entry, centerX, bottomY, "");
    }
}
