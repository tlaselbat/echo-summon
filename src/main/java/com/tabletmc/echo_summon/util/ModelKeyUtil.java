package com.tabletmc.echo_summon.util;

public final class ModelKeyUtil {
    private ModelKeyUtil() {}

    public static String mapEntityTypeToModelKey(String id) {
        if (id == null || id.isEmpty()) return "";
        return switch (id) {
            case "minecraft:horse" -> "echo_summon:horse";
            case "minecraft:donkey" -> "echo_summon:donkey";
            case "minecraft:mule" -> "echo_summon:mule";
            case "minecraft:camel" -> "echo_summon:camel";
            case "minecraft:skeleton_horse" -> "echo_summon:skeleton_horse";
            case "minecraft:zombie_horse" -> "echo_summon:zombie_horse";
            case "minecraft:happy_ghast" -> "echo_summon:happy_ghast";
            default -> "";
        };
    }
}
