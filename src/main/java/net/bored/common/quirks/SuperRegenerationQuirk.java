package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class SuperRegenerationQuirk extends Quirk {

    public SuperRegenerationQuirk() {
        super(new Identifier("plusultra", "super_regeneration"), 0xFF0033);
    }

    @Override
    public void registerAbilities() {
        this.addAbility(new Ability("Toggle Regen", 20, 0, 1) {
            @Override
            public boolean grantsXpOnActivate() { return false; }

            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                boolean newState = !data.isRegenActive();
                data.setRegenActive(newState);

                if (newState) {
                    if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("Regeneration Active").formatted(Formatting.GREEN), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);
                } else {
                    if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("Regeneration Inactive").formatted(Formatting.RED), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);
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
            // Mobs might need faster ticks or different logic, but this works generally
            if (user.age % 10 == 0) {
                if (user.getHealth() < user.getMaxHealth()) {
                    float healCost = 5.0f;

                    if (data.getStamina() >= healCost) {
                        data.consumeStamina(healCost);
                        user.heal(1.0f);

                        data.addXp(2.0f);

                        if (user.getWorld() instanceof ServerWorld serverWorld) {
                            serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, user.getX(), user.getY() + 1, user.getZ(), 5, 0.3, 0.5, 0.3, 0.05);
                        }

                        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.5f, 2.0f);
                    } else {
                        data.setRegenActive(false);
                        if (user instanceof PlayerEntity player) player.sendMessage(Text.literal("Stamina Depleted! Regen deactivated.").formatted(Formatting.RED), true);
                    }
                }
            }
        }
    }

    @Override
    public void registerAwakening() {
        this.setAwakeningCondition((user, data) -> user.getHealth() < 5.0f && data.getStamina() > 50.0f);
    }
}