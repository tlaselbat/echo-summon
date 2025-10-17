package com.tabletmc.echo_summon;

import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;

public class ModConstants {

    public static final String MOD_ID = "echo_summon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier Id(String path) {
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
    // Flag set on harness summon tool when a happy ghast is stored
    public static final String STORED_IS_HAPPY_GHAST_KEY = "stored_is_happy_ghast";

    // Convenience identifier for the happy ghast entity id
    public static final Identifier HAPPY_GHAST_ID = Identifier.of("minecraft", "happy_ghast");

    // Hardcoded list of entity IDs allowed for the saddle summon tool
    public static final Set<Identifier> SADDLE_ALLOWED_IDS = Set.of(
            Identifier.of("minecraft", "camel"),
            Identifier.of("minecraft", "horse"),
            Identifier.of("minecraft", "donkey"),
            Identifier.of("minecraft", "mule"),
            Identifier.of("minecraft", "skeleton_horse"),
            Identifier.of("minecraft", "zombie_horse"),
            Identifier.of("minecraft", "happy_ghast")
    );

    public static boolean isSaddleAllowed(EntityType<?> type) {
        Identifier id = EntityType.getId(type);
        return id != null && SADDLE_ALLOWED_IDS.contains(id);
    }

    public static final String ALLOWED_MOUNTS_TAG_PATH = "saddle_summon_tool_allowed_mounts";
    public static final String HARNESS_ALLOWED_MOUNTS_TAG_PATH = "harness_summon_tool_allowed_mounts";
}