package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.common.entity.WarpProjectileEntity;
import net.bored.common.quirks.SuperRegenerationQuirk;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.bored.registry.QuirkRegistry;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.command.PlusUltraCommand;
import net.bored.network.PlusUltraNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// REGISTER ENTITY
	public static final EntityType<WarpProjectileEntity> WARP_PROJECTILE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "warp_projectile"),
			FabricEntityTypeBuilder.<WarpProjectileEntity>create(SpawnGroup.MISC, WarpProjectileEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(4).trackedUpdateRate(10)
					.build()
	);

	@Override
	public void onInitialize() {
		LOGGER.info("Plus Ultra is initializing...");

		QuirkRegistry.init();

		QuirkRegistry.register(new WarpGateQuirk());
		QuirkRegistry.register(new SuperRegenerationQuirk());

		CommandRegistrationCallback.EVENT.register(PlusUltraCommand::register);
		PlusUltraNetworking.init();

		// 1. Sync on Login
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			IQuirkData data = (IQuirkData) handler.player;
			data.syncQuirkData();
		});

		// 2. Copy Data on Death/Dimension Change
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			IQuirkData oldData = (IQuirkData) oldPlayer;
			IQuirkData newData = (IQuirkData) newPlayer;

			if (oldData.hasQuirk()) {
				newData.setQuirk(oldData.getQuirk().getId());
				newData.setAwakened(oldData.isAwakened());
				newData.setMaxStamina(oldData.getMaxStamina());
				newData.setStamina(oldData.getMaxStamina());
				newData.setLevel(oldData.getLevel());
				newData.setXp(oldData.getXp());

				int anchorCount = oldData.getWarpAnchorCount();
				for (int i = 0; i < anchorCount; i++) {
					if (oldData.getWarpAnchorPos(i) != null) {
						newData.addWarpAnchor(oldData.getWarpAnchorPos(i), oldData.getWarpAnchorDim(i));
					}
				}
			}
		});

		// 3. Sync Data after Respawn
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			IQuirkData data = (IQuirkData) newPlayer;
			data.syncQuirkData();
		});

		LOGGER.info("Plus Ultra initialization complete.");
	}
}