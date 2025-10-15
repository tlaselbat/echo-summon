package com.tabletmc.echo_summon;

import com.tabletmc.echo_summon.item.ModItems;
import com.tabletmc.echo_summon.net.ServerNetworking;
import com.tabletmc.echo_summon.command.SpawnAllowedMountsCommand;


public class ModInitializer implements net.fabricmc.api.ModInitializer {

	@Override
	public void onInitialize()
	{
		ModItems.registerModItems();
		ServerNetworking.init();
		SpawnAllowedMountsCommand.register();
		ModConstants.LOGGER.info("Travel System Revamp Initialized.");


		}



}


