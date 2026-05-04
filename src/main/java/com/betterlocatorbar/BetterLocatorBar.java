package com.betterlocatorbar;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (shared) initializer for Better Locator Bar.
 * Server-side packet registration is done here.
 */
public class BetterLocatorBar implements ModInitializer {

    public static final String MOD_ID = "betterlocatorbar";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[BetterLocatorBar] Common initializer loaded.");
        // Register server-side packet handlers
        ServerPacketHandler.register();
    }
}
