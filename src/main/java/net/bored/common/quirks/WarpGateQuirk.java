package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

public class WarpGateQuirk extends Quirk {

    public WarpGateQuirk() {
        super(new Identifier("plusultra", "warp_gate"), 0x2E003E); // Dark Purple
    }

    @Override
    public void registerAwakening() {
        this.setAwakeningCondition((player, data) -> player.getHealth() < 8.0f);
    }

    @Override
    public void onAwaken(PlayerEntity player) {
        player.sendMessage(Text.literal("WARP GATE AWAKENED: MIST BODY ACTIVE").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 2.0f);

        if (!player.getWorld().isClient) {
            ((ServerWorld) player.getWorld()).spawnParticles(
                    ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 100, 1.0, 1.0, 1.0, 0.5
            );
        }
    }

    @Override
    public void registerAbilities() {
        // ABILITY 1: Warp Mist
        this.addAbility(new Ability("Warp Mist", 40, 25) {
            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient) return false;

                double range = 20.0;
                if (player instanceof IQuirkData data && data.isAwakened()) {
                    range = 60.0;
                }

                Vec3d start = player.getEyePos();
                Vec3d direction = player.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));

                HitResult result = world.raycast(new RaycastContext(
                        start, end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                ));

                Vec3d targetPos = end;
                if (result.getType() != HitResult.Type.MISS) {
                    targetPos = result.getPos();
                }

                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.teleport(targetPos.x, targetPos.y, targetPos.z);
                player.fallDistance = 0;
                world.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                return true;
            }
        });

        // ABILITY 2: Gate Anchor (Dynamic Cost)
        this.addAbility(new Ability("Gate Anchor", 20, 10) {

            @Override
            public int getCost(PlayerEntity player) {
                // Sneaking (Set) = 5 Stamina
                // Standing (Warp) = 40 Stamina
                return player.isSneaking() ? 5 : 40;
            }

            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient) return false;
                if (!(player instanceof IQuirkData data)) return false;

                if (player.isSneaking()) {
                    // --- SET ANCHOR ---
                    data.setWarpAnchor(player.getPos(), world.getRegistryKey());

                    player.sendMessage(Text.literal("Gate Anchor Set").formatted(Formatting.LIGHT_PURPLE), true);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 1.0f);

                    // Return true -> Networking consumes 5 stamina
                    return true;
                } else {
                    // --- TELEPORT TO ANCHOR ---
                    Vec3d anchor = data.getWarpAnchorPos();
                    RegistryKey<World> dim = data.getWarpAnchorDim();

                    if (anchor == null || dim == null) {
                        player.sendMessage(Text.literal("No Anchor Set! (Crouch + Use to set)").formatted(Formatting.RED), true);
                        return false; // Return false -> No stamina consumed
                    }

                    if (!dim.equals(world.getRegistryKey())) {
                        player.sendMessage(Text.literal("Cannot warp across dimensions!").formatted(Formatting.RED), true);
                        return false;
                    }

                    // Removed Manual Check & Consumption
                    // Networking will check if we have 40 stamina, then consume 40 stamina.

                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    player.teleport(anchor.x, anchor.y, anchor.z);
                    player.fallDistance = 0;
                    world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                    return true;
                }
            }
        });
    }

    @Override
    public void onTick(PlayerEntity player) {
        super.onTick(player);

        if (player.getWorld().isClient) {
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