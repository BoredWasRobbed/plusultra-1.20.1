package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
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
            // Disable default XP gain on toggle
            @Override
            public boolean grantsXpOnActivate() {
                return false;
            }

            @Override
            public boolean onActivate(World world, PlayerEntity player) {
                if (world.isClient || !(player instanceof IQuirkData data)) return false;

                boolean newState = !data.isRegenActive();
                data.setRegenActive(newState);

                if (newState) {
                    player.sendMessage(Text.literal("Regeneration Active").formatted(Formatting.GREEN), true);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);
                } else {
                    player.sendMessage(Text.literal("Regeneration Inactive").formatted(Formatting.RED), true);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);
                }
                return true;
            }
        });
    }

    @Override
    public void onTick(PlayerEntity player) {
        super.onTick(player);
        if (player.getWorld().isClient || !(player instanceof IQuirkData data)) return;

        if (data.isRegenActive()) {
            if (player.age % 10 == 0) {
                if (player.getHealth() < player.getMaxHealth()) {
                    float healCost = 5.0f;

                    if (data.getStamina() >= healCost) {
                        data.consumeStamina(healCost);
                        player.heal(1.0f);

                        // Grant XP here instead (Healing = XP)
                        data.addXp(2.0f);

                        // CHANGED: Visuals from Heart to Steam (Campfire Smoke)
                        if (player.getWorld() instanceof ServerWorld serverWorld) {
                            serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.5, 0.3, 0.05);
                        }

                        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.5f, 2.0f);
                    } else {
                        data.setRegenActive(false);
                        player.sendMessage(Text.literal("Stamina Depleted! Regen deactivated.").formatted(Formatting.RED), true);
                    }
                }
            }
        }
    }

    @Override
    public void registerAwakening() {
        this.setAwakeningCondition((player, data) -> player.getHealth() < 5.0f && data.getStamina() > 50.0f);
    }
}