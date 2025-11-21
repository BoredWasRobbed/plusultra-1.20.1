package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class PropulsionQuirk extends Quirk {

    private static final Vector3f RING_COLOR = new Vector3f(1.0f, 0.9f, 0.0f);

    public PropulsionQuirk() {
        super(new Identifier("plusultra", "propulsion"), 0xFFFF00);
    }

    @Override
    public void registerAwakening() {
        this.setAwakeningCondition((user, data) -> data.getLevel() >= 30);
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Propel
        this.addAbility(new Ability("Propel", 40, 15, 1) {

            @Override
            public int getCost(LivingEntity user) {
                if (user instanceof IQuirkData data) {
                    return 15 + (int)(data.getChargeTime() * 0.5f);
                }
                return 15;
            }

            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (!(user instanceof IQuirkData data)) return false;

                int chargeTime = data.getChargeTime();
                float chargeRatio = Math.min(chargeTime, 60) / 60.0f;
                float chargeMultiplier = 1.0f + (chargeRatio * 1.5f);

                float finalCost = 15.0f + (chargeTime * 0.5f);

                if (data.getStamina() < finalCost) {
                    if (user instanceof PlayerEntity p) p.sendMessage(Text.literal("Not enough stamina!").formatted(Formatting.RED), true);
                    data.setChargeTime(0);
                    return false;
                }

                Vec3d look = user.getRotationVector();
                double baseStrength = 1.5 + (data.getLevel() * 0.02);
                double totalStrength = baseStrength * chargeMultiplier;

                user.setVelocity(look.multiply(totalStrength));
                user.velocityModified = true;
                user.fallDistance = 0;
                user.setNoGravity(false);

                if (!world.isClient) {
                    data.consumeStamina(finalCost);
                    data.setCooldown(0, this.getCooldown());
                }

                data.setChargeTime(0);

                float pitch = 1.2f - (chargeRatio * 0.4f);
                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, pitch);

                if (world instanceof ServerWorld sw) {
                    Vec3d ringPos = user.getPos().add(0, user.getHeight() * 0.6, 0).subtract(look.multiply(1.5));
                    float ringSize = 1.5f + (chargeRatio * 2.0f);
                    spawnRings(sw, ringPos, look, ringSize);

                    // Secondary Ring at Full Charge
                    if (chargeRatio >= 1.0f) {
                        Vec3d secondaryPos = ringPos.subtract(look.multiply(1.0));
                        spawnRings(sw, secondaryPos, look, ringSize * 0.6f);
                    }
                }
                return false;
            }
        });

        // Ability 2: Glide Boost
        this.addAbility(new Ability("Glide Boost", 20, 0, 10) {
            @Override
            public boolean grantsXpOnActivate() { return false; }

            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;
                boolean newState = !data.isRegenActive();
                data.setRegenActive(newState);
                if (newState) {
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Boost Active").formatted(Formatting.YELLOW), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 2.0f);
                } else {
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Boost Inactive").formatted(Formatting.GRAY), true);
                }
                return true;
            }
        });
    }

    @Override
    public void onTick(LivingEntity user) {
        super.onTick(user);
        if (!(user instanceof IQuirkData data)) return;

        // --- Charging Logic ---
        if (data.isCharging() && data.getSelectedSlot() == 0) {

            if (data.getCooldown(0) > 0) {
                data.setCharging(false);
                if (!user.getWorld().isClient && user instanceof PlayerEntity p) {
                    p.sendMessage(Text.literal("Ability on Cooldown!").formatted(Formatting.RED), true);
                }
                return;
            }

            // NEW: Immediate stamina check to prevent starting charge
            if (data.getStamina() < 15.0f) {
                data.setCharging(false);
                if (!user.getWorld().isClient && user instanceof PlayerEntity p) {
                    p.sendMessage(Text.literal("Not enough stamina!").formatted(Formatting.RED), true);
                }
                return;
            }

            float nextCost = 15.0f + ((data.getChargeTime() + 1) * 0.5f);

            if (data.getStamina() < nextCost) {
                // Stop incrementing, hold charge at max available
            } else {
                if (data.getChargeTime() < 60) {
                    data.incrementChargeTime();
                }
            }

            user.fallDistance = 0;
            user.setVelocity(0, 0, 0);
            user.setNoGravity(true);
            user.velocityModified = true;

            if (!user.getWorld().isClient && user.getWorld() instanceof ServerWorld sw) {
                float chargeRatio = Math.min(data.getChargeTime(), 60) / 60.0f;
                float currentSize = 1.0f + (chargeRatio * 1.5f);

                Vec3d look = user.getRotationVector();
                Vec3d ringPos = user.getPos().add(0, user.getHeight() * 0.6, 0).subtract(look.multiply(1.5));

                if (user.age % 5 == 0) {
                    spawnRings(sw, ringPos, look, currentSize);
                    // Show secondary ring while charging at max
                    if (chargeRatio >= 1.0f) {
                        Vec3d secondaryPos = ringPos.subtract(look.multiply(1.0));
                        spawnRings(sw, secondaryPos, look, currentSize * 0.6f);
                    }
                }
            }
        } else if (data.getSelectedSlot() == 0 && !data.isCharging() && user.hasNoGravity() && !data.isRegenActive()) {
            user.setNoGravity(false);
        }

        if (data.isRegenActive()) {
            float cost = 0.5f;
            if (data.getStamina() >= cost) {
                if (!user.getWorld().isClient) {
                    data.consumeStamina(cost);
                    if (user.age % 40 == 0) data.addXp(0.2f);
                }

                Vec3d look = user.getRotationVector();
                double speed = 0.05 + (data.getLevel() * 0.002);

                if (data.isAwakened()) {
                    user.fallDistance = 0;
                    user.setVelocity(look.multiply(speed * 10));
                } else {
                    user.addVelocity(look.x * speed, look.y * speed, look.z * speed);
                }
                user.velocityModified = true;

                if (!user.getWorld().isClient && user.age % 5 == 0 && user.getWorld() instanceof ServerWorld sw) {
                    Vec3d ringPos = user.getPos().add(0, user.getHeight() * 0.6, 0).subtract(look.multiply(0.5));
                    spawnRings(sw, ringPos, look, 2.0f);
                }
            } else {
                data.setRegenActive(false);
                if (!user.getWorld().isClient && user instanceof PlayerEntity p) {
                    p.sendMessage(Text.literal("Boost Exhausted").formatted(Formatting.RED), true);
                }
            }
        }
    }

    private void spawnRings(ServerWorld world, Vec3d pos, Vec3d dir, float radius) {
        dir = dir.normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        if (Math.abs(dir.y) > 0.95) { up = new Vec3d(1, 0, 0); }
        Vec3d right = dir.crossProduct(up).normalize();
        Vec3d actualUp = right.crossProduct(dir).normalize();

        DustParticleEffect dust = new DustParticleEffect(RING_COLOR, 1.0f);

        int particleCount = 100;
        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double xOffset = (Math.cos(angle) * radius);
            double yOffset = (Math.sin(angle) * radius);
            Vec3d pPos = pos.add(right.multiply(xOffset)).add(actualUp.multiply(yOffset));
            world.spawnParticles(dust, pPos.x, pPos.y, pPos.z, 1, 0, 0, 0, 0);
        }
    }
}