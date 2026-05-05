package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Renders player skin heads in the locator bar using direct VertexConsumer rendering.
 * This bypasses DrawContext.drawTexture() entirely to avoid signature compatibility
 * issues across 1.21.x versions.
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
            SkinTextures skinTextures = entry.getSkinTextures();
            if (skinTextures == null) return DEFAULT_SKIN;
            Identifier tex = skinTextures.texture();
            return tex != null ? tex : DEFAULT_SKIN;
        } catch (Exception e) {
            return DEFAULT_SKIN;
        }
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

        // Draw face and hat layers using direct GL quad rendering
        int color = ((Math.clamp((int)(alpha * 255f), 0, 255)) << 24) | 0xFFFFFF;
        renderSkinQuad(context, skinId, x, y, size, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64, color);
        renderSkinQuad(context, skinId, x, y, size, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64, color);
    }

    /**
     * Renders a UV-mapped quad directly via Tessellator.
     * Completely avoids DrawContext.drawTexture() signature issues.
     */
    private static void renderSkinQuad(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int dstW, int dstH,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH, int color) {
        float u0 = (float) srcX / texW;
        float u1 = (float) (srcX + srcW) / texW;
        float v0 = (float) srcY / texH;
        float v1 = (float) (srcY + srcH) / texH;

        float x0 = dstX;
        float y0 = dstY;
        float x1 = dstX + dstW;
        float y1f = dstY + dstH;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(matrix, x0, y1f, 0).texture(u0, v1).color(r, g, b, a);
        buf.vertex(matrix, x1, y1f, 0).texture(u1, v1).color(r, g, b, a);
        buf.vertex(matrix, x1, y0,  0).texture(u1, v0).color(r, g, b, a);
        buf.vertex(matrix, x0, y0,  0).texture(u0, v0).color(r, g, b, a);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
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
