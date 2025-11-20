package net.bored.api.data;

import net.bored.api.quirk.Quirk;
import net.minecraft.util.Identifier;

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
    void setStamina(float stamina);
    void consumeStamina(float amount);

    // Cooldowns (NEW)
    int getCooldown(int slot);
    void setCooldown(int slot, int ticks);
    void tickCooldowns(); // Called every tick

    // Syncing
    void syncQuirkData();
}