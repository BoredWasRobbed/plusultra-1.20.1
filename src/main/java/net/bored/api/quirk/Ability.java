package net.bored.api.quirk;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Represents a specific move within a Quirk (e.g., "Detroit Smash").
 */
public abstract class Ability {

    private final String name;
    private final int cooldownTicks;
    private final int staminaCost;

    public Ability(String name, int cooldownTicks, int staminaCost) {
        this.name = name;
        this.cooldownTicks = cooldownTicks;
        this.staminaCost = staminaCost;
    }

    /**
     * The logic executed when the ability key is pressed.
     * @return true if the ability was successfully cast.
     */
    public abstract boolean onActivate(World world, PlayerEntity player);

    public String getName() {
        return name;
    }

    public int getCooldown() {
        return cooldownTicks;
    }

    public int getStaminaCost() {
        return staminaCost;
    }
}