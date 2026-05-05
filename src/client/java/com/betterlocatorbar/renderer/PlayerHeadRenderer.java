package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Renders player skin heads in the locator bar.
 *
 * Uses DrawContext.drawTexturedQuad(Identifier, x1, y1, x2, y2, u1, u2, v1, v2)
 * which is a PUBLIC method confirmed in Yarn 1.21.6 docs — no RenderPipeline needed.
 */
public class PlayerHeadRenderer {

    private static final int SKIN_FACE_U    = 8;
    private static final int SKIN_FACE_V    = 8;
    private static final int SKIN_FACE_SIZE = 8;
    private static final int SKIN_HAT_U     = 40;
    private static final int SKIN_HAT_V     = 8;

    private static final Identifier DEFAULT_SKIN =
            Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    // ─── Bedrock detection ────────────────────────────────────────────────────

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

    // ─── Skin resolution ──────────────────────────────────────────────────────

    private static Identifier resolveSkinId(PlayerListEntry entry) {
        try {
            // getSkinTextures() exists on PlayerListEntry per Yarn 1.21.11 docs.
            // SkinTextures is net.minecraft.entity.player.SkinTextures.
            // We use var to avoid import issues — Java infers the type at compile time.
            var skinTextures = entry.getSkinTextures();
            if (skinTextures == null) return DEFAULT_SKIN;
            // Reflectively call texture() to avoid import of SkinTextures
            var tex = skinTextures.getClass().getMethod("texture").invoke(skinTextures);
            if (tex instanceof Identifier id) return id;
        } catch (Exception ignored) {}
        return DEFAULT_SKIN;
    }

    // ─── Head rendering ───────────────────────────────────────────────────────

    public static void drawPlayerHead(DrawContext context, PlayerListEntry entry,
                                       int x, int y, int size, float alpha) {
        if (entry == null) return;
        Identifier skinId = resolveSkinId(entry);

        BLBConfig cfg = BLBConfig.get();
        if (cfg.showHeadBorder) {
            int a = Math.clamp((int)(alpha * 180f), 0, 255);
            int borderColor = (a << 24) | (cfg.headBorderColor & 0x00FFFFFF);
            context.fill(x - 1, y - 1, x + size + 1, y + size + 1, borderColor);
        }

        // Face layer
        drawSkinRegion(context, skinId, x, y, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
        // Hat overlay layer
        drawSkinRegion(context, skinId, x, y, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
    }

    /**
     * Uses the public DrawContext.drawTexturedQuad(Identifier, x1, y1, x2, y2, u1, u2, v1, v2)
     * confirmed in Yarn 1.21.6 docs. No RenderPipeline, no color arg — just Identifier + coords.
     */
    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int size,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH) {
        float u1 = (float) srcX / texW;
        float u2 = (float) (srcX + srcW) / texW;
        float v1 = (float) srcY / texH;
        float v2 = (float) (srcY + srcH) / texH;

        // Public method from Yarn 1.21.6 DrawContext:
        // drawTexturedQuad(Identifier sprite, int x1, int y1, int x2, int y2,
        //                  float u1, float u2, float v1, float v2)
        context.drawTexturedQuad(texture,
                dstX, dstY, dstX + size, dstY + size,
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
