package com.tabletmc.echo_summon.config;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpawnCommandConfig {
    private final List<SpawnEntry> spawnEntries;

    private SpawnCommandConfig(List<SpawnEntry> spawnEntries) {
        this.spawnEntries = Collections.unmodifiableList(new ArrayList<>(spawnEntries));
    }

    public static SpawnCommandConfig createDefault() {
        List<SpawnEntry> entries = new ArrayList<>();

        entries.add(SpawnEntry.simple("minecraft", "happy_ghast"));
        entries.add(SpawnEntry.simple("minecraft", "camel"));
        entries.add(SpawnEntry.simple("minecraft", "zombie_horse"));
        entries.add(SpawnEntry.simple("minecraft", "skeleton_horse"));
        entries.add(SpawnEntry.chest("minecraft", "donkey", false));
        entries.add(SpawnEntry.chest("minecraft", "mule", false));
        entries.add(SpawnEntry.chest("minecraft", "donkey", true));
        entries.add(SpawnEntry.chest("minecraft", "mule", true));

        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "WHITE", "BLACK_DOTS"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "GRAY", "WHITE_FIELD"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "CREAMY", "WHITE_DOTS"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "BROWN", "WHITE"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "DARK_BROWN", "NONE"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "CHESTNUT", "NONE"));
        entries.add(SpawnEntry.horseVariant("minecraft", "horse", "BLACK", "NONE"));

        return new SpawnCommandConfig(entries);
    }

    public List<SpawnEntry> spawnEntries() {
        return spawnEntries;
    }

    public record SpawnEntry(Identifier entityId, HorseVariant horseVariant, Boolean hasChest) {
        public static SpawnEntry simple(String namespace, String path) {
            return new SpawnEntry(Identifier.of(namespace, path), null, null);
        }

        public static SpawnEntry horseVariant(String namespace, String path, String color, String marking) {
            return new SpawnEntry(Identifier.of(namespace, path), new HorseVariant(color, marking), null);
        }

        public static SpawnEntry chest(String namespace, String path, boolean hasChest) {
            return new SpawnEntry(Identifier.of(namespace, path), null, hasChest);
        }
    }

    public record HorseVariant(String color, String marking) {}
}
