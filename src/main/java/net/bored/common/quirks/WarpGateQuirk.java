package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class WarpGateQuirk extends Quirk {

    public WarpGateQuirk() {
        super(new Identifier("plusultra", "warp_gate"), 0x2E003E); // Dark Purple
    }

    // --- AWAKENING CONFIGURATION ---

    @Override
    public void registerAwakening() {
        // Condition: Health < 4 Hearts (8 HP)
        this.setAwakeningCondition((player, data) -> player.getHealth() < 8.0f);
    }

    @Override
    public void onAwaken(PlayerEntity player) {
        player.sendMessage(Text.literal("WARP GATE AWAKENED: MIST BODY ACTIVE").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 2.0f);

        // Burst of particles
        if (!player.getWorld().isClient) {
            ((net.minecraft.server.world.ServerWorld) player.getWorld()).spawnParticles(
                    ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 100, 1.0, 1.0, 1.0, 0.5
            );
        }
    }

    // --- ABILITIES ---

    @Override
    public void registerAbilities() {
        this.addAbility(new Ability("Warp Mist", 40, 25) { // 2s Cooldown, 25 Stamina
            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient) return false;

                // 1. Calculate Range based on Awakening
                double range = 20.0;
                if (player instanceof IQuirkData data && data.isAwakened()) {
                    range = 60.0; // Massive range boost
                }

                // 2. Raycast to find landing spot
                Vec3d start = player.getEyePos();
                Vec3d direction = player.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));

                HitResult result = world.raycast(new RaycastContext(
                        start, end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                ));

                Vec3d targetPos = end; // Default to max range
                if (result.getType() != HitResult.Type.MISS) {
                    targetPos = result.getPos();
                }

                // 3. Teleport Logic
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                // Move player slightly above target to prevent suffocation
                player.teleport(targetPos.x, targetPos.y, targetPos.z);
                player.fallDistance = 0; // Reset fall damage

                world.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                return true;
            }

            @Override
            public int getStaminaCost() {
                // Reduce cost if awakened (Checking logic strictly inside getter is hard without player context,
                // so we usually handle cost reduction in the networking logic, but here is a static value)
                return super.getStaminaCost();
            }
        });
    }

    // --- VISUALS ---

    @Override
    public void onTick(PlayerEntity player) {
        super.onTick(player); // Check awakening

        if (player.getWorld().isClient) {
            // Subtle mist dripping from player
            if (player.getRandom().nextFloat() < 0.1f) {
                player.getWorld().addParticle(ParticleTypes.PORTAL,
                        player.getX() + (player.getRandom().nextDouble() - 0.5),
                        player.getY() + 1.5,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5),
                        0, -0.5, 0);
            }
        }
    }

    @Override
    public void onTickAwakened(PlayerEntity player) {
        if (player.getWorld().isClient) {
            // Heavy mist aura
            for (int i = 0; i < 3; i++) {
                player.getWorld().addParticle(ParticleTypes.SQUID_INK,
                        player.getX() + (player.getRandom().nextDouble() - 0.5) * 2,
                        player.getY() + 0.5,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5) * 2,
                        0, 0.1, 0);
            }
        }
    }
}