package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.common.entity.VillainEntity;
import net.bored.common.entity.WarpProjectileEntity;
import net.bored.common.quirks.AllForOneQuirk;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.bored.registry.QuirkRegistry;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.common.quirks.SuperRegenerationQuirk;
import net.bored.command.PlusUltraCommand;
import net.bored.network.PlusUltraNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlusUltra implements ModInitializer {
	public static final String MOD_ID = "plusultra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<WarpProjectileEntity> WARP_PROJECTILE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "warp_projectile"),
			FabricEntityTypeBuilder.<WarpProjectileEntity>create(SpawnGroup.MISC, WarpProjectileEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(4).trackedUpdateRate(10)
					.build()
	);

	public static final EntityType<VillainEntity> VILLAIN_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MOD_ID, "villain"),
			FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, VillainEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.95f))
					.build()
	);

	@Override
	public void onInitialize() {
		LOGGER.info("Plus Ultra is initializing...");

		QuirkRegistry.init();
		QuirkRegistry.register(new WarpGateQuirk());
		QuirkRegistry.register(new SuperRegenerationQuirk());
		QuirkRegistry.register(new AllForOneQuirk());

		FabricDefaultAttributeRegistry.register(VILLAIN_ENTITY, VillainEntity.createVillainAttributes());

		CommandRegistrationCallback.EVENT.register(PlusUltraCommand::register);
		PlusUltraNetworking.init();

		// --- STEAL / GIVE LOGIC ---
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!(player instanceof IQuirkData attacker) || !attacker.isAllForOne()) return ActionResult.PASS;

			if (attacker.isStealActive()) {
				if (entity instanceof IQuirkData target && target.hasQuirk()) {
					String stolenId = target.getQuirk().getId().toString();
					attacker.addStolenQuirk(stolenId);

					// FIXED: Remove from target's storage AND unequip
					target.removeStolenQuirk(stolenId);
					target.setQuirk(null);

					attacker.setStealActive(false);
					player.sendMessage(Text.literal("Stolen: " + stolenId).formatted(Formatting.DARK_PURPLE), true);
					return ActionResult.SUCCESS;
				}
			}

			if (attacker.isGiveActive()) {
				if (entity instanceof IQuirkData target) {
					String toGive = attacker.getQuirkToGive();
					if (toGive != null && !toGive.isEmpty()) {
						// Give to Inventory
						target.addStolenQuirk(toGive);

						// If target has NO active quirk, equip this one automatically
						if (!target.hasQuirk()) {
							target.setQuirk(new Identifier(toGive));
						}

						attacker.removeStolenQuirk(toGive);

						attacker.setGiveActive(false);
						attacker.setQuirkToGive("");
						player.sendMessage(Text.literal("Granted: " + toGive).formatted(Formatting.GOLD), true);
						return ActionResult.SUCCESS;
					}
				}
			}

			return ActionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			IQuirkData data = (IQuirkData) handler.player;
			data.syncQuirkData();
		});

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
				if (oldData.isRegenActive()) newData.setRegenActive(true);

				// Copy AFO
				if (oldData.isAllForOne()) {
					newData.setAllForOne(true);
				}
				// Copy Inventory & Passives (For EVERYONE, not just AFO users)
				for(String s : oldData.getStolenQuirks()) newData.addStolenQuirk(s);
				for(String s : oldData.getActivePassives()) newData.togglePassive(s);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			IQuirkData data = (IQuirkData) newPlayer;
			data.syncQuirkData();
		});

		LOGGER.info("Plus Ultra initialization complete.");
	}
}