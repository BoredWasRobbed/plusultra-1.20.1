package net.bored.api.data;

import net.bored.api.quirk.Quirk;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.List;

public interface IQuirkData {
    // Quirk
    Quirk getQuirk();
    void setQuirk(Identifier quirkId);
    boolean hasQuirk();

    // Awakening
    boolean isAwakened();
    void setAwakened(boolean awakened);

    // Config / Progression Tracking
    boolean hasReceivedStarterQuirk();
    void setReceivedStarterQuirk(boolean received);

    // Leveling
    int getLevel();
    void setLevel(int level);
    float getXp();
    void setXp(float xp);
    void addXp(float amount);
    float getMaxXp();

    // Slots
    int getSelectedSlot();
    void setSelectedSlot(int slot);
    void cycleSlot(int direction);

    // Stamina
    float getStamina();
    float getMaxStamina();
    void setStamina(float stamina);
    void setMaxStamina(float max);
    void consumeStamina(float amount);

    // Cooldowns
    int getCooldown(int slot);
    void setCooldown(int slot, int ticks);
    void tickCooldowns();

    // Warp Anchor
    void addWarpAnchor(Vec3d pos, RegistryKey<World> dimension);
    void removeWarpAnchor(int index);
    Vec3d getWarpAnchorPos(int index);
    RegistryKey<World> getWarpAnchorDim(int index);
    int getWarpAnchorCount();
    int getSelectedAnchorIndex();
    void cycleSelectedAnchor(int direction);

    // Portal Data
    void setPortal(Vec3d origin, int ticks);
    Vec3d getPortalOrigin();
    int getPortalTimer();
    void tickPortal();
    int getPortalImmunity();
    void setPortalImmunity(int ticks);

    // Dimensional Rift Data
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

    // Super Regeneration Data
    boolean isRegenActive();
    void setRegenActive(boolean active);

    // --- ALL FOR ONE DATA ---
    boolean isAllForOne(); // Is this player an AFO user natively?
    void setAllForOne(boolean isAFO);

    List<String> getStolenQuirks(); // List of Quirk IDs
    void addStolenQuirk(String quirkId);
    void removeStolenQuirk(String quirkId);

    List<String> getActivePassives(); // Quirks that are "toggled on" in background
    void togglePassive(String quirkId);

    // Modes for the AFO moves
    boolean isStealActive();
    void setStealActive(boolean active);

    boolean isGiveActive();
    void setGiveActive(boolean active);

    String getQuirkToGive();
    void setQuirkToGive(String quirkId);

    // Sync
    void syncQuirkData();
}