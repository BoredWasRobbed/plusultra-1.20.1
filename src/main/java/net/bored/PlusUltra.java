package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Quirk;
import net.bored.common.entity.VillainEntity;
import net.bored.common.entity.WarpProjectileEntity;
import net.bored.common.quirks.AllForOneQuirk;
import net.bored.config.PlusUltraConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

		// Init Config
		PlusUltraConfig.get();

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
				if (entity instanceof IQuirkData target) {

					// Cleaned up: Removed Slot Limit Logic

					List<String> stealable = new ArrayList<>();

					if (target.hasQuirk()) {
						String activeId = target.getQuirk().getId().toString();
						if (!activeId.equals("plusultra:all_for_one")) {
							stealable.add(activeId);
						}
					}

					for (String s : target.getStolenQuirks()) {
						if (!stealable.contains(s) && !s.equals("plusultra:all_for_one")) {
							stealable.add(s);
						}
					}

					if (!stealable.isEmpty()) {
						if (player instanceof ServerPlayerEntity serverPlayer) {
							PacketByteBuf buf = PacketByteBufs.create();
							buf.writeInt(entity.getId()); // Target ID
							buf.writeInt(stealable.size());
							for(String s : stealable) buf.writeString(s);

							ServerPlayNetworking.send(serverPlayer, PlusUltraNetworking.OPEN_STEAL_SELECTION_PACKET, buf);
						}
						return ActionResult.SUCCESS;
					} else {
						player.sendMessage(Text.literal("Target has no quirks to steal.").formatted(Formatting.RED), true);
					}
				}
			}

			if (attacker.isGiveActive()) {
				if (entity instanceof IQuirkData target) {
					String toGive = attacker.getQuirkToGive();
					if (toGive != null && !toGive.isEmpty()) {
						target.addStolenQuirk(toGive);

						if (!target.hasQuirk()) {
							target.setQuirk(new Identifier(toGive));
						}

						attacker.removeStolenQuirk(toGive);
						attacker.setGiveActive(false);
						attacker.setQuirkToGive("");

						Quirk q = QuirkRegistry.get(new Identifier(toGive));
						Text quirkName = q != null ? q.getName() : Text.literal(toGive);
						player.sendMessage(Text.literal("Granted: ").append(quirkName).formatted(Formatting.GOLD), true);

						if (world instanceof ServerWorld sw) {
							spawnBlackLightning(sw, player.getPos(), entity.getPos());
						}
						return ActionResult.SUCCESS;
					}
				}
			}

			return ActionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			IQuirkData data = (IQuirkData) handler.player;

			if (PlusUltraConfig.get().enableQuirkOnJoin && !data.hasReceivedStarterQuirk()) {
				List<String> pool = PlusUltraConfig.get().starterQuirks;
				if (pool != null && !pool.isEmpty()) {
					String randomId = pool.get(new Random().nextInt(pool.size()));
					Identifier id = Identifier.tryParse(randomId);

					if (id != null) {
						Quirk q = QuirkRegistry.get(id);
						if (q != null) {
							data.setQuirk(id);
							data.addStolenQuirk(randomId);
							data.setReceivedStarterQuirk(true);

							handler.player.sendMessage(Text.literal("You have been born with: ").append(q.getName()).formatted(Formatting.GOLD, Formatting.BOLD), false);
						}
					} else {
						LOGGER.warn("Invalid quirk ID in starter config: " + randomId);
					}
				}
			}

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
				newData.setReceivedStarterQuirk(oldData.hasReceivedStarterQuirk());

				int anchorCount = oldData.getWarpAnchorCount();
				for (int i = 0; i < anchorCount; i++) {
					if (oldData.getWarpAnchorPos(i) != null) {
						newData.addWarpAnchor(oldData.getWarpAnchorPos(i), oldData.getWarpAnchorDim(i));
					}
				}
				if (oldData.isRegenActive()) newData.setRegenActive(true);

				if (oldData.isAllForOne()) {
					newData.setAllForOne(true);
				}
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

	public static void spawnBlackLightning(ServerWorld world, Vec3d start, Vec3d end) {
		Vec3d direction = end.subtract(start);
		double distance = direction.length();
		direction = direction.normalize();

		DustParticleEffect blackDust = new DustParticleEffect(new Vector3f(0.0f, 0.0f, 0.0f), 1.5f);
		DustParticleEffect redDust = new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.0f), 1.0f);

		int segments = (int) (distance * 2);

		for (int i = 0; i < segments; i++) {
			double progress = i / (double) segments;
			Vec3d pos = start.add(direction.multiply(progress * distance));

			double jitter = 0.3;
			double jx = (world.random.nextDouble() - 0.5) * jitter;
			double jy = (world.random.nextDouble() - 0.5) * jitter;
			double jz = (world.random.nextDouble() - 0.5) * jitter;

			world.spawnParticles(blackDust, pos.x + jx, pos.y + 1.0 + jy, pos.z + jz, 1, 0, 0, 0, 0);

			if (world.random.nextFloat() < 0.3f) {
				world.spawnParticles(redDust, pos.x + jx, pos.y + 1.0 + jy, pos.z + jz, 1, 0, 0, 0, 0);
			}
		}
	}
}