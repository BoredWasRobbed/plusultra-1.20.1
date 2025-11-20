package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class AttractionQuirk extends Quirk {

    public AttractionQuirk() {
        super(new Identifier("plusultra", "attraction"), 0x00FA9A);
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Pull (Active) - Level 1
        this.addAbility(new Ability("Pull", 40, 15, 1) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (!(user instanceof IQuirkData data)) return false;

                // Scaling Range: Base 10 + 0.5 per level. Level 50 = 35 blocks.
                double range = 10.0 + (data.getLevel() * 0.5);

                // Scaling Strength: Base 0.5 + 0.05 per level.
                double strength = 0.5 + (data.getLevel() * 0.05);

                List<Entity> targets = world.getOtherEntities(user, user.getBoundingBox().expand(range));

                for (Entity target : targets) {
                    Vec3d dir = user.getPos().subtract(target.getPos()).normalize();
                    target.addVelocity(dir.x * strength, dir.y * strength, dir.z * strength);
                    target.velocityModified = true;
                }

                if (!world.isClient) {
                    ServerWorld sw = (ServerWorld) world;
                    // Visual ring effect
                    for (int i = 0; i < 360; i+=15) {
                        double rad = Math.toRadians(i);
                        double x = user.getX() + Math.cos(rad) * 2;
                        double z = user.getZ() + Math.sin(rad) * 2;
                        sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, user.getY() + 1, z, 1, 0, 0, 0, 0);
                    }
                }

                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                return true;
            }
        });
    }
}