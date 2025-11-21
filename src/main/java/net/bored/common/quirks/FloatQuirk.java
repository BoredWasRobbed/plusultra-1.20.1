package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.mixin.LivingEntityAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FloatQuirk extends Quirk {

    public FloatQuirk() {
        super(new Identifier("plusultra", "float"), 0x87CEEB);
    }

    @Override
    public void registerAwakening() {
        // Simple Condition: Reach Level 30 to unlock Flight Control
        this.setAwakeningCondition((user, data) -> data.getLevel() >= 30);
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Levitate (Toggle) - Level 1
        this.addAbility(new Ability("Levitate", 20, 0, 1) {
            @Override
            public boolean grantsXpOnActivate() { return false; }

            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                boolean newState = !data.isRegenActive();
                data.setRegenActive(newState);

                if (newState) {
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Levitation Active").formatted(Formatting.AQUA), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.5f, 2.0f);
                } else {
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Levitation Inactive").formatted(Formatting.GRAY), true);
                    user.setNoGravity(false);
                }
                return true;
            }
        });
    }

    @Override
    public void onTick(LivingEntity user) {
        super.onTick(user);
        // Removed "user.getWorld().isClient" check here to allow Client-Side prediction
        if (!(user instanceof IQuirkData data)) return;

        if (data.isRegenActive()) {
            float cost = Math.max(0.2f, 0.5f - (data.getLevel() * 0.005f));

            // Check stamina (Client predicts, Server enforces)
            if (data.getStamina() >= cost) {

                // --- SERVER SIDE: State & Resources ---
                if (!user.getWorld().isClient) {
                    data.consumeStamina(cost);
                    if (user.age % 60 == 0) data.addXp(0.5f);

                    // Audio (Broadcast to others)
                    if (user.age % 40 == 0) {
                        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.1f, 2.0f);
                    }
                    // Particles (Broadcast)
                    if (user.age % 10 == 0 && user.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY() - 0.2, user.getZ(), 1, 0.2, 0, 0.2, 0);
                    }
                }

                // --- MOVEMENT LOGIC ---
                user.fallDistance = 0;
                user.setNoGravity(true); // Essential on both sides

                // Determine if we should calculate physics
                // Players: Run on CLIENT (Prediction). Skip on SERVER (Trust Client).
                // Mobs: Run on SERVER.
                boolean shouldApplyPhysics = user.getWorld().isClient || !(user instanceof PlayerEntity);

                if (shouldApplyPhysics) {
                    // Calculate Y velocity (Hover control)
                    double yVel = 0.0;

                    // Vertical Movement is now AWAKENING EXCLUSIVE
                    if (data.isAwakened()) {
                        boolean isJumping = ((LivingEntityAccessor)user).isJumping();
                        if (user.isSneaking()) {
                            yVel = -0.2; // Descent speed
                        } else if (isJumping) {
                            yVel = 0.2; // Ascent speed
                        }
                    }
                    // If not awakened, yVel stays 0.0 (Perfect Hover)

                    // Scale Speed with Level
                    // Lvl 1: 0.025 ~ Vanilla Walk
                    // Lvl 100: 0.175 ~ Fast Flight
                    float airSpeed = 0.025f + (data.getLevel() * 0.0015f);

                    // Apply input-based velocity
                    user.updateVelocity(airSpeed, new Vec3d(user.sidewaysSpeed, 0, user.forwardSpeed));

                    Vec3d currentVel = user.getVelocity();
                    // Apply Drag to prevent sliding forever (0.91 is roughly block friction)
                    user.setVelocity(currentVel.x * 0.91, yVel, currentVel.z * 0.91);
                    user.velocityModified = true;
                }

            } else {
                // Stamina ran out
                if (!user.getWorld().isClient) {
                    data.setRegenActive(false);
                    user.setNoGravity(false);
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Stamina Depleted! Falling.").formatted(Formatting.RED), true);
                } else {
                    // Client visual update
                    user.setNoGravity(false);
                }
            }
        } else {
            // Safety: restore gravity if quirk is inactive
            if (user.hasNoGravity()) {
                user.setNoGravity(false);
            }
        }
    }

    @Override
    public void onUnequip(LivingEntity user) {
        super.onUnequip(user);
        user.setNoGravity(false);
    }
}