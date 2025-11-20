package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

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
            ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 100, 1.0, 1.0, 1.0, 0.5);
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
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.teleport(targetPos.x, targetPos.y, targetPos.z);
                player.fallDistance = 0;
                world.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return true;
            }
        });

        // 3. Dimensional Rift (MANUAL RESOURCE HANDLING)
        this.addAbility(new Ability("Dimensional Rift", 600, 50, 10) {
            @Override public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient || !(player instanceof IQuirkData data)) return false;
                if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;

                int state = data.getPlacementState();

                // Note: returning FALSE prevents the generic networking from applying cooldowns/stamina.
                // We must handle them manually here.

                if (state == 0) {
                    // CHECK RESOURCES START
                    if (data.getCooldown(data.getSelectedSlot()) > 0) return false;
                    if (data.getStamina() < 50) {
                        player.sendMessage(Text.literal("Not enough stamina!").formatted(Formatting.RED), true);
                        return false;
                    }

                    // START: Save Pos, Switch to Spectator
                    data.setPlacementState(1, player.getPos(), serverPlayer.interactionManager.getGameMode());
                    serverPlayer.changeGameMode(GameMode.SPECTATOR);
                    player.sendMessage(Text.literal("SPECTRAL MODE: Select 1st Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false; // Return false to keep inputs open
                }
                else if (state == 1) {
                    // FIRST POINT SELECTED
                    data.setTempRiftA(player.getPos());
                    data.setPlacementState(2, data.getPlacementOrigin(), data.getOriginalGameMode());
                    player.sendMessage(Text.literal("Select 2nd Point (Press Z)").formatted(Formatting.AQUA), true);
                    return false;
                }
                else if (state == 2) {
                    // SECOND POINT SELECTED -> FINALIZE
                    Vec3d posA = data.getTempRiftA();
                    Vec3d posB = player.getPos();
                    Vec3d origin = data.getPlacementOrigin();
                    GameMode mode = data.getOriginalGameMode();

                    // Teleport back and restore gamemode
                    if (origin != null) player.teleport(origin.x, origin.y, origin.z);
                    if (mode != null) serverPlayer.changeGameMode(mode);

                    // Create Rift
                    data.setRift(posA, posB, 600);
                    data.setPlacementState(0, null, null);

                    // APPLY COSTS NOW (At the end)
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
    }

    @Override
    public void onTick(PlayerEntity player) {
        super.onTick(player);
        if (player.getWorld().isClient) {
            if (player.getRandom().nextFloat() < 0.1f) {
                player.getWorld().addParticle(ParticleTypes.PORTAL, player.getX() + (player.getRandom().nextDouble() - 0.5), player.getY() + 1.5, player.getZ() + (player.getRandom().nextDouble() - 0.5), 0, -0.5, 0);
            }
        }

        if (!player.getWorld().isClient && player instanceof IQuirkData data) {
            // ANCHOR PORTAL LOGIC
            if (data.getPortalTimer() > 0) {
                Vec3d origin = data.getPortalOrigin();
                int index = data.getSelectedAnchorIndex();
                Vec3d target = data.getWarpAnchorPos(index);

                if (origin != null && target != null) {
                    ServerWorld world = (ServerWorld) player.getWorld();
                    // Dense particles
                    world.spawnParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, origin.x, origin.y + 1, origin.z, 5, 0.5, 1.0, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 25, 0.5, 1.0, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.SQUID_INK, target.x, target.y + 1, target.z, 5, 0.5, 1.0, 0.5, 0.05);

                    if (data.getPortalImmunity() <= 0) {
                        if (player.squaredDistanceTo(origin) < 2.0) {
                            player.teleport(target.x, target.y, target.z);
                            player.fallDistance = 0;
                            data.setPortalImmunity(40);
                            world.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        }
                        else if (player.squaredDistanceTo(target.add(0, 1, 0)) < 2.0) {
                            player.teleport(origin.x, origin.y, origin.z);
                            player.fallDistance = 0;
                            data.setPortalImmunity(40);
                            world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        }
                    }
                }
            }

            // DIMENSIONAL RIFT LOGIC
            if (data.getRiftTimer() > 0) {
                Vec3d A = data.getRiftA();
                Vec3d B = data.getRiftB();
                if (A != null && B != null) {
                    ServerWorld world = (ServerWorld) player.getWorld();
                    world.spawnParticles(ParticleTypes.DRAGON_BREATH, A.x, A.y, A.z, 20, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, A.x, A.y + 1, A.z, 40, 0.3, 1.0, 0.3, 0.2);
                    world.spawnParticles(ParticleTypes.DRAGON_BREATH, B.x, B.y, B.z, 20, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticles(ParticleTypes.PORTAL, B.x, B.y + 1, B.z, 40, 0.3, 1.0, 0.3, 0.2);

                    if (data.getPortalImmunity() <= 0) {
                        if (player.squaredDistanceTo(A) < 3.0) {
                            player.teleport(B.x, B.y, B.z);
                            player.fallDistance = 0;
                            data.setPortalImmunity(40);
                            world.playSound(null, B.x, B.y, B.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                        } else if (player.squaredDistanceTo(B) < 3.0) {
                            player.teleport(A.x, A.y, A.z);
                            player.fallDistance = 0;
                            data.setPortalImmunity(40);
                            world.playSound(null, A.x, A.y, A.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onTickAwakened(PlayerEntity player) {
        if (player.getWorld().isClient) {
            for (int i = 0; i < 3; i++) player.getWorld().addParticle(ParticleTypes.SQUID_INK, player.getX() + (player.getRandom().nextDouble() - 0.5) * 2, player.getY() + 0.5, player.getZ() + (player.getRandom().nextDouble() - 0.5) * 2, 0, 0.1, 0);
        }
    }
}