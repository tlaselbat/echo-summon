package com.tabletmc.echo_summon;

import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Pattern;

public class ModConstants {

    public static final String MOD_ID = "echo_summon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier Id(String path) {
        return Identifier.of(MOD_ID, path);
    }
    public static final String SADDLE_TOOL_TAG_PREFIX = MOD_ID + ":saddle_tool:";
    public static final String REMOTE_ACTIVE_KEY = MOD_ID + ":remote_active";
    public static final String STORED_DIM_KEY = MOD_ID + ":stored_dim";
    public static final String STORED_POS_KEY = MOD_ID + ":stored_pos";
    public static final String LAST_SCAN_TIME_KEY = MOD_ID + ":last_scan_time";
    public static final int MAX_PACKET_STRING_LENGTH = 128;
    public static final Pattern SAFE_STRING_PAYLOAD = Pattern.compile("^[a-z0-9_:-]{1,128}$");
    // Cooldown duration (ticks) for reins actions to prevent spam
    public static final int SUMMON_COOLDOWN_TICKS = 20; // 1 second at 20 TPS

    // Shared NBT keys and identifiers
    public static final String STORED_MOUNT_KEY = MOD_ID + ":stored_mount";
    public static final String STORED_MOUNT_ID_KEY = "stored_mount_id";

    public static final String SUMMON_TAG = MOD_ID + ":saddle_summon_tool_summoned";
    // Tool-side flag indicating a mount is currently summoned by this tool
    public static final String MOUNT_SUMMONED_TOOL_FLAG = MOD_ID + ":mount_summoned";

    public static final String SADDLE_SUMMON_TOOL_ID_KEY = "saddle_summon_tool_item_id";

    public static final String MOUNT_SADDLE_DATA_KEY = "mount_saddle_data";
    // Flag indicating the stored mount originally had a vanilla saddle at capture time
    public static final String HAD_VANILLA_SADDLE_KEY = MOD_ID + ":had_saddle";


    // Hardcoded list of entity IDs allowed for the saddle summon tool
    public static final Set<Identifier> SADDLE_ALLOWED_IDS = Set.of(
            Identifier.of("minecraft", "camel"),
            Identifier.of("minecraft", "donkey"),
            Identifier.of("minecraft", "mule"),
            Identifier.of("minecraft", "horse"),
            Identifier.of("minecraft", "skeleton_horse"),
            Identifier.of("minecraft", "zombie_horse"),
            Identifier.of("minecraft", "happy_ghast")
    );

    public static boolean isSaddleAllowed(EntityType<?> type) {
        Identifier id = EntityType.getId(type);
        // Allow listed explicit IDs, plus be tolerant of namespace differences for happy_ghast
        // Some packs/mods may register Happy Ghast under a different namespace than "minecraft"
        return id != null && (
                SADDLE_ALLOWED_IDS.contains(id)
                        || "happy_ghast".equals(id.getPath())
        );
    }

    public static final String ALLOWED_MOUNTS_TAG_PATH = "saddle_summon_tool_allowed_mounts";
}