package net.bored.common.entity;

import net.bored.PlusUltra;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class WarpProjectileEntity extends ThrownItemEntity {

    private Vec3d targetAnchor = null;

    public WarpProjectileEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
    }

    public WarpProjectileEntity(World world, LivingEntity owner) {
        super(PlusUltra.WARP_PROJECTILE, owner, world);
    }

    public void setTargetAnchor(Vec3d pos) {
        this.targetAnchor = pos;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            // BIGGER PARTICLES: Spawn multiple with spread to create a thick "slug" of energy
            for (int i = 0; i < 3; i++) {
                double dx = (this.random.nextDouble() - 0.5) * 0.3;
                double dy = (this.random.nextDouble() - 0.5) * 0.3;
                double dz = (this.random.nextDouble() - 0.5) * 0.3;
                this.getWorld().addParticle(ParticleTypes.SQUID_INK, this.getX() + dx, this.getY() + dy, this.getZ() + dz, 0, 0, 0);
                this.getWorld().addParticle(ParticleTypes.PORTAL, this.getX() + dx, this.getY() + dy, this.getZ() + dz, 0, 0, 0);
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        Entity target = entityHitResult.getEntity();

        if (!this.getWorld().isClient && this.targetAnchor != null) {
            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            // HIT VISUALS: Engulf the target in portal particles BEFORE teleporting
            // This sells the effect that a portal opened ON them.
            serverWorld.spawnParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.5, 1.0, 0.5, 0.1);
            serverWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 40, 0.5, 1.0, 0.5, 0.2);
            serverWorld.spawnParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);

            // Audio
            this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 2.0f);
            this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

            // Execute Teleport
            target.teleport(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            target.fallDistance = 0;

            // Destination Audio/Visuals
            this.getWorld().playSound(null, targetAnchor.x, targetAnchor.y, targetAnchor.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            this.discard();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (targetAnchor != null) {
            nbt.putDouble("TargetX", targetAnchor.x);
            nbt.putDouble("TargetY", targetAnchor.y);
            nbt.putDouble("TargetZ", targetAnchor.z);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("TargetX")) {
            this.targetAnchor = new Vec3d(nbt.getDouble("TargetX"), nbt.getDouble("TargetY"), nbt.getDouble("TargetZ"));
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}