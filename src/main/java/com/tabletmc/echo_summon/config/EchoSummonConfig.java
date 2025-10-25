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
    private static final String KEY_BODY_GLINT = "enableMountBodyGlint";
    private static final String KEY_SADDLE_GLINT = "enableMountSaddleGlint";
    private static final String KEY_SADDLE_TEX_MAIN = "enableCustomSaddleItemTexture";
    private static final String KEY_SADDLE_TEX_BODY = "enableCustomSaddleItemBodyTexture";
    private static final String KEY_DISABLE_TEST_MOUNT_AI = "disableTestCommandMountAI";
    private static final String KEY_GLINT_INTENSITY = "mountGlintIntensity"; // 0.0 - 1.0
    private static final String KEY_GLINT_COLOR = "mountGlintColor"; // RGB int value (0xRRGGBB)
    private static final String KEY_GLINT_OPACITY = "mountGlintOpacity"; // 1 - 255
    private static final String KEY_ECHO_GLINT_ENGINE = "enableEchoGlintEngine";
    private static final String KEY_GLINT_SCROLL_X = "mountGlintScrollX";
    private static final String KEY_GLINT_SCROLL_Y = "mountGlintScrollY";
    private static final String KEY_GLINT_SCALE_X = "mountGlintScaleX";
    private static final String KEY_GLINT_SCALE_Y = "mountGlintScaleY";
    private static final String KEY_MASK_HORSE = "mountGlintMask.horse";
    private static final String KEY_MASK_DONKEY = "mountGlintMask.donkey";
    private static final String KEY_MASK_MULE = "mountGlintMask.mule";
    private static final String KEY_MASK_SKELETON = "mountGlintMask.skeleton_horse";
    private static final String KEY_MASK_ZOMBIE = "mountGlintMask.zombie_horse";
    private static final String KEY_MASK_CAMEL = "mountGlintMask.camel";

    // Defaults: disabled
    public static boolean enableMountBodyGlint = false;
    public static boolean enableMountSaddleGlint = false;
    // Custom saddle textures
    public static boolean enableCustomSaddleItemTexture = true; // controls <mount>_saddle_item.png usage
    public static boolean enableCustomSaddleItemBodyTexture = true; // controls <mount>_saddle_item_body.png overlay
    public static boolean disableTestCommandMountAI = false;
    public static float mountGlintIntensity = 1.0f;
    public static int mountGlintColor = 0xFFFFFF; // white tint by default
    public static int mountGlintOpacity = 255; // 1-255
    public static boolean enableEchoGlintEngine = false; // kill switch
    public static float mountGlintScrollX = 0.0f; // UV units per second
    public static float mountGlintScrollY = 0.15f;
    public static float mountGlintScaleX = 1.0f; // UV scale
    public static float mountGlintScaleY = 1.0f;
    // Per-mount custom glint masks (sprite id or relative path). Empty = use default body asset mask.
    // Defaults point to conventional direct textures under textures/entity/equipment/horse_body/*.png
    public static String mountGlintMaskHorse = "entity/equipment/horse_body/horse_body_glint_mask";
    public static String mountGlintMaskDonkey = "entity/equipment/horse_body/donkey_body_glint_mask";
    public static String mountGlintMaskMule = "entity/equipment/horse_body/mule_body_glint_mask";
    public static String mountGlintMaskSkeletonHorse = "entity/equipment/horse_body/skeleton_horse_body_glint_mask";
    public static String mountGlintMaskZombieHorse = "entity/equipment/horse_body/zombie_horse_body_glint_mask";
    public static String mountGlintMaskCamel = "entity/equipment/horse_body/camel_body_glint_mask";

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
            enableMountBodyGlint = getBoolean(p, KEY_BODY_GLINT, false);
            enableMountSaddleGlint = getBoolean(p, KEY_SADDLE_GLINT, false);
            enableCustomSaddleItemTexture = getBoolean(p, KEY_SADDLE_TEX_MAIN, true);
            enableCustomSaddleItemBodyTexture = getBoolean(p, KEY_SADDLE_TEX_BODY, true);
            disableTestCommandMountAI = getBoolean(p, KEY_DISABLE_TEST_MOUNT_AI, false);
            mountGlintIntensity = getFloat(p, KEY_GLINT_INTENSITY, 1.0f);
            mountGlintColor = getColorInt(p, KEY_GLINT_COLOR, 0xFFFFFF);
            mountGlintOpacity = getIntInRange(p, KEY_GLINT_OPACITY, 255, 1, 255);
            enableEchoGlintEngine = getBoolean(p, KEY_ECHO_GLINT_ENGINE, false);
            mountGlintScrollX = getFloat(p, KEY_GLINT_SCROLL_X, 0.0f);
            mountGlintScrollY = getFloat(p, KEY_GLINT_SCROLL_Y, 0.15f);
            mountGlintScaleX = getFloatInRange(p, KEY_GLINT_SCALE_X, 1.0f, 0.1f, 8.0f);
            mountGlintScaleY = getFloatInRange(p, KEY_GLINT_SCALE_Y, 1.0f, 0.1f, 8.0f);
            mountGlintMaskHorse = getString(p, KEY_MASK_HORSE, "");
            mountGlintMaskDonkey = getString(p, KEY_MASK_DONKEY, "");
            mountGlintMaskMule = getString(p, KEY_MASK_MULE, "");
            mountGlintMaskSkeletonHorse = getString(p, KEY_MASK_SKELETON, "");
            mountGlintMaskZombieHorse = getString(p, KEY_MASK_ZOMBIE, "");
            mountGlintMaskCamel = getString(p, KEY_MASK_CAMEL, "");
        } else {
            // Write defaults on first run
            save();
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty(KEY_BODY_GLINT, Boolean.toString(enableMountBodyGlint));
        p.setProperty(KEY_SADDLE_GLINT, Boolean.toString(enableMountSaddleGlint));
        p.setProperty(KEY_SADDLE_TEX_MAIN, Boolean.toString(enableCustomSaddleItemTexture));
        p.setProperty(KEY_SADDLE_TEX_BODY, Boolean.toString(enableCustomSaddleItemBodyTexture));
        p.setProperty(KEY_DISABLE_TEST_MOUNT_AI, Boolean.toString(disableTestCommandMountAI));
        p.setProperty(KEY_GLINT_INTENSITY, Float.toString(clamp01(mountGlintIntensity)));
        // Store as hex without 0x prefix for readability
        p.setProperty(KEY_GLINT_COLOR, String.format("%06X", (mountGlintColor & 0xFFFFFF)));
        p.setProperty(KEY_GLINT_OPACITY, Integer.toString(clamp(mountGlintOpacity, 1, 255)));
        p.setProperty(KEY_ECHO_GLINT_ENGINE, Boolean.toString(enableEchoGlintEngine));
        p.setProperty(KEY_GLINT_SCROLL_X, Float.toString(mountGlintScrollX));
        p.setProperty(KEY_GLINT_SCROLL_Y, Float.toString(mountGlintScrollY));
        p.setProperty(KEY_GLINT_SCALE_X, Float.toString(mountGlintScaleX));
        p.setProperty(KEY_GLINT_SCALE_Y, Float.toString(mountGlintScaleY));
        p.setProperty(KEY_MASK_HORSE, nonNull(mountGlintMaskHorse));
        p.setProperty(KEY_MASK_DONKEY, nonNull(mountGlintMaskDonkey));
        p.setProperty(KEY_MASK_MULE, nonNull(mountGlintMaskMule));
        p.setProperty(KEY_MASK_SKELETON, nonNull(mountGlintMaskSkeletonHorse));
        p.setProperty(KEY_MASK_ZOMBIE, nonNull(mountGlintMaskZombieHorse));
        p.setProperty(KEY_MASK_CAMEL, nonNull(mountGlintMaskCamel));
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "Echo Summon configuration");
            }
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean getBoolean(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1");
    }

    private static float getFloat(Properties p, String key, float def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return clamp01(Float.parseFloat(v));
        } catch (Throwable ignored) {
            return def;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static int getIntInRange(Properties p, String key, int def, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            int i = Integer.parseInt(v.trim());
            return clamp(i, min, max);
        } catch (Throwable ignored) {
            return def;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static float getFloatInRange(Properties p, String key, float def, float min, float max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            float f = Float.parseFloat(v.trim());
            return Math.max(min, Math.min(max, f));
        } catch (Throwable ignored) {
            return def;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static int getColorInt(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            String s = v.trim();
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            if (s.startsWith("#")) s = s.substring(1);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static String getString(Properties p, String key, String def) {
        String v = p.getProperty(key);
        return v == null ? def : v.trim();
    }

    private static float clamp01(float f) {
        return Math.min(1f, Math.max(0f, f));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String nonNull(String s) {
        return s == null ? "" : s.trim();
    }
}
