package net.bored;

import net.bored.api.data.IQuirkData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents; // NEW IMPORT
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.bored.registry.QuirkRegistry;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.command.PlusUltraCommand;
import net.bored.network.PlusUltraNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Plus Ultra is initializing...");

		QuirkRegistry.init();

		QuirkRegistry.register(new WarpGateQuirk());

		CommandRegistrationCallback.EVENT.register(PlusUltraCommand::register);
		PlusUltraNetworking.init();

		// 1. Sync on Login (Already had this)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			IQuirkData data = (IQuirkData) handler.player;
			data.syncQuirkData();
		});

		// 2. Copy Data on Death/Dimension Change (NEW)
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			IQuirkData oldData = (IQuirkData) oldPlayer;
			IQuirkData newData = (IQuirkData) newPlayer;

			if (oldData.hasQuirk()) {
				// Restore Quirk
				newData.setQuirk(oldData.getQuirk().getId());

				// Restore Progression/Stats
				newData.setAwakened(oldData.isAwakened());
				newData.setMaxStamina(oldData.getMaxStamina());
				newData.setStamina(oldData.getMaxStamina()); // Reset to full stamina on respawn

				// Restore Anchor
				if (oldData.getWarpAnchorPos() != null) {
					newData.setWarpAnchor(oldData.getWarpAnchorPos(), oldData.getWarpAnchorDim());
				}
			}
		});

		// 3. Sync Data after Respawn (NEW)
		// This ensures the HUD reappears immediately after clicking "Respawn"
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			IQuirkData data = (IQuirkData) newPlayer;
			data.syncQuirkData();
		});

		LOGGER.info("Plus Ultra initialization complete.");
	}
}