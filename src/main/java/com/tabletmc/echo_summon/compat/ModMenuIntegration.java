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
            ConfigEntryBuilder eb = builder.entryBuilder();

            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.enableCustomMountBodyRendering").copy().append(Text.literal(" (testing)")),
                            EchoSummonConfig.enableCustomMountBodyRendering)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.echo_summon.enableCustomMountBodyRendering.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableCustomMountBodyRendering = val)
                    .build());

            general.addEntry(eb.startBooleanToggle(
                            Text.translatable("config.echo_summon.enableMountBodyGlint").copy().append(Text.literal(" (testing)")),
                            EchoSummonConfig.enableMountBodyGlint)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.echo_summon.enableMountBodyGlint.tooltip"))
                    .setSaveConsumer(val -> EchoSummonConfig.enableMountBodyGlint = val)
                    .build());

            return builder.build();
        };
    }
}
