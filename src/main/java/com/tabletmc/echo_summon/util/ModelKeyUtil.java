package com.tabletmc.echo_summon.util;

/**
 * Utility for mapping entity type identifiers to CustomModelData string keys
 * used by the saddle summon tool item asset selectors.
 */
public final class ModelKeyUtil {
    private ModelKeyUtil() {}

    /**
     * Maps a namespaced entity id (e.g., "minecraft:horse") to an item model key
     * (e.g., "echo_summon:horse"). Returns empty string when there is no mapping.
     */
    public static String mapEntityTypeToModelKey(String id) {
        if (id == null || id.isEmpty()) return "";
        return switch (id) {
            case "minecraft:horse" -> "echo_summon:horse";
            case "minecraft:donkey" -> "echo_summon:donkey";
            case "minecraft:mule" -> "echo_summon:mule";
            case "minecraft:camel" -> "echo_summon:camel";
            case "minecraft:skeleton_horse" -> "echo_summon:skeleton_horse";
            case "minecraft:zombie_horse" -> "echo_summon:zombie_horse";
            default -> "";
        };
    }
}
