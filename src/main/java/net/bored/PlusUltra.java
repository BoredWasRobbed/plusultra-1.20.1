package net.bored;

import net.bored.api.data.IQuirkData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.bored.registry.QuirkRegistry;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.command.QuirkCommand;
import net.bored.network.PlusUltraNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Plus Ultra is initializing...");

		// 1. Init Systems
		QuirkRegistry.init();

		// 2. Register Quirks
		QuirkRegistry.register(new WarpGateQuirk());

		// 3. Register Commands & Networking
		CommandRegistrationCallback.EVENT.register(QuirkCommand::register);
		PlusUltraNetworking.init();

		// 4. Fix HUD not showing on join (Sync Data on Join)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			IQuirkData data = (IQuirkData) handler.player;
			data.syncQuirkData();
		});

		LOGGER.info("Plus Ultra initialization complete.");
	}
}