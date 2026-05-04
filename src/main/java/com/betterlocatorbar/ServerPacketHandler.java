package com.betterlocatorbar;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers server-side packet handlers.
 *
 * <p>When a client sends a {@code betterlocatorbar:request_player_data} packet,
 * the server collects all online players' coordinates + dimension and replies
 * with a {@code betterlocatorbar:response_player_data} packet.</p>
 *
 * <p>This server component is entirely optional — if not installed server-side,
 * the client falls back to showing only online status (no coordinates).</p>
 */
public class ServerPacketHandler {

    // Re-define IDs (avoid depending on client module here)
    private static final Identifier REQUEST_ID  =
            Identifier.of("betterlocatorbar", "request_player_data");
    private static final Identifier RESPONSE_ID =
            Identifier.of("betterlocatorbar", "response_player_data");

    // ─── Request payload (mirrors client definition) ──────────────────────────
    record RequestPayload() implements CustomPayload {
        static final Id<RequestPayload> ID = new Id<>(REQUEST_ID);
        static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC =
                PacketCodec.unit(new RequestPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Response payload ─────────────────────────────────────────────────────
    record PlayerEntry(UUID uuid, String name, int x, int y, int z, String dim, boolean online)
            implements CustomPayload {
        // This is not a standalone payload — it's nested inside ResponsePayload below.
        // We use a flat approach: encode a list of field sequences.
        static final Id<PlayerEntry> ID = new Id<>(RESPONSE_ID);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Flat response: a PacketByteBuf with: [count as VarInt] then per-entry fields.
     * We use a manual approach since we can't share the client's TrackedPlayerData here.
     */
    record ResponsePayload(List<PlayerEntry> entries) implements CustomPayload {
        static final Id<ResponsePayload> ID = new Id<>(RESPONSE_ID);
        static final PacketCodec<RegistryByteBuf, ResponsePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.collection(ArrayList::new,
                                PacketCodec.tuple(
                                        // UUID as 16-byte array
                                        PacketCodecs.BYTE_ARRAY.xmap(
                                                ServerPacketHandler::uuidFrom,
                                                ServerPacketHandler::uuidTo),
                                        PlayerEntry::uuid,
                                        PacketCodecs.STRING, PlayerEntry::name,
                                        PacketCodecs.INTEGER, PlayerEntry::x,
                                        PacketCodecs.INTEGER, PlayerEntry::y,
                                        PacketCodecs.INTEGER, PlayerEntry::z,
                                        PacketCodecs.STRING, PlayerEntry::dim,
                                        PacketCodecs.BOOL, PlayerEntry::online,
                                        PlayerEntry::new
                                )),
                        ResponsePayload::entries,
                        ResponsePayload::new
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    public static void register() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(RequestPayload.ID, RequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResponsePayload.ID, ResponsePayload.CODEC);

        // Handle incoming client request
        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity requester = context.player();
                    if (requester.getServer() == null) return;

                    List<PlayerEntry> entries = new ArrayList<>();
                    for (ServerPlayerEntity p : requester.getServer().getPlayerManager().getPlayerList()) {
                        if (p.getUuid().equals(requester.getUuid())) continue; // skip self

                        String dimKey = p.getWorld().getRegistryKey().getValue().toString();
                        entries.add(new PlayerEntry(
                                p.getUuid(),
                                p.getName().getString(),
                                p.getBlockX(),
                                p.getBlockY(),
                                p.getBlockZ(),
                                dimKey,
                                true
                        ));
                    }

                    context.responseSender().sendPacket(new ResponsePayload(entries));
                }
        );

        BetterLocatorBar.LOGGER.info("[BetterLocatorBar] Server packet handler registered.");
    }

    // ─── UUID helpers ─────────────────────────────────────────────────────────
    private static byte[] uuidTo(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] b = new byte[16];
        for (int i = 7; i >= 0; i--) {
            b[i]     = (byte) (msb & 0xFF); msb >>= 8;
            b[i + 8] = (byte) (lsb & 0xFF); lsb >>= 8;
        }
        return b;
    }

    private static UUID uuidFrom(byte[] b) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (b[i] & 0xFF);
            lsb = (lsb << 8) | (b[i + 8] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
