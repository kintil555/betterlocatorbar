package com.betterlocatorbar.network;

import com.betterlocatorbar.ServerPacketHandler;
import com.betterlocatorbar.util.TrackedPlayerData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles client↔server communication for the Player Tracker GUI.
 *
 * <p>The client sends {@link RequestPayload} to ask the server for
 * a fresh list of player positions. The server replies with
 * {@link ServerPacketHandler.ResponsePayload} containing {@link ServerPacketHandler.PlayerEntry}
 * entries, which are then converted to {@link TrackedPlayerData} and stored.</p>
 *
 * <p><b>IMPORTANT:</b> We must NOT define a separate ResponsePayload here.
 * The server registers {@code betterlocatorbar:response_player_data} with its own
 * codec via {@link ServerPacketHandler}. Registering a second payload with the same
 * channel ID causes a codec mismatch → Network Protocol Error → client disconnect.</p>
 */
public class PlayerDataPacket {

    // ─── Packet IDs ──────────────────────────────────────────────────────────
    public static final Identifier REQUEST_ID =
            Identifier.of("betterlocatorbar", "request_player_data");

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

    // ─── Registration ─────────────────────────────────────────────────────────
    // NOTE: PayloadTypeRegistry (C2S + S2C) is handled in ServerPacketHandler (common init).
    // This method only registers the client-side receiver for the server's ResponsePayload.

    public static void registerS2C() {
        // Receive ServerPacketHandler.ResponsePayload (which contains PlayerEntry list),
        // convert each entry to TrackedPlayerData, then store in TrackerDataStore.
        ClientPlayNetworking.registerGlobalReceiver(
                ServerPacketHandler.ResponsePayload.ID,
                (payload, context) -> {
                    List<TrackedPlayerData> converted = payload.entries().stream()
                            .map(e -> new TrackedPlayerData(
                                    e.uuid(),
                                    e.name(),
                                    e.x(),
                                    e.y(),
                                    e.z(),
                                    e.dim(),
                                    e.online()
                            ))
                            .collect(Collectors.toList());
                    context.client().execute(() -> TrackerDataStore.update(converted));
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
