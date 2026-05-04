package com.betterlocatorbar;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) initializer for Better Locator Bar.
 *
 * <p>Registers server-side packet handlers and the real-time coordinate
 * broadcast scheduler. Both components are no-ops when running on a
 * client-only installation (Fabric handles environment gating).</p>
 */
public class BetterLocatorBar implements ModInitializer {

    public static final String MOD_ID = "betterlocatorbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[BetterLocatorBar] Common initializer loaded.");

        // Register pull-based request handler + payload types
        ServerPacketHandler.register();

        // Register push-based real-time coordinate broadcaster (server tick)
        CoordBroadcastScheduler.register();
    }
}
