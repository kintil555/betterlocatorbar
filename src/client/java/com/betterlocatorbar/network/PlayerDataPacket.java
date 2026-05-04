package com.betterlocatorbar.network;

import com.betterlocatorbar.util.TrackedPlayerData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles client↔server communication for the Player Tracker GUI.
 *
 * <p>The client sends {@link RequestPayload} to ask the server for
 * a fresh list of player positions. The server replies with
 * {@link ResponsePayload} containing {@link TrackedPlayerData} entries.</p>
 */
public class PlayerDataPacket {

    // ─── Packet IDs ──────────────────────────────────────────────────────────
    public static final Identifier REQUEST_ID =
            Identifier.of("betterlocatorbar", "request_player_data");
    public static final Identifier RESPONSE_ID =
            Identifier.of("betterlocatorbar", "response_player_data");

    // ─── Request (Client → Server) ────────────────────────────────────────────
    public record RequestPayload() implements CustomPayload {
        public static final Id<RequestPayload> ID = new Id<>(REQUEST_ID);
        public static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC =
                PacketCodec.unit(new RequestPayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ─── Response (Server → Client) ──────────────────────────────────────────
    public record ResponsePayload(List<TrackedPlayerData> players) implements CustomPayload {
        public static final Id<ResponsePayload> ID = new Id<>(RESPONSE_ID);

        public static final PacketCodec<RegistryByteBuf, ResponsePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.collection(ArrayList::new, TrackedPlayerData.PACKET_CODEC),
                        ResponsePayload::players,
                        ResponsePayload::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ─── Registration ─────────────────────────────────────────────────────────
    // NOTE: PayloadTypeRegistry (C2S + S2C) is handled in ServerPacketHandler (common init).
    // This method only registers the client-side receiver.

    public static void registerS2C() {
        // Handle response on the client side — store data in TrackerDataStore
        ClientPlayNetworking.registerGlobalReceiver(ResponsePayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    TrackerDataStore.update(payload.players())
            );
        });
    }

    /** Send a request from client to server asking for player positions.
     *  Only sends if the server has the mod installed (canSend guard). */
    public static void sendRequest() {
        if (ClientPlayNetworking.canSend(RequestPayload.ID)) {
            ClientPlayNetworking.send(new RequestPayload());
        }
    }
}
