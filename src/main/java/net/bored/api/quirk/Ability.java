package net.bored.api.quirk;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public abstract class Ability {

    private final String name;
    private final int cooldownTicks;
    private final int staminaCost;
    private final int requiredLevel;

    public Ability(String name, int cooldownTicks, int staminaCost, int requiredLevel) {
        this.name = name;
        this.cooldownTicks = cooldownTicks;
        this.staminaCost = staminaCost;
        this.requiredLevel = requiredLevel;
    }

    public abstract boolean onActivate(World world, PlayerEntity player);

    /**
     * If true, onActivate will be called even if the ability is on cooldown.
     * Use this to handle secondary effects (like closing a rift) inside onActivate.
     */
    public boolean canUseWhileOnCooldown() {
        return false;
    }

    public int getCost(PlayerEntity player) {
        return this.staminaCost;
    }

    public String getName() {
        return name;
    }

    public int getCooldown() {
        return cooldownTicks;
    }

    public int getStaminaCost() {
        return staminaCost;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }
}