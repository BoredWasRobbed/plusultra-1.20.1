package net.bored.api.quirk;

import net.bored.api.data.IQuirkData;
import net.minecraft.entity.player.PlayerEntity;

@FunctionalInterface
public interface AwakeningCondition {
    /**
     * Checks if the player meets the conditions to awaken.
     * @param player The player entity
     * @param data The player's quirk data
     * @return true if awakening should trigger
     */
    boolean shouldAwaken(PlayerEntity player, IQuirkData data);
}