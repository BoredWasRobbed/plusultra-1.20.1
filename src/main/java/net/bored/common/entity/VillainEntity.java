package net.bored.common.entity;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.common.quirks.WarpGateQuirk;
import net.bored.registry.QuirkRegistry;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity; // Changed from HostileEntity
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// NOW EXTENDS ZOMBIE ENTITY to work with ZombieEntityRenderer
public class VillainEntity extends ZombieEntity implements IQuirkData {

    // QUIRK DATA STORAGE
    private Identifier quirkId;
    private boolean isAwakened;
    private int selectedSlot;
    private float stamina = 100.0f;
    private float maxStamina = 100.0f;
    private int[] cooldowns = new int[0];
    private int level = 1;
    private float xp = 0;
    private final List<Vec3d> anchorPositions = new ArrayList<>();
    private final List<RegistryKey<World>> anchorDimensions = new ArrayList<>();
    private int selectedAnchorIndex;
    private Vec3d portalOrigin;
    private int portalTimer;
    private int portalImmunity;
    private int placementState;
    private Vec3d placementOrigin;
    private Vec3d tempRiftA;
    private Vec3d riftA;
    private Vec3d riftB;
    private int riftTimer;
    private boolean regenActive;

    public VillainEntity(EntityType<? extends ZombieEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createVillainAttributes() {
        // Use Zombie attributes as base
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected boolean burnsInDaylight() {
        return false; // Prevent burning in sunlight
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new QuirkAttackGoal(this)); // Custom Quirk Goal
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(6, new LookAroundGoal(this));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        // Randomly assign a quirk on spawn
        if (this.random.nextBoolean()) {
            this.setQuirk(new Identifier("plusultra", "super_regeneration"));
            // Mobs activate regen immediately
            this.setRegenActive(true);
        } else {
            this.setQuirk(new Identifier("plusultra", "warp_gate"));
            // Mobs set an anchor at spawn so they can return/shoot
            this.addWarpAnchor(this.getPos(), world.toServerWorld().getRegistryKey());
        }
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    public void tick() {
        super.tick();
        // Regenerate Stamina
        if (this.stamina < this.maxStamina) {
            this.stamina = Math.min(this.stamina + 0.1f, this.maxStamina);
        }
        this.tickCooldowns();
        this.tickPortal();
        this.tickRift();

        Quirk q = getQuirk();
        if (q != null) q.onTick(this);
    }

    // --- QUIRK ATTACK AI ---
    static class QuirkAttackGoal extends Goal {
        private final VillainEntity mob;
        private int attackTimer = 0;

        public QuirkAttackGoal(VillainEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.mob.getTarget() != null && this.mob.hasQuirk();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;
            this.mob.getLookControl().lookAt(target, 30.0f, 30.0f);

            if (attackTimer > 0) {
                attackTimer--;
                return;
            }

            Quirk quirk = this.mob.getQuirk();
            if (quirk instanceof WarpGateQuirk) {
                // Try to use Warp Shot (Slot 3)
                this.mob.setSelectedSlot(3); // Index for Warp Shot
                Ability shot = quirk.getAbility(3);
                if (shot != null && this.mob.getStamina() >= shot.getCost(this.mob) && this.mob.getCooldown(3) <= 0) {
                    shot.onActivate(this.mob.getWorld(), this.mob);
                    this.attackTimer = 60; // Wait 3 seconds
                }
            }
            // Regen users just melee
        }
    }

    // --- IQuirkData Implementation ---
    @Override public Quirk getQuirk() { if (quirkId == null) return null; return QuirkRegistry.get(quirkId); }
    @Override public void setQuirk(Identifier id) {
        this.quirkId = id;
        if(getQuirk()!=null) this.cooldowns = new int[getQuirk().getAbilities().size()];
        else this.cooldowns = new int[0];
    }
    @Override public boolean hasQuirk() { return quirkId != null; }
    @Override public boolean isAwakened() { return isAwakened; }
    @Override public void setAwakened(boolean awakened) { this.isAwakened = awakened; }
    @Override public int getLevel() { return level; }
    @Override public void setLevel(int level) { this.level = level; }
    @Override public float getXp() { return xp; }
    @Override public void setXp(float xp) { this.xp = xp; }
    @Override public void addXp(float amount) { this.xp += amount; }
    @Override public float getMaxXp() { return 100 + (level*50); }
    @Override public int getSelectedSlot() { return selectedSlot; }
    @Override public void setSelectedSlot(int slot) { this.selectedSlot = slot; }
    @Override public void cycleSlot(int direction) { }
    @Override public float getStamina() { return stamina; }
    @Override public float getMaxStamina() { return maxStamina; }
    @Override public void setStamina(float stamina) { this.stamina = stamina; }
    @Override public void setMaxStamina(float max) { this.maxStamina = max; }
    @Override public void consumeStamina(float amount) { this.stamina -= amount; }
    @Override public int getCooldown(int slot) { if(slot < cooldowns.length) return cooldowns[slot]; return 0; }
    @Override public void setCooldown(int slot, int ticks) { if(slot < cooldowns.length) cooldowns[slot] = ticks; }
    @Override public void tickCooldowns() { for(int i=0; i<cooldowns.length; i++) if(cooldowns[i]>0) cooldowns[i]--; }
    @Override public void addWarpAnchor(Vec3d pos, RegistryKey<World> dimension) { anchorPositions.add(pos); anchorDimensions.add(dimension); }
    @Override public void removeWarpAnchor(int index) { }
    @Override public Vec3d getWarpAnchorPos(int index) { if(!anchorPositions.isEmpty()) return anchorPositions.get(0); return null; }
    @Override public RegistryKey<World> getWarpAnchorDim(int index) { if(!anchorDimensions.isEmpty()) return anchorDimensions.get(0); return null; }
    @Override public int getWarpAnchorCount() { return anchorPositions.size(); }
    @Override public int getSelectedAnchorIndex() { return 0; }
    @Override public void cycleSelectedAnchor(int direction) { }
    @Override public void setPortal(Vec3d origin, int ticks) { this.portalOrigin = origin; this.portalTimer = ticks; }
    @Override public Vec3d getPortalOrigin() { return portalOrigin; }
    @Override public int getPortalTimer() { return portalTimer; }
    @Override public void tickPortal() { if(portalTimer>0) portalTimer--; if(portalImmunity>0) portalImmunity--; }
    @Override public int getPortalImmunity() { return portalImmunity; }
    @Override public void setPortalImmunity(int ticks) { this.portalImmunity = ticks; }
    @Override public void setPlacementState(int state, Vec3d originalPos, GameMode originalMode) { this.placementState = state; this.placementOrigin = originalPos; }
    @Override public int getPlacementState() { return placementState; }
    @Override public Vec3d getPlacementOrigin() { return placementOrigin; }
    @Override public GameMode getOriginalGameMode() { return GameMode.SURVIVAL; }
    @Override public void setTempRiftA(Vec3d pos) { this.tempRiftA = pos; }
    @Override public Vec3d getTempRiftA() { return tempRiftA; }
    @Override public void setRift(Vec3d a, Vec3d b, int ticks) { this.riftA = a; this.riftB = b; this.riftTimer = ticks; }
    @Override public Vec3d getRiftA() { return riftA; }
    @Override public Vec3d getRiftB() { return riftB; }
    @Override public int getRiftTimer() { return riftTimer; }
    @Override public void tickRift() { if(riftTimer>0) riftTimer--; }
    @Override public boolean isRegenActive() { return regenActive; }
    @Override public void setRegenActive(boolean active) { this.regenActive = active; }
    @Override public void syncQuirkData() {
        // Mobs don't strictly need sync for now
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.quirkId != null) nbt.putString("QuirkId", this.quirkId.toString());
        nbt.putBoolean("RegenActive", this.regenActive);
        if (!anchorPositions.isEmpty()) {
            Vec3d pos = anchorPositions.get(0);
            nbt.putDouble("AnchorX", pos.x);
            nbt.putDouble("AnchorY", pos.y);
            nbt.putDouble("AnchorZ", pos.z);
            nbt.putString("AnchorDim", anchorDimensions.get(0).getValue().toString());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("QuirkId")) this.setQuirk(new Identifier(nbt.getString("QuirkId")));
        this.regenActive = nbt.getBoolean("RegenActive");
        if (nbt.contains("AnchorX")) {
            this.addWarpAnchor(new Vec3d(nbt.getDouble("AnchorX"), nbt.getDouble("AnchorY"), nbt.getDouble("AnchorZ")),
                    RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("AnchorDim"))));
        }
    }
}