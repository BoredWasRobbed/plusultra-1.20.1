package net.bored.api.quirk;

import net.bored.api.data.IQuirkData;
import net.minecraft.entity.LivingEntity;

@FunctionalInterface
public interface AwakeningCondition {
    boolean shouldAwaken(LivingEntity user, IQuirkData data);
}