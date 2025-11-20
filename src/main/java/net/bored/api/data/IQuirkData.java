package net.bored.api.data;

import net.bored.api.quirk.Quirk;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public interface IQuirkData {
    // Quirk Accessors
    Quirk getQuirk();
    void setQuirk(Identifier quirkId);
    boolean hasQuirk();

    // Awakening Data
    boolean isAwakened();
    void setAwakened(boolean awakened);

    // Slot Accessors
    int getSelectedSlot();
    void setSelectedSlot(int slot);
    void cycleSlot(int direction);

    // Stamina Accessors
    float getStamina();
    float getMaxStamina();
    void setStamina(float stamina);
    void setMaxStamina(float max); // NEW
    void consumeStamina(float amount);

    // Cooldowns
    int getCooldown(int slot);
    void setCooldown(int slot, int ticks);
    void tickCooldowns();

    // Warp Anchor Data
    void setWarpAnchor(Vec3d pos, RegistryKey<World> dimension);
    Vec3d getWarpAnchorPos();
    RegistryKey<World> getWarpAnchorDim();

    // Syncing
    void syncQuirkData();
}