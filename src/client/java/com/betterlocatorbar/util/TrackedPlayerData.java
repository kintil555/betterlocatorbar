package com.betterlocatorbar.util;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Immutable snapshot of a tracked player's state sent from server → client.
 *
 * @param uuid         Player UUID
 * @param name         Display name (username)
 * @param x            Block X coordinate
 * @param y            Block Y coordinate
 * @param z            Block Z coordinate
 * @param dimension    Registry key string of the dimension (e.g. "minecraft:overworld")
 * @param isOnline     Whether the player is currently connected
 */
public record TrackedPlayerData(
        UUID uuid,
        String name,
        int x,
        int y,
        int z,
        String dimension,
        boolean isOnline
) {
    /** PacketCodec for serialisation over the network */
    public static final PacketCodec<RegistryByteBuf, TrackedPlayerData> PACKET_CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BYTE_ARRAY.xmap(
                            bytes -> uuidFromBytes(bytes),
                            uuid -> uuidToBytes(uuid)
                    ), TrackedPlayerData::uuid,
                    PacketCodecs.STRING, TrackedPlayerData::name,
                    PacketCodecs.INTEGER, TrackedPlayerData::x,
                    PacketCodecs.INTEGER, TrackedPlayerData::y,
                    PacketCodecs.INTEGER, TrackedPlayerData::z,
                    PacketCodecs.STRING, TrackedPlayerData::dimension,
                    PacketCodecs.BOOL, TrackedPlayerData::isOnline,
                    TrackedPlayerData::new
            );

    // ─── UUID ↔ byte[] helpers (UUID is 16 bytes) ─────────────────────────
    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buf = new byte[16];
        for (int i = 7; i >= 0; i--) {
            buf[i]     = (byte) (msb & 0xFF); msb >>= 8;
            buf[i + 8] = (byte) (lsb & 0xFF); lsb >>= 8;
        }
        return buf;
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
            lsb = (lsb << 8) | (bytes[i + 8] & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    /** Formatted coordinate string for GUI display */
    public String coordsString() {
        return "X: %d, Y: %d, Z: %d".formatted(x, y, z);
    }

    /** Short dimension label for GUI display */
    public String dimensionLabel() {
        return switch (dimension) {
            case "minecraft:overworld"   -> "Overworld";
            case "minecraft:the_nether"  -> "Nether";
            case "minecraft:the_end"     -> "The End";
            default -> dimension.contains(":") ? dimension.split(":")[1] : dimension;
        };
    }
}
