package net.bored.api.quirk;

import net.minecraft.entity.LivingEntity;
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

    public abstract boolean onActivate(World world, LivingEntity user);

    public boolean canUseWhileOnCooldown() {
        return false;
    }

    public boolean grantsXpOnActivate() {
        return true;
    }

    public int getCost(LivingEntity user) {
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