package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.common.entity.WarpProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

import java.util.List;

public class WarpGateQuirk extends Quirk {

    public WarpGateQuirk() {
        super(new Identifier("plusultra", "warp_gate"), 0x2E003E);
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
            ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 100, 1.0, 1.0, 1.0, 0.5);
        }
    }

    @Override
    public void registerAbilities() {
        // 1. Gate Anchor
        this.addAbility(new Ability("Gate Anchor", 20, 10, 1) {
            @Override public int getCost(PlayerEntity player) { return player.isSneaking() ? 5 : 40; }
            @Override public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient || !(player instanceof IQuirkData data)) return false;
                if (player.isSneaking()) {
                    data.addWarpAnchor(player.getPos(), world.getRegistryKey());
                    player.sendMessage(Text.literal("Anchor Saved [" + data.getWarpAnchorCount() + "/5]").formatted(Formatting.LIGHT_PURPLE), true);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return true;
                } else {
                    int index = data.getSelectedAnchorIndex();
                    Vec3d anchor = data.getWarpAnchorPos(index);
                    RegistryKey<World> dim = data.getWarpAnchorDim(index);
                    if (anchor == null || dim == null || !dim.equals(world.getRegistryKey())) {
                        player.sendMessage(Text.literal("No Valid Anchor!").formatted(Formatting.RED), true);
                        return false;
                    }
                    Vec3d forward = player.getRotationVector().multiply(2.0);
                    Vec3d portalOrigin = player.getPos().add(forward.x, 1.0, forward.z);
                    data.setPortal(portalOrigin, 100);
                    world.playSound(null, portalOrigin.x, portalOrigin.y, portalOrigin.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return true;
                }
            }
        });

        // 2. Warp Mist
        this.addAbility(new Ability("Warp Mist", 40, 25, 5) {
            @Override public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient) return false;
                double range = 20.0;
                if (player instanceof IQuirkData data) {
                    range += (data.getLevel() * 2.0);
                    if (data.isAwakened()) range = 60.0;
                }
                Vec3d start = player.getEyePos();
                Vec3d direction = player.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));
                HitResult result = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                Vec3d targetPos = (result.getType() != HitResult.Type.MISS) ? result.getPos() : end;
                BlockPos targetBlock = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
                if (world.isAir(targetBlock) && world.isAir(targetBlock.down())) {
                    for (int i = 1; i < 20; i++) {
                        if (!world.isAir(targetBlock.down(i))) {
                            targetPos = new Vec3d(targetPos.x, targetPos.y - i + 1, targetPos.z);
                            break;
                        }
                    }
                }

                ServerWorld serverWorld = (ServerWorld) world;
                serverWorld.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 1.0, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 1.0, 0.5, 0.5);

                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.teleport(targetPos.x, targetPos.y, targetPos.z);
                player.fallDistance = 0;
                world.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                serverWorld.spawnParticles(ParticleTypes.SQUID_INK, targetPos.x, targetPos.y + 1, targetPos.z, 20, 0.5, 1.0, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1.0, 0.5, 0.5);

                return true;
            }
        });

        // 3. Dimensional Rift
        this.addAbility(new Ability("Dimensional Rift", 600, 50, 10) {
            // Allow activation during cooldown to CLOSE the rift
            @Override
            public boolean canUseWhileOnCooldown() {
                return true;
            }

            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient || !(player instanceof IQuirkData data)) return false;
                if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;

                // COOLDOWN CHECK / TOGGLE OFF LOGIC
                // If the Rift is active (Timer > 0), we close it. This is allowed even if on cooldown.
                if (data.getRiftTimer() > 0) {
                    data.setRift(null, null, 0);
                    player.sendMessage(Text.literal("Dimensional Rift Closed").formatted(Formatting.YELLOW), true);
                    // Return false so we don't trigger the standard cost/cooldown reset logic in Networking
                    return false;
                }

                // If it wasn't active, we are trying to OPEN it.
                // But wait! Since we bypassed the check in Networking, we must MANUALLY check if it's on cooldown here.
                if (data.getCooldown(data.getSelectedSlot()) > 0) {
                    return false; // It's on cooldown and we aren't closing it, so deny.
                }

                int state = data.getPlacementState();

                if (state == 0) {
                    if (data.getStamina() < 20) {
                        player.sendMessage(Text.literal("Not enough stamina to project!").formatted(Formatting.RED), true);
                        return false;
                    }

                    data.setPlacementState(1, player.getPos(), serverPlayer.interactionManager.getGameMode());
                    serverPlayer.changeGameMode(GameMode.SPECTATOR);
                    player.sendMessage(Text.literal("SPECTRAL MODE: Select 1st Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false;
                }
                else if (state == 1) {
                    data.setTempRiftA(player.getPos());
                    data.setPlacementState(2, data.getPlacementOrigin(), data.getOriginalGameMode());
                    player.sendMessage(Text.literal("Select 2nd Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false;
                }
                else if (state == 2) {
                    Vec3d posA = data.getTempRiftA();
                    Vec3d posB = player.getPos();
                    Vec3d origin = data.getPlacementOrigin();
                    GameMode mode = data.getOriginalGameMode();

                    if (origin != null) player.teleport(origin.x, origin.y, origin.z);
                    if (mode != null) serverPlayer.changeGameMode(mode);

                    data.setRift(posA, posB, 600);
                    data.setPlacementState(0, null, null);

                    data.consumeStamina(50);
                    data.setCooldown(data.getSelectedSlot(), 600);

                    player.sendMessage(Text.literal("Dimensional Rift Opened!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
                    world.playSound(null, posA.x, posA.y, posA.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);
                    world.playSound(null, posB.x, posB.y, posB.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);

                    return false;
                }
                return false;
            }
        });

        // 4. Warp Shot (UPDATED: PROJECTILE)
        this.addAbility(new Ability("Warp Shot", 100, 30, 5) {
            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient || !(player instanceof IQuirkData data)) return false;

                int index = data.getSelectedAnchorIndex();
                Vec3d anchor = data.getWarpAnchorPos(index);
                RegistryKey<World> dim = data.getWarpAnchorDim(index);

                if (anchor == null || dim == null || !dim.equals(world.getRegistryKey())) {
                    player.sendMessage(Text.literal("No Valid Anchor for Warp Shot!").formatted(Formatting.RED), true);
                    return false;
                }

                // Spawn Projectile
                WarpProjectileEntity projectile = new WarpProjectileEntity(world, player);
                projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.5F, 1.0F);
                projectile.setTargetAnchor(anchor);
                world.spawnEntity(projectile);

                // THROW SOUND: Ghast Shoot for "Beam/Shot" effect
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return true;
            }
        });
    }

    @Override
    public void onTick(PlayerEntity player) {
        super.onTick(player);

        if (!player.getWorld().isClient && player instanceof IQuirkData data) {
            ServerWorld world = (ServerWorld) player.getWorld();

            int pState = data.getPlacementState();
            if (pState == 1 || pState == 2) {
                if (data.getStamina() > 0) {
                    data.consumeStamina(0.25f);
                } else {
                    data.setPlacementState(0, null, null);
                    if (data.getOriginalGameMode() != null && player instanceof ServerPlayerEntity spe) {
                        spe.changeGameMode(data.getOriginalGameMode());
                    }
                    Vec3d origin = data.getPlacementOrigin();
                    if (origin != null) player.teleport(origin.x, origin.y, origin.z);
                    player.sendMessage(Text.literal("Exhausted! Projection cancelled.").formatted(Formatting.RED), true);
                    return;
                }
            }

            if (data.getPortalTimer() > 0) {
                Vec3d origin = data.getPortalOrigin();
                int index = data.getSelectedAnchorIndex();
                Vec3d target = data.getWarpAnchorPos(index);

                if (origin != null && target != null) {
                    world.spawnParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, origin.x, origin.y + 1, origin.z, 5, 0.5, 1.0, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, target.x, target.y + 1, target.z, 5, 0.5, 1.0, 0.5, 0.05);

                    if (data.getPortalImmunity() <= 0) {
                        if (player.squaredDistanceTo(origin) < 2.0) {
                            teleportEntity(player, target, world);
                            data.setPortalImmunity(40);
                        }
                        else if (player.squaredDistanceTo(target.add(0, 1, 0)) < 2.0) {
                            teleportEntity(player, origin, world);
                            data.setPortalImmunity(40);
                        }
                    }

                    handleEntityTeleport(world, origin, target);
                    handleEntityTeleport(world, target.add(0, 1, 0), origin);
                }
            }

            if (data.getRiftTimer() > 0) {
                Vec3d A = data.getRiftA();
                Vec3d B = data.getRiftB();
                if (A != null && B != null) {
                    world.spawnParticles(ParticleTypes.SQUID_INK, A.x, A.y + 1, A.z, 10, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, A.x, A.y + 1, A.z, 20, 0.3, 1.0, 0.3, 0.2);
                    world.spawnParticles(ParticleTypes.SQUID_INK, B.x, B.y + 1, B.z, 10, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, B.x, B.y + 1, B.z, 20, 0.3, 1.0, 0.3, 0.2);

                    if (data.getPortalImmunity() <= 0) {
                        if (player.squaredDistanceTo(A) < 3.0) {
                            teleportEntity(player, B, world);
                            data.setPortalImmunity(40);
                        } else if (player.squaredDistanceTo(B) < 3.0) {
                            teleportEntity(player, A, world);
                            data.setPortalImmunity(40);
                        }
                    }

                    handleEntityTeleport(world, A, B);
                    handleEntityTeleport(world, B, A);
                }
            }
        }
    }

    private void handleEntityTeleport(ServerWorld world, Vec3d source, Vec3d dest) {
        List<Entity> entities = world.getOtherEntities(null, new Box(source.add(-1, 0, -1), source.add(1, 2, 1)));

        for (Entity entity : entities) {
            if (entity instanceof PlayerEntity) continue;

            if (entity.getPortalCooldown() <= 0) {
                teleportEntity(entity, dest, world);
                entity.resetPortalCooldown();
                entity.setPortalCooldown(100);
            }
        }
    }

    private void teleportEntity(Entity entity, Vec3d dest, World world) {
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        entity.teleport(dest.x, dest.y, dest.z);
        entity.fallDistance = 0;
        world.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void onTickAwakened(PlayerEntity player) {
    }
}