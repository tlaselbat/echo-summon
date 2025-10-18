package com.tabletmc.echo_summon;

import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.command.SpawnAllowedMountsCommand;
import com.tabletmc.echo_summon.config.EchoSummonConfig;


public class ModInitializer implements net.fabricmc.api.ModInitializer {

    @Override
    public void onInitialize()
    {
        // Load config early on server to apply policy defaults
        EchoSummonConfig.load();
        ModItems.registerModItems();
        ServerNetworking.init();
        SpawnAllowedMountsCommand.register();
        ModConstants.LOGGER.info("Travel System Revamp initialized.");


        }

}

