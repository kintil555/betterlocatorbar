package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Renders a player's skin face (8×8 head region) in the locator bar,
 * replacing the vanilla coloured dot indicator.
 *
 * <h3>Java vs Bedrock detection (Geyser / Floodgate)</h3>
 * Geyser bridges Bedrock players onto Java servers using UUIDs of the form
 * {@code 00000000-0000-0000-0009-xxxxxxxxxxxx}: MSB == 0, and the top 16
 * bits of the LSB equal {@code 0x0009}.
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

    /**
     * Returns {@code true} if this player appears to be a Bedrock player
     * connected via Geyser/Floodgate.
     */
    public static boolean isBedrockPlayer(PlayerListEntry entry) {
        if (entry.getProfile() == null || entry.getProfile().id() == null) return false;
        return isBedrockUuid(entry.getProfile().id());
    }

    private static boolean isBedrockUuid(UUID uuid) {
        if (uuid.getMostSignificantBits() != 0L) return false;
        long high16 = (uuid.getLeastSignificantBits() >>> 48) & 0xFFFFL;
        return high16 == 0x0009L;
    }

    /**
     * Returns {@code true} if this entry should appear in the locator bar.
     * Filters out NPC tab-list entries injected by Citizens, NPCLib, etc.
     */
    public static boolean shouldShowInLocatorBar(PlayerListEntry entry) {
        if (entry.getProfile() == null) return false;
        String name = entry.getProfile().name();
        if (name == null || name.isBlank()) return false;
        UUID uuid = entry.getProfile().id();
        if (uuid == null) return false;

        // Bedrock players are real — always pass through
        if (isBedrockUuid(uuid)) return true;

        // UUID version 2 is never used for real accounts
        int version = (int) ((uuid.getMostSignificantBits() >> 12) & 0xF);
        if (version == 2) return false;

        // Purely numeric names → NPC IDs
        if (name.matches("[0-9_]+")) return false;

        return true;
    }

    // ─── Head rendering ───────────────────────────────────────────────────────

    /**
     * Draw a player's head icon at (x, y) with the given pixel size.
     *
     * <p>Uses {@link DrawContext#drawTexturedQuad} which is the public API
     * in 1.21.11 for arbitrary-UV skin sampling.
     * Signature: (Identifier, x1, y1, x2, y2, u1, u2, v1, v2)</p>
     *
     * @param alpha 0.0–1.0 opacity; < 1.0 tints Bedrock players visually
     */
    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;

        // getSkinTextures() returns SkinTextures (net.minecraft.entity.player),
        // then .texture() gives the skin Identifier. Valid for Yarn 1.21.x+
        Identifier skinId = entry.getSkinTextures().texture();

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderAlpha = (int) (alpha * 255f) << 24;
            int borderColor = (cfg.headBorderColor & 0x00FFFFFF) | borderAlpha;
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        // Draw face base layer (inner 8×8 at u=8,v=8 in 64×64 texture)
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);

        // Draw hat overlay layer (outer 8×8 at u=40,v=8 in 64×64 texture)
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
    }

    /**
     * Draws a rectangular region of a skin texture into the destination rect.
     *
     * <p>Uses the public {@link DrawContext#drawTexturedQuad} overload
     * (Identifier, x1, y1, x2, y2, u1, u2, v1, v2) present in 1.21.11.</p>
     */
    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH) {
        float u1 = (float) srcX / texW;
        float u2 = (float) (srcX + srcW) / texW;
        float v1 = (float) srcY / texH;
        float v2 = (float) (srcY + srcH) / texH;

        // public void drawTexturedQuad(Identifier sprite,
        //     int x1, int y1, int x2, int y2,
        //     float u1, float u2, float v1, float v2)
        context.drawTexturedQuad(texture,
                dstX, dstY, dstX + dstW, dstY + dstH,
                u1, u2, v1, v2);
    }

    // ─── Name tag ─────────────────────────────────────────────────────────────

    /**
     * Draw player name below head icon, with an optional prefix badge.
     *
     * @param centerX  horizontal center for the name
     * @param bottomY  top of text area (just below head)
     * @param badge    prefix string (e.g. "§7[BE]") — empty = no badge
     */
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

    /** Legacy overload without badge — for PlayerTrackerScreen compatibility. */
    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY) {
        drawPlayerName(context, entry, centerX, bottomY, "");
    }
}
