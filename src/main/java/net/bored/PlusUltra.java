package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.common.entity.VillainEntity;
import net.bored.common.entity.WarpProjectileEntity;
import net.bored.common.quirks.AllForOneQuirk;
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

			// NEW: Steal Logic opens GUI now
			if (attacker.isStealActive()) {
				if (entity instanceof IQuirkData target) {

					// Collect all potential quirks to steal
					List<String> stealable = new ArrayList<>();

					// 1. If they have an equipped quirk, it's stealable
					if (target.hasQuirk()) {
						String activeId = target.getQuirk().getId().toString();
						// Don't allow stealing AFO itself to prevent issues
						if (!activeId.equals("plusultra:all_for_one")) {
							stealable.add(activeId);
						}
					}

					// 2. If they have quirks in storage (inventory)
					for (String s : target.getStolenQuirks()) {
						if (!stealable.contains(s) && !s.equals("plusultra:all_for_one")) {
							stealable.add(s);
						}
					}

					if (!stealable.isEmpty()) {
						// Send packet to opener to open UI
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

			// Existing Give Logic
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
						player.sendMessage(Text.literal("Granted: " + toGive).formatted(Formatting.GOLD), true);

						// Visuals for giving too? Why not
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

	// Visual Effect Helper: "Black Lightning"
	// Creates a jagged line of black dust particles between two points
	public static void spawnBlackLightning(ServerWorld world, Vec3d start, Vec3d end) {
		Vec3d direction = end.subtract(start);
		double distance = direction.length();
		direction = direction.normalize();

		// Black dust
		DustParticleEffect blackDust = new DustParticleEffect(new Vector3f(0.0f, 0.0f, 0.0f), 1.5f);
		// Red accent (optional, for that AFO feel)
		DustParticleEffect redDust = new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.0f), 1.0f);

		int segments = (int) (distance * 2); // Particles per block roughly

		// Main Beam
		for (int i = 0; i < segments; i++) {
			double progress = i / (double) segments;
			Vec3d pos = start.add(direction.multiply(progress * distance));

			// Add jaggedness
			double jitter = 0.3;
			double jx = (world.random.nextDouble() - 0.5) * jitter;
			double jy = (world.random.nextDouble() - 0.5) * jitter;
			double jz = (world.random.nextDouble() - 0.5) * jitter;

			world.spawnParticles(blackDust, pos.x + jx, pos.y + 1.0 + jy, pos.z + jz, 1, 0, 0, 0, 0);

			// Occasional red spark
			if (world.random.nextFloat() < 0.3f) {
				world.spawnParticles(redDust, pos.x + jx, pos.y + 1.0 + jy, pos.z + jz, 1, 0, 0, 0, 0);
			}
		}
	}
}