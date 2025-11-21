package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

public class OverhaulQuirk extends Quirk {

    public OverhaulQuirk() {
        super(new Identifier("plusultra", "overhaul"), 0x800000); // Dark Maroon Color
    }

    @Override
    public void registerAbilities() {
        // Move 1: Deconstruct (Level 1) - CHARGEABLE
        this.addAbility(new Ability("Deconstruct", 20, 15, 1) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                // 1. Retrieve State
                int charge = data.getChargeTime();
                int level = data.getLevel();
                Vec3d anchorPos = data.getPlacementOrigin();

                // 2. Reset State
                data.setChargeTime(0);
                data.setPlacementState(0, null, null);

                // 3. Calculate Parameters
                float baseDamage = 12.0f;
                float breakRadius = 0.0f;

                // Scaling: Cap charge based on level
                int maxCharge = Math.min(125, 20 + (level * 2));
                int effectiveCharge = Math.min(charge, maxCharge);

                if (effectiveCharge > 0) {
                    // AGGRESSIVE SCALING:
                    // / 3.0f means 15 ticks (0.75s) gives 5 block radius
                    breakRadius = (effectiveCharge / 3.0f);
                    baseDamage += (effectiveCharge / 5.0f);
                }

                // DROPS LOGIC: Drop if low charge (< 20), No drops if high charge (Vaporize)
                boolean doDrops = effectiveCharge < 20;

                // Determine Center Point
                BlockPos center = null;

                // PATH A: ANCHORED (Prioritized)
                if (anchorPos != null) {
                    // RE-CHECK DISTANCE (Sanity check, though onTick handles it too)
                    if (user.squaredDistanceTo(anchorPos) <= 9.0) { // 3 blocks squared
                        center = new BlockPos((int)Math.floor(anchorPos.x), (int)Math.floor(anchorPos.y), (int)Math.floor(anchorPos.z));
                    } else {
                        // If too far, fall back to raycast or cancel?
                        // Let's fall back to raycast logic below (effectively losing the anchor)
                        anchorPos = null;
                    }
                }

                // PATH B: RAYCAST (If no anchor or anchor invalid)
                if (center == null) {
                    double range = 4.0 + (effectiveCharge / 5.0f);
                    Vec3d start = user.getEyePos();
                    Vec3d direction = user.getRotationVector();
                    Vec3d end = start.add(direction.multiply(range));

                    // Check Entities first (for direct hit damage)
                    Box box = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
                    for (Entity e : world.getOtherEntities(user, box)) {
                        if (e.getBoundingBox().intersects(start, end)) {
                            e.damage(world.getDamageSources().magic(), baseDamage);
                            ((ServerWorld) world).spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                            return true; // Direct entity hit consumes ability
                        }
                    }

                    BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, user));
                    if (blockHit.getType() == HitResult.Type.BLOCK) {
                        center = blockHit.getBlockPos();
                    }
                }

                // Execute Destruction at Center
                if (center != null) {
                    boolean brokeAnything = false;

                    if (breakRadius < 0.5f) {
                        // Single Block Break
                        if (breakBlockCheck(world, center, user, doDrops)) {
                            brokeAnything = true;
                        }
                    } else {
                        // AOE Break
                        int r = (int) Math.ceil(breakRadius);
                        float rSq = breakRadius * breakRadius;

                        for (int x = -r; x <= r; x++) {
                            for (int y = -r; y <= r; y++) {
                                for (int z = -r; z <= r; z++) {
                                    BlockPos p = center.add(x, y, z);
                                    if (p.getSquaredDistance(center) <= rSq) {
                                        if (breakBlockCheck(world, p, user, doDrops)) {
                                            brokeAnything = true;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AOE Damage around Center
                    Box damageBox = new Box(center).expand(breakRadius + 2.0);
                    for (Entity e : world.getOtherEntities(user, damageBox)) {
                        e.damage(world.getDamageSources().magic(), baseDamage);
                        ((ServerWorld) world).spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                    }

                    if (brokeAnything) {
                        world.playSound(null, center, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.PLAYERS, 1.0f, 1.5f);
                        return true;
                    }
                    return true; // Consumes stamina even if missed slightly (whiff)
                }

                return false;
            }
        });

        // Move 2: Reconstruct (Level 10) - REPAIR BLOCKS
        this.addAbility(new Ability("Reconstruct", 10, 10, 10) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient) return true;

                double range = 5.0;
                Vec3d start = user.getEyePos();
                Vec3d direction = user.getRotationVector();
                Vec3d end = start.add(direction.multiply(range));

                Box box = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
                LivingEntity target = null;
                for (Entity e : world.getOtherEntities(user, box)) {
                    if (e instanceof LivingEntity le && e.getBoundingBox().intersects(start, end)) {
                        target = le;
                        break;
                    }
                }

                LivingEntity healTarget = (target != null) ? target : user;

                if (target != null || (target == null && healTarget.getHealth() < healTarget.getMaxHealth())) {
                    if (healTarget.getHealth() < healTarget.getMaxHealth()) {
                        healTarget.heal(4.0f);
                        world.playSound(null, healTarget.getX(), healTarget.getY(), healTarget.getZ(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.5f, 2.0f);
                        ((ServerWorld) world).spawnParticles(ParticleTypes.HAPPY_VILLAGER, healTarget.getX(), healTarget.getBodyY(0.5), healTarget.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                        return true;
                    }
                }

                BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = blockHit.getBlockPos();
                    BlockState state = world.getBlockState(pos);
                    Block newBlock = null;

                    if (state.isOf(Blocks.COBBLESTONE)) newBlock = Blocks.STONE;
                    else if (state.isOf(Blocks.DIRT)) newBlock = Blocks.GRASS_BLOCK;
                    else if (state.isOf(Blocks.GRAVEL)) newBlock = Blocks.STONE;
                    else if (state.isOf(Blocks.SAND)) newBlock = Blocks.SANDSTONE;
                    else if (state.isOf(Blocks.CRACKED_STONE_BRICKS)) newBlock = Blocks.STONE_BRICKS;
                    else if (state.isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)) newBlock = Blocks.POLISHED_BLACKSTONE_BRICKS;
                    else if (state.isOf(Blocks.CRACKED_NETHER_BRICKS)) newBlock = Blocks.NETHER_BRICKS;

                    if (newBlock != null) {
                        world.setBlockState(pos, newBlock.getDefaultState());
                        world.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1.5f);
                        ((ServerWorld) world).spawnParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.0);
                        return true;
                    }
                }

                return false;
            }
        });

        // Move 3: Earthen Spikes
        this.addAbility(new Ability("Earthen Spikes", 40, 25, 25) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient) return true;

                Vec3d dir = user.getRotationVector().multiply(1, 0, 1).normalize();
                Vec3d pos = user.getPos();

                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 1.0f, 0.5f);

                for (int i = 1; i <= 10; i++) {
                    Vec3d targetPos = pos.add(dir.multiply(i));

                    ((ServerWorld) world).spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()), targetPos.x, targetPos.y, targetPos.z, 10, 0.5, 0.5, 0.5, 0.1);
                    ((ServerWorld) world).spawnParticles(ParticleTypes.SWEEP_ATTACK, targetPos.x, targetPos.y + 0.5, targetPos.z, 1, 0, 0, 0, 0);

                    Box damageBox = new Box(targetPos.add(-1, 0, -1), targetPos.add(1, 2, 1));
                    List<Entity> entities = world.getOtherEntities(user, damageBox);
                    for (Entity e : entities) {
                        e.damage(world.getDamageSources().magic(), 6.0f);
                        e.addVelocity(0, 0.5, 0);
                    }
                }
                return true;
            }
        });

        // Move 4: Fusion - SPHERICAL DESTRUCTION + RELEASE ON END
        this.addAbility(new Ability("Fusion", 20, 0, 40) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                boolean newState = !data.isRegenActive();
                data.setRegenActive(newState);

                if (newState) {
                    if (user instanceof PlayerEntity p) p.sendMessage(Text.literal("Fusion Active").formatted(Formatting.RED), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0f, 1.0f);

                    // ABSORB SURROUNDINGS
                    if (!world.isClient) {
                        BlockPos center = user.getBlockPos();
                        int radius = 3;
                        int rSq = radius * radius; // Squared radius for spherical check

                        for (int x = -radius; x <= radius; x++) {
                            for (int y = -radius; y <= radius; y++) {
                                for (int z = -radius; z <= radius; z++) {
                                    // SPHERICAL CHECK
                                    if (x*x + y*y + z*z <= rSq) {
                                        BlockPos p = center.add(x, y, z);
                                        BlockState state = world.getBlockState(p);
                                        if (!state.isAir() && state.getHardness(world, p) >= 0) {
                                            // Capture Drops
                                            ItemStack tool = user instanceof PlayerEntity player ? player.getMainHandStack() : ItemStack.EMPTY;
                                            BlockEntity blockEntity = world.getBlockEntity(p);
                                            List<ItemStack> drops = Block.getDroppedStacks(state, (ServerWorld)world, p, blockEntity, user, tool);

                                            for (ItemStack drop : drops) {
                                                data.addFusionItem(drop);
                                            }

                                            // Destroy without dropping (absorbed)
                                            world.breakBlock(p, false, user);
                                            ((ServerWorld)world).spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.1);
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else {
                    if (user instanceof PlayerEntity p) p.sendMessage(Text.literal("Fusion Inactive").formatted(Formatting.GRAY), true);

                    // RELEASE ITEMS
                    if (!world.isClient) {
                        for (ItemStack stack : data.getFusionItems()) {
                            Block.dropStack(world, user.getBlockPos(), stack);
                        }
                        data.clearFusionItems();
                        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0f, 0.5f);
                    }
                }
                return true;
            }
        });

        // Move 5: Catastrophe
        this.addAbility(new Ability("Catastrophe", 300, 60, 60) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient) return true;

                double radius = 10.0;
                Box area = user.getBoundingBox().expand(radius);

                List<Entity> entities = world.getOtherEntities(user, area);
                for (Entity e : entities) {
                    e.damage(world.getDamageSources().magic(), 20.0f);
                    if (e instanceof LivingEntity le) {
                        le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
                        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2));
                    }
                }

                ((ServerWorld) world).spawnParticles(ParticleTypes.EXPLOSION, user.getX(), user.getY() + 1, user.getZ(), 5, 2, 2, 2, 0.1);
                ((ServerWorld) world).spawnParticles(ParticleTypes.SQUID_INK, user.getX(), user.getY() + 1, user.getZ(), 100, 8, 2, 8, 0.1);

                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.5f);

                return true;
            }
        });
    }

    // Helper for robust block breaking (Fluids + Solids) with drop toggle
    private boolean breakBlockCheck(World world, BlockPos pos, LivingEntity user, boolean drop) {
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && state.getHardness(world, pos) >= 0) {
            if (state.getBlock() instanceof FluidBlock || !state.getFluidState().isEmpty()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                return true;
            } else {
                // Explicitly force drop if requested, passing the player's tool to ensure correct loot
                if (drop && world instanceof ServerWorld sw) {
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    // FIX: Use the player's main hand tool so stone/ores/etc drop correctly
                    ItemStack tool = user instanceof PlayerEntity p ? p.getMainHandStack() : ItemStack.EMPTY;
                    Block.getDroppedStacks(state, sw, pos, blockEntity, user, tool).forEach(stack -> {
                        Block.dropStack(world, pos, stack);
                    });
                    state.onStacksDropped(sw, pos, ItemStack.EMPTY, true);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                } else {
                    // Standard break (no drops or drops handled internally if not overridden above)
                    world.breakBlock(pos, false, user);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick(LivingEntity user) {
        super.onTick(user);
        if (!(user instanceof IQuirkData data)) return;

        // 1. Deconstruct Charging Logic - Runs on BOTH Client and Server now for HUD sync
        if (data.getSelectedSlot() == 0 && data.isCharging()) {
            int level = data.getLevel();
            int maxCharge = Math.min(125, 20 + (level * 2));

            if (data.getChargeTime() < maxCharge) {
                data.incrementChargeTime();

                // Server-Side Visuals & Targeting (No need to run on Client)
                if (!user.getWorld().isClient) {
                    // TICK 1: LOCK ON TARGET (Anchor)
                    if (data.getChargeTime() == 1) {
                        double range = 6.0;
                        Vec3d start = user.getEyePos();
                        Vec3d dir = user.getRotationVector();
                        Vec3d end = start.add(dir.multiply(range));

                        BlockHitResult blockHit = user.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, user));
                        if (blockHit.getType() == HitResult.Type.BLOCK) {
                            Vec3d center = blockHit.getBlockPos().toCenterPos();
                            data.setPlacementState(0, center, null);
                        } else {
                            data.setPlacementState(0, null, null);
                        }
                    }

                    // DISTANCE CHECK: Unselect if too far (3 blocks)
                    Vec3d anchor = data.getPlacementOrigin();
                    if (anchor != null) {
                        if (user.squaredDistanceTo(anchor) > 9.0) { // 3 blocks squared
                            data.setPlacementState(0, null, null);
                            // Optional: Play a "detach" sound
                            user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_TRIPWIRE_DETACH, SoundCategory.PLAYERS, 0.5f, 1.5f);
                        } else if (user.age % 3 == 0) {
                            ((ServerWorld) user.getWorld()).spawnParticles(ParticleTypes.CRIT, anchor.x, anchor.y, anchor.z, 2, 0.3, 0.3, 0.3, 0.0);
                            user.getWorld().playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.2f, 2.0f);
                        }
                    }
                }
            }
        }

        // Stop here if Client (Server only logic below)
        if (user.getWorld().isClient) return;

        // 2. Fusion Buff Logic
        if (data.isRegenActive() && data.getQuirk().getId().toString().equals("plusultra:overhaul")) {
            if (data.getStamina() > 0.5f) {
                data.consumeStamina(0.5f);
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10, 1, false, false));
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 1, false, false));
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 10, 0, false, false));

                if (user.age % 10 == 0) {
                    ((ServerWorld) user.getWorld()).spawnParticles(ParticleTypes.DAMAGE_INDICATOR, user.getX(), user.getBodyY(0.5), user.getZ(), 1, 0.5, 0.5, 0.5, 0);
                }
            } else {
                // Stamina Depleted: Release Items
                if (!user.getWorld().isClient) {
                    for (ItemStack stack : data.getFusionItems()) {
                        Block.dropStack(user.getWorld(), user.getBlockPos(), stack);
                    }
                    data.clearFusionItems();
                    user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0f, 0.5f);
                }
                data.setRegenActive(false);
                if (user instanceof PlayerEntity p) p.sendMessage(Text.literal("Stamina Depleted! Fusion ended.").formatted(Formatting.RED), true);
            }
        }
    }
}