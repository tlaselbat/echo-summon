package com.tabletmc.echo_summon.config;

import java.util.Objects;

public final class SpawnConfigService {
    private static final SpawnConfigService INSTANCE = new SpawnConfigService();

    private final SpawnCommandConfig config;

    private SpawnConfigService() {
        this.config = SpawnCommandConfig.createDefault();
    }

    public static SpawnConfigService getInstance() {
        return INSTANCE;
    }

    public SpawnCommandConfig getConfig() {
        return Objects.requireNonNull(config);
    }
}
