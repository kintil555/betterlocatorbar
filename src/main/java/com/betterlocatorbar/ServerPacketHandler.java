package com.betterlocatorbar;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers server-side packet handlers for the Player Tracker feature.
 *
 * <h3>Architecture (v2 — push + pull)</h3>
 * <ul>
 *   <li><b>Push:</b> {@link CoordBroadcastScheduler} broadcasts coordinates to all
 *       mod-enabled clients every second automatically.</li>
 *   <li><b>Pull:</b> If a client sends {@code betterlocatorbar:request_player_data},
 *       the server immediately responds — used for the first GUI open before the
 *       first push tick arrives.</li>
 * </ul>
 *
 * <p>This server component is entirely optional — if not installed server-side,
 * the client gracefully falls back to showing only online/offline status with no
 * coordinates.</p>
 */
public class ServerPacketHandler {

    // ─── Packet IDs ──────────────────────────────────────────────────────────

    static final Identifier REQUEST_ID  = Identifier.of("betterlocatorbar", "request_player_data");
    static final Identifier RESPONSE_ID = Identifier.of("betterlocatorbar", "response_player_data");

    // ─── Request payload (Client → Server) ───────────────────────────────────

    record RequestPayload() implements CustomPayload {
        static final Id<RequestPayload> ID = new Id<>(REQUEST_ID);
        static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC =
                PacketCodec.unit(new RequestPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Per-player entry ─────────────────────────────────────────────────────

    /**
     * One player's data inside a {@link ResponsePayload}.
     * Public so {@link CoordBroadcastScheduler} can build entries directly.
     */
    public record PlayerEntry(UUID uuid, String name, int x, int y, int z, String dim, boolean online)
            implements CustomPayload {
        static final Id<PlayerEntry> ID = new Id<>(RESPONSE_ID);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Response payload (Server → Client) ──────────────────────────────────

    /** Full player list sent to one client. */
    public record ResponsePayload(List<PlayerEntry> entries) implements CustomPayload {

        public static final Id<ResponsePayload> ID = new Id<>(RESPONSE_ID);

        static final PacketCodec<RegistryByteBuf, ResponsePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.collection(ArrayList::new,
                                PacketCodec.tuple(
                                        PacketCodecs.BYTE_ARRAY.xmap(
                                                ServerPacketHandler::uuidFrom,
                                                ServerPacketHandler::uuidTo),
                                        PlayerEntry::uuid,
                                        PacketCodecs.STRING,  PlayerEntry::name,
                                        PacketCodecs.INTEGER, PlayerEntry::x,
                                        PacketCodecs.INTEGER, PlayerEntry::y,
                                        PacketCodecs.INTEGER, PlayerEntry::z,
                                        PacketCodecs.STRING,  PlayerEntry::dim,
                                        PacketCodecs.BOOLEAN, PlayerEntry::online,
                                        PlayerEntry::new)),
                        ResponsePayload::entries,
                        ResponsePayload::new);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Alias used by {@link CoordBroadcastScheduler} for
     * {@code ServerPlayNetworking.canSend(player, RESPONSE_PAYLOAD_ID)}.
     */
    public static final CustomPayload.Id<ResponsePayload> RESPONSE_PAYLOAD_ID = ResponsePayload.ID;

    // ─── Registration ─────────────────────────────────────────────────────────

    public static void register() {
        PayloadTypeRegistry.playC2S().register(RequestPayload.ID, RequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResponsePayload.ID, ResponsePayload.CODEC);

        // Pull handler: respond immediately on explicit client request.
        // Acts as a fast-path for the first GUI open before the push scheduler fires.
        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity requester = context.player();
                    if (context.server() == null) return;

                    List<PlayerEntry> entries = new ArrayList<>();
                    for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                        if (p.getUuid().equals(requester.getUuid())) continue;

                        String dimKey = p.getEntityWorld().getRegistryKey().getValue().toString();
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
                });

        BetterLocatorBar.LOGGER.info("[BetterLocatorBar] Server packet handler registered.");
    }

    // ─── UUID helpers ─────────────────────────────────────────────────────────

    static byte[] uuidTo(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] b = new byte[16];
        for (int i = 7; i >= 0; i--) {
            b[i]     = (byte) (msb & 0xFF); msb >>= 8;
            b[i + 8] = (byte) (lsb & 0xFF); lsb >>= 8;
        }
        return b;
    }

    static UUID uuidFrom(byte[] b) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (b[i] & 0xFF);
            lsb = (lsb << 8) | (b[i + 8] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
