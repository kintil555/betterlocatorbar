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
 * Geyser bridges Bedrock players onto Java servers. These players receive
 * a UUID in the form {@code 00000000-0000-0000-0009-xxxxxxxxxxxx} —
 * specifically, the most-significant 64 bits are always
 * {@code 0x0000000000000000} and the variant nibble is {@code 0x9}.
 * Floodgate (and GeyserMC) documents this as the "Bedrock XUID" scheme.
 *
 * @see <a href="https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/util/LoginEncryptionUtils.java">
 *     Geyser LoginEncryptionUtils</a>
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
     *
     * <p>Geyser derives the Java UUID from the player's Bedrock XUID (a
     * 64-bit integer) and encodes it as:
     * {@code 00000000-0000-0000-0009-<xuid-as-12-hex-digits>}.
     * The top 64 bits (MSB) are always zero; the bottom 64 bits (LSB)
     * start with the byte {@code 0x00 0x09}.</p>
     */
    public static boolean isBedrockPlayer(PlayerListEntry entry) {
        if (entry.getProfile() == null || entry.getProfile().id() == null) return false;
        UUID uuid = entry.getProfile().id();
        // MSB == 0 is the primary signal (no Mojang Java account has MSB == 0)
        if (uuid.getMostSignificantBits() != 0L) return false;
        // Additional check: LSB high byte pair must be 0x0009
        long lsb = uuid.getLeastSignificantBits();
        long high16 = (lsb >>> 48) & 0xFFFFL;
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

        // Bedrock players are real — always pass them through
        if (isBedrockPlayer(uuid)) return true;

        // UUID version 2 is never used for real accounts
        int version = (int) ((uuid.getMostSignificantBits() >> 12) & 0xF);
        if (version == 2) return false;

        // Purely numeric names are NPC IDs
        if (name.matches("[0-9_]+")) return false;

        return true;
    }

    private static boolean isBedrockPlayer(UUID uuid) {
        if (uuid.getMostSignificantBits() != 0L) return false;
        long high16 = (uuid.getLeastSignificantBits() >>> 48) & 0xFFFFL;
        return high16 == 0x0009L;
    }

    // ─── Head rendering ───────────────────────────────────────────────────────

    /**
     * Draw a player's head icon at (x, y) with the given pixel size.
     *
     * @param context  DrawContext from HUD render
     * @param entry    PlayerListEntry for the target player
     * @param x        left X position
     * @param y        top Y position
     * @param size     rendered pixel size (square)
     * @param alpha    0.0–1.0 opacity (< 1.0 tints Bedrock players)
     */
    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;

        SkinTextures skin = entry.getSkinTextures();
        Identifier skinId = skin.texture();

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderAlpha  = (int) (alpha * 255f) << 24;
            int borderColor  = (cfg.headBorderColor & 0x00FFFFFF) | borderAlpha;
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        // Draw face base layer (inner head)
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE,
                64, 64, alpha);

        // Draw hat overlay layer (outer head)
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE,
                64, 64, alpha);
    }

    /**
     * Draws a region of a skin texture scaled into the destination rectangle.
     * Uses {@link DrawContext#drawTexture} which is stable across 1.21.x.
     */
    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int textureW, int textureH,
                                        float alpha) {
        int argbAlpha = (int) (alpha * 255f) << 24 | 0x00FFFFFF;
        // drawTexture(id, x, y, z, u, v, width, height, texW, texH)
        // We scale manually by using the overload that accepts source dimensions.
        context.drawTexture(
                net.minecraft.client.render.RenderLayer::getGuiTextured,
                texture,
                dstX, dstY,
                (float) srcX, (float) srcY,
                dstW, dstH,
                textureW, textureH
        );
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

        String name   = entry.getProfile().name();
        String display = badge.isEmpty() ? name : badge + " " + name;
        int nameWidth  = mc.textRenderer.getWidth(display);
        int nameX      = centerX - nameWidth / 2;
        context.drawTextWithShadow(mc.textRenderer, display, nameX, bottomY + 2, 0xC0FFFFFF);
    }

    /** Legacy overload without badge, kept for compatibility with PlayerTrackerScreen. */
    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY) {
        drawPlayerName(context, entry, centerX, bottomY, "");
    }
}
