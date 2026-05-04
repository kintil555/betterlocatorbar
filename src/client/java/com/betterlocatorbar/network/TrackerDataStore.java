package com.betterlocatorbar.network;

import com.betterlocatorbar.util.TrackedPlayerData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe client-side cache for the most recent player tracker data
 * received from the server via {@link PlayerDataPacket.ResponsePayload}.
 */
public final class TrackerDataStore {

    private static final List<TrackedPlayerData> PLAYERS = new CopyOnWriteArrayList<>();

    private TrackerDataStore() {}

    /** Replace the cached player list with fresh server data */
    public static void update(List<TrackedPlayerData> newData) {
        PLAYERS.clear();
        PLAYERS.addAll(newData);
    }

    /** Return an unmodifiable snapshot of the current player list */
    public static List<TrackedPlayerData> getPlayers() {
        return Collections.unmodifiableList(PLAYERS);
    }

    /** Clear all cached data (e.g., on disconnect) */
    public static void clear() {
        PLAYERS.clear();
    }
}
