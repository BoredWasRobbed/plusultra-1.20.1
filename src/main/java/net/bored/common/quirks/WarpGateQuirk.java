package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.common.entity.WarpProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
        this.setAwakeningCondition((user, data) -> user.getHealth() < 8.0f);
    }

    @Override
    public void onAwaken(LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            player.sendMessage(Text.literal("WARP GATE AWAKENED: MIST BODY ACTIVE").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
        }
        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 2.0f);
        if (!user.getWorld().isClient) {
            ((ServerWorld) user.getWorld()).spawnParticles(ParticleTypes.SQUID_INK, user.getX(), user.getY() + 1, user.getZ(), 100, 1.0, 1.0, 1.0, 0.5);
        }
    }

    @Override
    public void registerAbilities() {
        // 1. Gate Anchor
        this.addAbility(new Ability("Gate Anchor", 20, 10, 1) {
            @Override public int getCost(LivingEntity user) { return user.isSneaking() ? 5 : 40; }
            @Override public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                if (user.isSneaking()) {
                    data.addWarpAnchor(user.getPos(), world.getRegistryKey());
                    if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("Anchor Saved [" + data.getWarpAnchorCount() + "/5]").formatted(Formatting.LIGHT_PURPLE), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return true;
                } else {
                    int index = data.getSelectedAnchorIndex();
                    Vec3d anchor = data.getWarpAnchorPos(index);
                    RegistryKey<World> dim = data.getWarpAnchorDim(index);

                    if (anchor == null || dim == null || !dim.equals(world.getRegistryKey())) {
                        if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("No Valid Anchor!").formatted(Formatting.RED), true);
                        return false;
                    }
                    Vec3d forward = user.getRotationVector().multiply(2.0);
                    Vec3d portalOrigin = user.getPos().add(forward.x, 1.0, forward.z);
                    data.setPortal(portalOrigin, 100);
                    world.playSound(null, portalOrigin.x, portalOrigin.y, portalOrigin.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return true;
                }
            }
        });

        // 2. Warp Mist
        this.addAbility(new Ability("Warp Mist", 40, 25, 5) {
            @Override public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient) return false;
                double range = 20.0;
                if (user instanceof IQuirkData data) {
                    range += (data.getLevel() * 2.0);
                    if (data.isAwakened()) range = 60.0;
                }
                Vec3d start = user.getEyePos();
                Vec3d direction = user.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));
                HitResult result = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
                Vec3d targetPos = (result.getType() != HitResult.Type.MISS) ? result.getPos() : end;

                BlockPos targetBlock = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
                // Simple ground check fallback
                if (world.isAir(targetBlock) && world.isAir(targetBlock.down())) {
                    // Basic logic: just try to go down until ground
                    // (Simplified for brevity)
                }

                ServerWorld serverWorld = (ServerWorld) world;
                serverWorld.spawnParticles(ParticleTypes.SQUID_INK, user.getX(), user.getY() + 1, user.getZ(), 20, 0.5, 1.0, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.PORTAL, user.getX(), user.getY() + 1, user.getZ(), 30, 0.5, 1.0, 0.5, 0.5);

                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                user.teleport(targetPos.x, targetPos.y, targetPos.z);
                user.fallDistance = 0;
                world.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

                serverWorld.spawnParticles(ParticleTypes.SQUID_INK, targetPos.x, targetPos.y + 1, targetPos.z, 20, 0.5, 1.0, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 1, targetPos.z, 30, 0.5, 1.0, 0.5, 0.5);

                return true;
            }
        });

        // 3. Dimensional Rift
        this.addAbility(new Ability("Dimensional Rift", 600, 50, 10) {
            @Override
            public boolean canUseWhileOnCooldown() { return true; }

            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                // NOTE: Mobs cannot use the Spectator mode selection logic.
                if (!(user instanceof ServerPlayerEntity serverPlayer)) {
                    // AI LOGIC: Mobs just spawn a rift on their target if they have one?
                    // For now, we disable this ability for non-players to prevent crashes.
                    return false;
                }

                if (data.getRiftTimer() > 0) {
                    data.setRift(null, null, 0);
                    serverPlayer.sendMessage(Text.literal("Dimensional Rift Closed").formatted(Formatting.YELLOW), true);
                    return false;
                }

                if (data.getCooldown(data.getSelectedSlot()) > 0) return false;

                int state = data.getPlacementState();

                if (state == 0) {
                    if (data.getStamina() < 20) {
                        serverPlayer.sendMessage(Text.literal("Not enough stamina to project!").formatted(Formatting.RED), true);
                        return false;
                    }
                    data.setPlacementState(1, user.getPos(), serverPlayer.interactionManager.getGameMode());
                    serverPlayer.changeGameMode(GameMode.SPECTATOR);
                    serverPlayer.sendMessage(Text.literal("SPECTRAL MODE: Select 1st Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false;
                }
                else if (state == 1) {
                    data.setTempRiftA(user.getPos());
                    data.setPlacementState(2, data.getPlacementOrigin(), data.getOriginalGameMode());
                    serverPlayer.sendMessage(Text.literal("Select 2nd Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false;
                }
                else if (state == 2) {
                    Vec3d posA = data.getTempRiftA();
                    Vec3d posB = user.getPos();
                    Vec3d origin = data.getPlacementOrigin();
                    GameMode mode = data.getOriginalGameMode();

                    if (origin != null) user.teleport(origin.x, origin.y, origin.z);
                    if (mode != null) serverPlayer.changeGameMode(mode);

                    data.setRift(posA, posB, 600);
                    data.setPlacementState(0, null, null);

                    data.consumeStamina(50);
                    data.setCooldown(data.getSelectedSlot(), 600);

                    serverPlayer.sendMessage(Text.literal("Dimensional Rift Opened!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
                    world.playSound(null, posA.x, posA.y, posA.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);
                    world.playSound(null, posB.x, posB.y, posB.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);

                    return false;
                }
                return false;
            }
        });

        // 4. Warp Shot
        this.addAbility(new Ability("Warp Shot", 100, 30, 5) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                // Mob Logic: if no anchor is set, maybe set one at current location automatically?
                // For now, we enforce standard anchor logic. Mobs need to use "Gate Anchor" first.
                int index = data.getSelectedAnchorIndex();
                Vec3d anchor = data.getWarpAnchorPos(index);
                RegistryKey<World> dim = data.getWarpAnchorDim(index);

                if (anchor == null || dim == null || !dim.equals(world.getRegistryKey())) {
                    if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("No Valid Anchor for Warp Shot!").formatted(Formatting.RED), true);
                    return false;
                }

                WarpProjectileEntity projectile = new WarpProjectileEntity(world, user);
                projectile.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 2.5F, 1.0F);
                projectile.setTargetAnchor(anchor);
                world.spawnEntity(projectile);

                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return true;
            }
        });
    }

    @Override
    public void onTick(LivingEntity user) {
        super.onTick(user);

        if (!user.getWorld().isClient && user instanceof IQuirkData data) {
            ServerWorld world = (ServerWorld) user.getWorld();

            // Rift Cancellation Logic (Player only)
            int pState = data.getPlacementState();
            if (pState == 1 || pState == 2) {
                if (data.getStamina() > 0) {
                    data.consumeStamina(0.25f);
                } else {
                    data.setPlacementState(0, null, null);
                    if (data.getOriginalGameMode() != null && user instanceof ServerPlayerEntity spe) {
                        spe.changeGameMode(data.getOriginalGameMode());
                    }
                    Vec3d origin = data.getPlacementOrigin();
                    if (origin != null) user.teleport(origin.x, origin.y, origin.z);
                    if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("Exhausted! Projection cancelled.").formatted(Formatting.RED), true);
                    return;
                }
            }

            // Portal Particle/Tick Logic
            if (data.getPortalTimer() > 0) {
                Vec3d origin = data.getPortalOrigin();
                int index = data.getSelectedAnchorIndex();
                Vec3d target = data.getWarpAnchorPos(index);

                if (origin != null && target != null) {
                    world.spawnParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, origin.x, origin.y + 1, origin.z, 5, 0.5, 1.0, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, target.x, target.y + 1, target.z, 5, 0.5, 1.0, 0.5, 0.05);

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

                    handleEntityTeleport(world, A, B);
                    handleEntityTeleport(world, B, A);
                }
            }
        }
    }

    private void handleEntityTeleport(ServerWorld world, Vec3d source, Vec3d dest) {
        List<Entity> entities = world.getOtherEntities(null, new Box(source.add(-1, 0, -1), source.add(1, 3, 1)));

        for (Entity entity : entities) {
            if (entity instanceof IQuirkData data) {
                if (data.getPortalImmunity() <= 0) {
                    teleportEntity(entity, dest, world);
                    data.setPortalImmunity(40);
                }
            }
            else if (entity.getPortalCooldown() <= 0) {
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
}