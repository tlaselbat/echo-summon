package com.tabletmc.echo_summon.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EchoSummonConfig {
    private EchoSummonConfig() {}

    private static final String FILE_NAME = "echo_summon.properties";
    private static final String KEY_BODY_RENDER = "enableCustomMountBodyRendering";
    private static final String KEY_BODY_GLINT = "enableMountBodyGlint";
    private static final String KEY_DISABLE_TEST_MOUNT_AI = "disableTestCommandMountAI";

    // Defaults: disabled
    public static boolean enableCustomMountBodyRendering = false;
    public static boolean enableMountBodyGlint = false;
    public static boolean disableTestCommandMountAI = false;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Properties p = new Properties();
        Path path = configPath();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            } catch (IOException ignored) {}
            enableCustomMountBodyRendering = getBoolean(p, KEY_BODY_RENDER, false);
            enableMountBodyGlint = getBoolean(p, KEY_BODY_GLINT, false);
            disableTestCommandMountAI = getBoolean(p, KEY_DISABLE_TEST_MOUNT_AI, false);
        } else {
            // Write defaults on first run
            save();
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty(KEY_BODY_RENDER, Boolean.toString(enableCustomMountBodyRendering));
        p.setProperty(KEY_BODY_GLINT, Boolean.toString(enableMountBodyGlint));
        p.setProperty(KEY_DISABLE_TEST_MOUNT_AI, Boolean.toString(disableTestCommandMountAI));
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "Echo Summon configuration");
            }
        } catch (IOException ignored) {}
    }

    private static boolean getBoolean(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1");
    }
}
