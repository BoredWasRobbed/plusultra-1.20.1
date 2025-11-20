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
                    // Lower volume, higher pitch
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.5f, 2.0f);
                } else {
                    if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Levitation Inactive").formatted(Formatting.GRAY), true);
                }
                return true;
            }
        });
    }

    @Override
    public void onTick(LivingEntity user) {
        super.onTick(user);
        if (user.getWorld().isClient || !(user instanceof IQuirkData data)) return;

        if (data.isRegenActive()) {
            float cost = Math.max(0.2f, 0.5f - (data.getLevel() * 0.005f));

            if (data.getStamina() >= cost) {
                data.consumeStamina(cost);

                user.fallDistance = 0;

                // Get current velocity
                Vec3d velocity = user.getVelocity();

                // Calculate Y velocity: Lock it to 0 unless input given
                double yVel = 0.0;

                // Use Accessor for jumping check
                boolean isJumping = ((LivingEntityAccessor)user).isJumping();

                if (user.isSneaking()) {
                    yVel = -0.2; // Descent speed
                } else if (isJumping) {
                    yVel = 0.2; // Ascent speed
                } else {
                    // Counteract gravity completely to hover in place
                    // If we just set to 0, gravity ticks later might pull it down slightly,
                    // so we apply a small upward force to counteract standard gravity (0.08/tick) if needed,
                    // but forcing velocity every tick usually works.
                    yVel = 0.0;
                }

                // Apply velocity. Preserve X/Z so player can move normally using vanilla controls.
                // If we overwrite X/Z, the player can't move.
                user.setVelocity(velocity.x, yVel, velocity.z);
                user.velocityModified = true;

                // Reduced sound frequency
                if (user.age % 40 == 0) {
                    user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.1f, 2.0f);
                }

                if (user.age % 10 == 0) {
                    if (user.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY() - 0.2, user.getZ(), 1, 0.2, 0, 0.2, 0);
                    }
                }

                if (user.age % 60 == 0) data.addXp(0.5f);

            } else {
                data.setRegenActive(false);
                if(user instanceof PlayerEntity p) p.sendMessage(Text.literal("Stamina Depleted! Falling.").formatted(Formatting.RED), true);
            }
        }
    }
}