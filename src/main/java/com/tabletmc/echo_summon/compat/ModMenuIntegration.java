package com.tabletmc.echo_summon.compat;

import com.tabletmc.echo_summon.config.EchoSummonConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (Screen parent) -> {
            // Ensure current values are loaded
            EchoSummonConfig.load();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.echo_summon.title"));
            builder.setSavingRunnable(EchoSummonConfig::save);

            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.echo_summon.category.general"));
            ConfigCategory glintSettings = builder.getOrCreateCategory(Text.translatable("config.echo_summon.category.glint_settings"));
            ConfigEntryBuilder eb = builder.entryBuilder();

            // Custom saddle item textures
            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.enableCustomSaddleItemTexture"),
                            EchoSummonConfig.enableCustomSaddleItemTexture)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.echo_summon.enableCustomSaddleItemTexture.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableCustomSaddleItemTexture = val)
                    .build());

            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.enableCustomSaddleItemBodyTexture"),
                            EchoSummonConfig.enableCustomSaddleItemBodyTexture)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.echo_summon.enableCustomSaddleItemBodyTexture.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableCustomSaddleItemBodyTexture = val)
                    .build());

            // removed: captured mount body texture (testing)

            // captured mount saddle glint (testing) [separate toggle]
            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.capturedSaddleGlintTesting"),
                            EchoSummonConfig.enableMountSaddleGlint)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.echo_summon.capturedSaddleGlintTesting.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableMountSaddleGlint = val)
                    .build());

            // capture mount body glint (testing) [unified entity glint]
            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.capturedBodyGlintTesting"),
                            EchoSummonConfig.enableMountBodyGlint)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.echo_summon.capturedBodyGlintTesting.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableMountBodyGlint = val)
                    .build());

            // Mount body glint settings
            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintColor"),
                            String.format("#%06X", (EchoSummonConfig.mountGlintColor & 0xFFFFFF)))
                    .setDefaultValue("#FFFFFF")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintColor.tooltip"))
                    .setSaveConsumer(str -> {
                        String s = str == null ? "#FFFFFF" : str.trim();
                        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
                        if (s.startsWith("#")) s = s.substring(1);
                        try {
                            EchoSummonConfig.mountGlintColor = Integer.parseInt(s, 16) & 0xFFFFFF;
                        } catch (Throwable ignored) {
                            EchoSummonConfig.mountGlintColor = 0xFFFFFF;
                        }
                    })
                    .build());

            // Echo glint engine (kill switch)
            glintSettings.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.enableEchoGlintEngine"),
                            EchoSummonConfig.enableEchoGlintEngine)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.echo_summon.enableEchoGlintEngine.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableEchoGlintEngine = val)
                    .build());

            // Intensity shown as 1-10, mapped to 0.1 - 1.0
            glintSettings.addEntry(eb.startIntField(
                            Text.translatable("config.echo_summon.mountGlintIntensity10"),
                            Math.max(1, Math.min(10, Math.round(EchoSummonConfig.mountGlintIntensity * 10f))))
                    .setDefaultValue(10)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintIntensity10.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintIntensity =
                            (val == null ? 10 : Math.max(1, Math.min(10, val))) / 10f)
                    .build());

            // Opacity 1-255
            glintSettings.addEntry(eb.startIntField(
                            Text.translatable("config.echo_summon.mountGlintOpacity"),
                            EchoSummonConfig.mountGlintOpacity)
                    .setDefaultValue(255)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintOpacity.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintOpacity =
                            val == null ? 255 : Math.max(1, Math.min(255, val)))
                    .build());

            // Scroll speed (UV per second)
            glintSettings.addEntry(eb.startFloatField(
                            Text.translatable("config.echo_summon.mountGlintScrollX"),
                            EchoSummonConfig.mountGlintScrollX)
                    .setDefaultValue(0.0f)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintScrollX.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintScrollX = val == null ? 0.0f : val)
                    .build());

            glintSettings.addEntry(eb.startFloatField(
                            Text.translatable("config.echo_summon.mountGlintScrollY"),
                            EchoSummonConfig.mountGlintScrollY)
                    .setDefaultValue(0.15f)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintScrollY.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintScrollY = val == null ? 0.15f : val)
                    .build());

            // Scale
            glintSettings.addEntry(eb.startFloatField(
                            Text.translatable("config.echo_summon.mountGlintScaleX"),
                            EchoSummonConfig.mountGlintScaleX)
                    .setDefaultValue(1.0f)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintScaleX.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintScaleX =
                            val == null ? 1.0f : Math.max(0.1f, Math.min(8.0f, val)))
                    .build());

            glintSettings.addEntry(eb.startFloatField(
                            Text.translatable("config.echo_summon.mountGlintScaleY"),
                            EchoSummonConfig.mountGlintScaleY)
                    .setDefaultValue(1.0f)
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintScaleY.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.mountGlintScaleY =
                            val == null ? 1.0f : Math.max(0.1f, Math.min(8.0f, val)))
                    .build());

            // Per-mount custom glint masks (sprite id or path without textures/ and .png)
            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.horse"),
                            EchoSummonConfig.mountGlintMaskHorse)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskHorse = normalizeMask(str))
                    .build());

            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.donkey"),
                            EchoSummonConfig.mountGlintMaskDonkey)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskDonkey = normalizeMask(str))
                    .build());

            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.mule"),
                            EchoSummonConfig.mountGlintMaskMule)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskMule = normalizeMask(str))
                    .build());

            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.skeleton"),
                            EchoSummonConfig.mountGlintMaskSkeletonHorse)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskSkeletonHorse = normalizeMask(str))
                    .build());

            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.zombie"),
                            EchoSummonConfig.mountGlintMaskZombieHorse)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskZombieHorse = normalizeMask(str))
                    .build());

            glintSettings.addEntry(eb.startStrField(
                            Text.translatable("config.echo_summon.mountGlintMask.camel"),
                            EchoSummonConfig.mountGlintMaskCamel)
                    .setDefaultValue("")
                    .setTooltip(Text.translatable("config.echo_summon.mountGlintMask.tooltip"))
                    .setSaveConsumer(str -> EchoSummonConfig.mountGlintMaskCamel = normalizeMask(str))
                    .build());

            return builder.build();
        };
    }

    // Normalizes a mask string: trims, strips leading "textures/" and trailing ".png" if present.
    private static String normalizeMask(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        // Strip leading textures/
        if (s.startsWith("textures/")) {
            s = s.substring("textures/".length());
        }
        // Strip .png extension
        if (s.endsWith(".png")) {
            s = s.substring(0, s.length() - 4);
        }
        return s;
    }
}
