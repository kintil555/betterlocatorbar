package com.betterlocatorbar.renderer;

import com.betterlocatorbar.config.BLBConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.AssetInfo;
import net.minecraft.util.Identifier;

import java.util.UUID;

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

    /**
     * Filter logic matching vanilla LocatorBar:
     * Vanilla only shows players who have a valid PlayerListEntry with a game profile.
     * We add extra NPC filtering on top:
     * - Must have a non-blank name matching Minecraft username format
     * - UUID version must not be 2 (fake/NPC players from plugins)
     * - Bedrock players (Geyser) are always shown
     */
    public static boolean shouldShowInLocatorBar(PlayerListEntry entry) {
        if (entry.getProfile() == null) return false;
        String name = entry.getProfile().name();
        if (name == null || name.isBlank()) return false;
        UUID uuid = entry.getProfile().id();
        if (uuid == null) return false;

        // Bedrock players (Geyser) always show
        if (isBedrockUuid(uuid)) return true;

        // UUID version 2 = fake NPC player injected by server plugins
        int version = (int) ((uuid.getMostSignificantBits() >> 12) & 0xF);
        if (version == 2) return false;

        // Valid Minecraft username: 3-16 chars, only letters/digits/underscore
        if (!name.matches("[a-zA-Z0-9_]{3,16}")) return false;

        return true;
    }

    // ─── Skin resolution ──────────────────────────────────────────────────────

    private static Identifier resolveSkinId(PlayerListEntry entry) {
        SkinTextures skinTextures = entry.getSkinTextures();
        if (skinTextures == null) return DEFAULT_SKIN;

        // In 1.21.11, SkinTextures was refactored:
        // field 'texture' (Identifier) -> 'body' (AssetInfo.TextureAsset)
        // AssetInfo.TextureAsset wraps the texture, access via .texture() or .id()
        // We use reflection-free approach: body() returns AssetInfo.TextureAsset,
        // which itself has a texture() accessor returning Identifier.
        net.minecraft.entity.player.AssetInfo.TextureAsset bodyAsset = skinTextures.body();
        if (bodyAsset == null) return DEFAULT_SKIN;
        Identifier tex = bodyAsset.texture();
        return tex != null ? tex : DEFAULT_SKIN;
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

        drawSkinRegion(context, skinId, x, y, size,
                SKIN_FACE_U, SKIN_FACE_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
        drawSkinRegion(context, skinId, x, y, size,
                SKIN_HAT_U, SKIN_HAT_V, SKIN_FACE_SIZE, SKIN_FACE_SIZE, 64, 64);
    }

    private static void drawSkinRegion(DrawContext context, Identifier texture,
                                        int dstX, int dstY, int size,
                                        int srcX, int srcY, int srcW, int srcH,
                                        int texW, int texH) {
        float u1 = (float) srcX / texW;
        float u2 = (float) (srcX + srcW) / texW;
        float v1 = (float) srcY / texH;
        float v2 = (float) (srcY + srcH) / texH;
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
