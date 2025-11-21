package net.bored.api.data;

import net.bored.api.quirk.Quirk;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.List;

public interface IQuirkData {
    Quirk getQuirk();
    void setQuirk(Identifier quirkId);
    boolean hasQuirk();
    boolean isAwakened();
    void setAwakened(boolean awakened);
    boolean hasReceivedStarterQuirk();
    void setReceivedStarterQuirk(boolean received);
    int getLevel();
    void setLevel(int level);
    float getXp();
    void setXp(float xp);
    void addXp(float amount);
    float getMaxXp();
    int getSelectedSlot();
    void setSelectedSlot(int slot);
    void cycleSlot(int direction);
    float getStamina();
    float getMaxStamina();
    void setStamina(float stamina);
    void setMaxStamina(float max);
    void consumeStamina(float amount);
    int getCooldown(int slot);
    void setCooldown(int slot, int ticks);
    void tickCooldowns();
    void addWarpAnchor(Vec3d pos, RegistryKey<World> dimension);
    void removeWarpAnchor(int index);
    Vec3d getWarpAnchorPos(int index);
    RegistryKey<World> getWarpAnchorDim(int index);
    int getWarpAnchorCount();
    int getSelectedAnchorIndex();
    void cycleSelectedAnchor(int direction);
    void setPortal(Vec3d origin, int ticks);
    Vec3d getPortalOrigin();
    int getPortalTimer();
    void tickPortal();
    int getPortalImmunity();
    void setPortalImmunity(int ticks);
    void setPlacementState(int state, Vec3d originalPos, GameMode originalMode);
    int getPlacementState();
    Vec3d getPlacementOrigin();
    GameMode getOriginalGameMode();
    void setTempRiftA(Vec3d pos);
    Vec3d getTempRiftA();
    void setRift(Vec3d a, Vec3d b, int ticks);
    Vec3d getRiftA();
    Vec3d getRiftB();
    int getRiftTimer();
    void tickRift();
    boolean isRegenActive();
    void setRegenActive(boolean active);
    boolean isAllForOne();
    void setAllForOne(boolean isAFO);
    List<String> getStolenQuirks();
    void addStolenQuirk(String quirkId);
    void removeStolenQuirk(String quirkId);
    int getCurrentSlotLimit();
    List<String> getActivePassives();
    void togglePassive(String quirkId);
    boolean isStealActive();
    void setStealActive(boolean active);
    boolean isGiveActive();
    void setGiveActive(boolean active);
    String getQuirkToGive();
    void setQuirkToGive(String quirkId);
    void syncQuirkData();

    // Charge State for Propulsion (and future quirks)
    boolean isCharging();
    void setCharging(boolean charging);

    // NEW: Charge Timer
    int getChargeTime();
    void setChargeTime(int ticks);
    void incrementChargeTime();

    // NEW: RPG Stats
    int getStatPoints();
    void setStatPoints(int points);
    void addStatPoints(int points);

    int getStrengthStat();
    void setStrengthStat(int value);

    int getHealthStat();
    void setHealthStat(int value);

    int getSpeedStat();
    void setSpeedStat(int value);

    int getStaminaStat();
    void setStaminaStat(int value);

    int getDefenseStat();
    void setDefenseStat(int value);
}