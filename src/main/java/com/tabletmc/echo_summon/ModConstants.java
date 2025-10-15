package com.tabletmc.echo_summon;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModConstants {

    public static final String MOD_ID = "echo_summon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Identifier Id(String path) {
        return Identifier.of(MOD_ID, path);
    }
    public static boolean summonCooldown = false;
    public static void summonCooldown() {

        // Start a new thread to handle the cooldown period.
        new Thread(() -> {
            summonCooldown = true;
            LOGGER.info("Cooldown started.");
            try {
                // Wait for one second.
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            } finally {
                summonCooldown = false;
                LOGGER.info("Cooldown ended.");}})
                .start();
    }
    // Cooldown duration (ticks) for reins actions to prevent spam
    public static final int SUMMON_COOLDOWN_TICKS = 20; // 1 second at 20 TPS

    // Shared NBT keys and identifiers
    public static final String STORED_MOUNT_KEY = MOD_ID + ":stored_mount";
    public static final String STORED_MOUNT_ID_KEY = "stored_mount_id";

    public static final String SUMMON_TAG = MOD_ID + ":saddle_summon_tool_summoned";
    public static final String HARNESS_SUMMON_TAG = MOD_ID + ":harness_summon_tool_summoned";

    public static final String SADDLE_SUMMON_TOOL_ID_KEY = "saddle_summon_tool_item_id";
    public static final String HARNESS_SUMMON_TOOL_ID_KEY = "harness_summon_tool_item_id";

    public static final String MOUNT_SADDLE_DATA_KEY = "mount_saddle_data";
    public static final String MOUNT_HARNESS_DATA_KEY = "mount_harness_data";

    public static final String ALLOWED_MOUNTS_TAG_PATH = "saddle_summon_tool_allowed_mounts";
    public static final String HARNESS_ALLOWED_MOUNTS_TAG_PATH = "harness_summon_tool_allowed_mounts";
}