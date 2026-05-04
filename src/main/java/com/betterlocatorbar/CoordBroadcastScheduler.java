package com.betterlocatorbar;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically broadcasts every online player's coordinates to all clients
 * that have the mod installed (detected via canSend check on the response channel).
 *
 * <p>Push interval: every 20 ticks = 1 second. This gives smooth real-time
 * tracking without hammering the network — coordinates are small payloads.</p>
 *
 * <p>Flow:
 * <pre>
 *   Server tick (every 20t)
 *     └─ for each online player P
 *           └─ build list of all OTHER players' coords
 *              └─ send ResponsePayload → P  (if P has the mod)
 * </pre>
 * </p>
 *
 * <p>The pull-based request handler in {@link ServerPacketHandler} is kept as
 * a fallback (e.g. for the very first open of the GUI before the first push).</p>
 */
public final class CoordBroadcastScheduler {

    /** Push interval in ticks. 20 = once per second. */
    private static final int PUSH_INTERVAL_TICKS = 20;

    private static int tickCounter = 0;

    private CoordBroadcastScheduler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CoordBroadcastScheduler::onServerTick);
        BetterLocatorBar.LOGGER.info("[BetterLocatorBar] Coord broadcast scheduler registered (push every {}t).", PUSH_INTERVAL_TICKS);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < PUSH_INTERVAL_TICKS) return;
        tickCounter = 0;

        List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
        if (online.size() < 2) return; // nothing to broadcast if alone

        for (ServerPlayerEntity recipient : online) {
            // Only send if the client actually has the mod (canSend checks channel registration)
            if (!ServerPlayNetworking.canSend(recipient, ServerPacketHandler.RESPONSE_PAYLOAD_ID)) continue;

            // Build the payload: all players except the recipient themselves
            List<ServerPacketHandler.PlayerEntry> entries = new ArrayList<>(online.size() - 1);
            for (ServerPlayerEntity p : online) {
                if (p.getUuid().equals(recipient.getUuid())) continue;

                String dimKey = p.getEntityWorld().getRegistryKey().getValue().toString();
                entries.add(new ServerPacketHandler.PlayerEntry(
                        p.getUuid(),
                        p.getName().getString(),
                        p.getBlockX(),
                        p.getBlockY(),
                        p.getBlockZ(),
                        dimKey,
                        true
                ));
            }

            ServerPlayNetworking.send(recipient, new ServerPacketHandler.ResponsePayload(entries));
        }
    }
}
