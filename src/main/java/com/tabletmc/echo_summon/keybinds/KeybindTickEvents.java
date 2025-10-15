package com.tabletmc.echo_summon.keybinds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Registers client-side tick events for the mod.
 * Previous handled the logic for the summon horse keybind.
 */
public class KeybindTickEvents {

    /**
     * Initializes the client-side tick events.
     * This method registers the onEndTick method to be called at the end of each client tick.
     */
    public static void init() {
        // Register the onEndTick method to be called at the end of each client tick.
        ClientTickEvents.END_CLIENT_TICK.register(KeybindTickEvents::onEndTick);
    }

    /**
     * Called at the end of each client tick.
     * Keybind removed; this is now a no-op.
     */
    private static void onEndTick(MinecraftClient client) {
        // No operation
    }
}