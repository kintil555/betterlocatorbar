package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Renders a player's skin face (8×8 head region) in the locator bar,
 * replacing the vanilla coloured dot indicator.
 */
public class PlayerHeadRenderer {

    // UV coordinates of the face region in the 64×64 skin texture
    private static final int SKIN_FACE_U = 8;
    private static final int SKIN_FACE_V = 8;
    private static final int SKIN_FACE_SIZE = 8;

    // UV coordinates of the hat/overlay layer
    private static final int SKIN_HAT_U = 40;
    private static final int SKIN_HAT_V = 8;

    /**
     * Draw a player's head icon at (x, y) with the given pixel size.
     *
     * @param context  DrawContext from HUD render
     * @param entry    PlayerListEntry for the target player
     * @param x        left X position
     * @param y        top Y position
     * @param size     rendered pixel size (square)
     * @param alpha    0.0–1.0 opacity
     */
    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;

        // Resolve skin texture (may be Steve/Alex default while async loading)
        SkinTextures skin = entry.getSkinTextures();
        Identifier skinId = skin.texture();

        int argbAlpha = (int) (alpha * 255f) << 24;

        // Draw border if enabled
        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int borderColor = (cfg.headBorderColor & 0x00FFFFFF) | argbAlpha;
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        // Draw face base layer
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE,
                64, 64, alpha);

        // Draw hat overlay layer
        drawSkinRegion(context, skinId,
                x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE,
                64, 64, alpha);
    }

    /**
     * Draws a region of a skin texture into the specified rectangle.
     * Maps srcX/srcY/srcW/srcH (pixels in a textureW×textureH texture) to
     * the destination rect (dstX, dstY, dstW, dstH) in screen space.
     */
    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int textureW, int textureH,
                                        float alpha) {
        float u0 = (float) srcX / textureW;
        float v0 = (float) srcY / textureH;
        float u1 = (float) (srcX + srcW) / textureW;
        float v1 = (float) (srcY + srcH) / textureH;

        context.drawTexturedQuad(
                texture,
                dstX, dstY,
                dstX + dstW, dstY + dstH,
                u0, u1, v0, v1
        );
    }

    /**
     * Draw player name below head icon. Centers the name above the given x.
     */
    public static void drawPlayerName(DrawContext context, PlayerListEntry entry,
                                       int centerX, int bottomY) {
        if (!BLBConfig.get().showNameTag) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        String name = entry.getProfile().name();
        int nameWidth = mc.textRenderer.getWidth(name);
        int nameX = centerX - nameWidth / 2;
        // Draw with drop shadow at 75% opacity (white with alpha)
        context.drawTextWithShadow(mc.textRenderer, name, nameX, bottomY + 2, 0xC0FFFFFF);
    }
}
