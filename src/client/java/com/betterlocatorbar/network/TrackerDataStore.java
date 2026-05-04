package com.betterlocatorbar.network;

import com.betterlocatorbar.util.TrackedPlayerData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe client-side cache for the most recent player tracker data.
 *
 * <p>Updated either by:
 * <ul>
 *   <li><b>Push</b>: the server's {@code CoordBroadcastScheduler} sends data every ~1 s
 *       automatically as long as the server-side mod is installed.</li>
 *   <li><b>Pull</b>: the client explicitly requests data on GUI open via
 *       {@link PlayerDataPacket#sendRequest()}.</li>
 * </ul>
 * </p>
 */
public final class TrackerDataStore {

    private static final List<TrackedPlayerData> PLAYERS = new CopyOnWriteArrayList<>();

    /** System time (ms) of the last successful update from the server. -1 = never. */
    private static final AtomicLong LAST_UPDATE_MS = new AtomicLong(-1L);

    private TrackerDataStore() {}

    /** Replace the cached player list with fresh server data. */
    public static void update(List<TrackedPlayerData> newData) {
        PLAYERS.clear();
        PLAYERS.addAll(newData);
        LAST_UPDATE_MS.set(System.currentTimeMillis());
    }

    /** Return an unmodifiable snapshot of the current player list. */
    public static List<TrackedPlayerData> getPlayers() {
        return Collections.unmodifiableList(PLAYERS);
    }

    /**
     * Returns {@code true} if we have received at least one server push/pull
     * and the data is fresh (received within the last 5 seconds).
     */
    public static boolean hasFreshData() {
        long last = LAST_UPDATE_MS.get();
        return last != -1L && (System.currentTimeMillis() - last) < 5_000L;
    }

    /**
     * Returns how many milliseconds ago the last update was received,
     * or {@code -1} if no update has been received yet.
     */
    public static long millisSinceLastUpdate() {
        long last = LAST_UPDATE_MS.get();
        return last == -1L ? -1L : System.currentTimeMillis() - last;
    }

    /** Clear all cached data (e.g., on disconnect). */
    public static void clear() {
        PLAYERS.clear();
        LAST_UPDATE_MS.set(-1L);
    }
}
