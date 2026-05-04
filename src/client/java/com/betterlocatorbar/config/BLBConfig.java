package com.betterlocatorbar.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;

/**
 * Stores and persists mod configuration to config/betterlocatorbar.json
 */
public class BLBConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("betterlocatorbar");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("betterlocatorbar.json");

    private static BLBConfig INSTANCE = new BLBConfig();

    // ── Settings ────────────────────────────────────────────────────────────
    /** Show player skin head instead of colored dot in locator bar */
    public boolean showPlayerHeads = true;

    /** Size multiplier for head icons in locator bar (1.0 = default size) */
    public float headScale = 1.0f;

    /** Show player name tag below head icon */
    public boolean showNameTag = true;

    /** Enable the experimental Player Tracker GUI feature */
    public boolean enableTrackerGui = true;

    /** Show a border around each head icon */
    public boolean showHeadBorder = true;

    /** Color of head border as ARGB int (default: white) */
    public int headBorderColor = 0xFFFFFFFF;

    /** Show compass direction letters overlay on the locator bar */
    public boolean showCompassOverlay = false;

    // ── Singleton access ─────────────────────────────────────────────────────
    public static BLBConfig get() {
        return INSTANCE;
    }

    // ── Persistence ──────────────────────────────────────────────────────────
    public static void load() {
        if (!CONFIG_PATH.toFile().exists()) {
            INSTANCE = new BLBConfig();
            save();
            return;
        }
        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            INSTANCE = GSON.fromJson(reader, BLBConfig.class);
            if (INSTANCE == null) INSTANCE = new BLBConfig();
        } catch (IOException e) {
            LOGGER.error("[BetterLocatorBar] Failed to load config, using defaults", e);
            INSTANCE = new BLBConfig();
        }
    }

    public static void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            LOGGER.error("[BetterLocatorBar] Failed to save config", e);
        }
    }
}
