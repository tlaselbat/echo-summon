package com.tabletmc.echo_summon;
import com.tabletmc.echo_summon.client.tooltip.SaddleSummonToolTooltipClient;
import com.tabletmc.echo_summon.client.tooltip.EquipmentTooltipsClient;
import com.tabletmc.echo_summon.keybinds.KeybindTickEvents;
import com.tabletmc.echo_summon.keybinds.RegisterKeybinds;
import com.tabletmc.echo_summon.net.ClientNetworking;
import com.tabletmc.echo_summon.config.EchoSummonConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Client entrypoint for Echo Summon. Wires up client-only systems such as
 * configuration loading, tooltips, keybinds, and client networking hooks.
 */
public class ClientInitializer implements net.fabricmc.api.ClientModInitializer {
    /**
     * Initializes client-only components on mod load.
     */
    @Override
    public void onInitializeClient() {
        // Load config on client so rendering toggles are available
        EchoSummonConfig.load();
        ClientNetworking.init();
        KeybindTickEvents.init();
        RegisterKeybinds.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        SaddleSummonToolTooltipClient.register();
        EquipmentTooltipsClient.register();
    }
}
