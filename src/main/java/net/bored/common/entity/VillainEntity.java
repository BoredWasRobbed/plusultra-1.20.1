package net.bored.common.entity;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.common.quirks.SuperRegenerationQuirk;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.registry.QuirkRegistry;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class VillainEntity extends ZombieEntity {

    public VillainEntity(EntityType<? extends ZombieEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createVillainAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected boolean burnsInDaylight() {
        return false;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new QuirkAttackGoal(this));
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(6, new LookAroundGoal(this));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        if (this instanceof IQuirkData quirkData) {
            // Give BOTH quirks to storage sometimes to test multi-quirk AI
            if (this.random.nextFloat() < 0.3f) {
                quirkData.addStolenQuirk("plusultra:super_regeneration");
                quirkData.addStolenQuirk("plusultra:warp_gate");
                quirkData.setQuirk(new Identifier("plusultra:warp_gate")); // Set initial active
                quirkData.addWarpAnchor(this.getPos(), world.toServerWorld().getRegistryKey());
            }
            else if (this.random.nextBoolean()) {
                quirkData.setQuirk(new Identifier("plusultra", "super_regeneration"));
                quirkData.setRegenActive(true);
            } else {
                quirkData.setQuirk(new Identifier("plusultra", "warp_gate"));
                quirkData.addWarpAnchor(this.getPos(), world.toServerWorld().getRegistryKey());
            }
        }
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && this instanceof IQuirkData data) {
            // 1. Ensure Warp Anchor exists if we possess Warp Gate (even in storage)
            if (data.getStolenQuirks().contains("plusultra:warp_gate") && data.getWarpAnchorCount() == 0) {
                data.addWarpAnchor(this.getPos(), this.getWorld().getRegistryKey());
            }

            // 2. Check for Passives to Activate (like Regen)
            if (data.getStolenQuirks().contains("plusultra:super_regeneration")) {
                // If not active and we are hurt, try to toggle it on
                if (!data.isRegenActive() && this.getHealth() < this.getMaxHealth() && data.getStamina() > 20) {
                    // We can toggle it by adding to active passives directly
                    data.togglePassive("plusultra:super_regeneration");
                    // Also set the flag for legacy support
                    data.setRegenActive(true);
                }
            }
        }
    }

    static class QuirkAttackGoal extends Goal {
        private final VillainEntity mob;
        private int attackTimer = 0;
        private int globalCooldown = 0;

        public QuirkAttackGoal(VillainEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            // Start if we have a target and ANY quirks
            if (this.mob.getTarget() == null) return false;
            if (this.mob instanceof IQuirkData data) {
                return data.hasQuirk() || !data.getStolenQuirks().isEmpty();
            }
            return false;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;
            this.mob.getLookControl().lookAt(target, 30.0f, 30.0f);

            if (attackTimer > 0) attackTimer--;
            if (globalCooldown > 0) globalCooldown--;

            if (this.mob instanceof IQuirkData data) {
                // Try to use the CURRENT active quirk first
                if (tryUseQuirk(data, data.getQuirk(), target)) return;

                // If current quirk didn't fire, try switching to another one in storage
                if (globalCooldown <= 0) {
                    for (String quirkId : data.getStolenQuirks()) {
                        // Skip if already active
                        if (data.getQuirk() != null && data.getQuirk().getId().toString().equals(quirkId)) continue;

                        // Peek at the quirk to see if it's useful
                        Quirk potentialQuirk = QuirkRegistry.get(new Identifier(quirkId));
                        if (potentialQuirk != null) {
                            // Force Switch
                            data.setQuirk(potentialQuirk.getId());
                            this.globalCooldown = 20; // 1 second delay between swaps

                            // Try to use it immediately
                            if (tryUseQuirk(data, potentialQuirk, target)) return;
                        }
                    }
                }
            }
        }

        private boolean tryUseQuirk(IQuirkData data, Quirk quirk, LivingEntity target) {
            if (quirk instanceof WarpGateQuirk) {
                double distSq = this.mob.squaredDistanceTo(target);
                // Warp Shot Range
                if (distSq > 16.0 && distSq < 900.0 && this.mob.canSee(target)) {
                    if (attackTimer <= 0) {
                        data.setSelectedSlot(3); // Warp Shot Slot
                        Ability shot = quirk.getAbility(3);
                        // Note: data.getCooldown(3) resets on swap, so mobs are deadly.
                        // Added attackTimer to throttle specifically this attack.
                        if (shot != null && data.getStamina() >= shot.getCost(this.mob)) {
                            shot.onActivate(this.mob.getWorld(), this.mob);
                            this.attackTimer = 60;
                            return true;
                        }
                    }
                }
            }
            // Add logic for other offensive quirks here
            return false;
        }
    }
}