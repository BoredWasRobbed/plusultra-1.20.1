package net.bored.mixin;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.bored.config.PlusUltraConfig;
import net.bored.network.PlusUltraNetworking;
import net.bored.registry.QuirkRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityQuirkMixin extends Entity implements IQuirkData {

    public LivingEntityQuirkMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Unique private Identifier quirkId = null;
    @Unique private boolean isAwakened = false;
    @Unique private boolean hasReceivedStarter = false; // NEW FIELD
    @Unique private int selectedSlot = 0;
    @Unique private float stamina = 100.0f;
    @Unique private float maxStamina = 100.0f;
    @Unique private int[] cooldowns = new int[0];
    @Unique private int level = 1;
    @Unique private float xp = 0.0f;

    @Unique private final List<Vec3d> anchorPositions = new ArrayList<>();
    @Unique private final List<RegistryKey<World>> anchorDimensions = new ArrayList<>();
    @Unique private int selectedAnchorIndex = 0;

    @Unique private Vec3d portalOrigin = null;
    @Unique private int portalTimer = 0;
    @Unique private int portalImmunity = 0;

    @Unique private int placementState = 0;
    @Unique private Vec3d placementOrigin = null;
    @Unique private GameMode originalGameMode = GameMode.SURVIVAL;
    @Unique private Vec3d tempRiftA = null;

    @Unique private Vec3d riftA = null;
    @Unique private Vec3d riftB = null;
    @Unique private int riftTimer = 0;

    @Unique private boolean regenActive = false;

    // --- ALL FOR ONE DATA ---
    @Unique private boolean isAllForOne = false;
    @Unique private final List<String> stolenQuirks = new ArrayList<>();
    @Unique private final List<String> activePassives = new ArrayList<>();
    @Unique private boolean stealActive = false;
    @Unique private boolean giveActive = false;
    @Unique private String quirkToGive = "";

    @Override public Quirk getQuirk() { if (quirkId == null) return null; return QuirkRegistry.get(quirkId); }

    @Override public void setQuirk(Identifier id) {
        // NEW: Check Config for disabled quirks
        if (id != null && PlusUltraConfig.get().isQuirkDisabled(id)) {
            // If user tries to equip a disabled quirk, we deny it
            if ((Object)this instanceof PlayerEntity player) {
                player.sendMessage(Text.literal("This quirk is disabled by the server.").formatted(Formatting.RED), true);
            }
            return;
        }

        if (id == null) {
            this.quirkId = null;
            this.cooldowns = new int[0];
            this.syncQuirkData();
            return;
        }

        this.quirkId = id;
        this.selectedSlot = 0;

        String idStr = id.toString();

        // Handle AFO Flag
        if (idStr.equals("plusultra:all_for_one")) {
            this.isAllForOne = true;
        }

        Quirk q = getQuirk();
        if (q != null) this.cooldowns = new int[q.getAbilities().size()];
        else this.cooldowns = new int[0];

        this.syncQuirkData();
    }

    @Override public boolean hasQuirk() { return this.quirkId != null; }
    @Override public boolean isAwakened() { return this.isAwakened; }
    @Override public void setAwakened(boolean awakened) { this.isAwakened = awakened; this.syncQuirkData(); }

    @Override public boolean hasReceivedStarterQuirk() { return hasReceivedStarter; }
    @Override public void setReceivedStarterQuirk(boolean received) { this.hasReceivedStarter = received; this.syncQuirkData(); }

    @Override public int getLevel() { return level; }
    @Override public void setLevel(int level) { this.level = Math.max(1, level); this.syncQuirkData(); }
    @Override public float getXp() { return xp; }
    @Override public void setXp(float xp) { this.xp = xp; checkLevelUp(); this.syncQuirkData(); }
    @Override public void addXp(float amount) { this.xp += amount; checkLevelUp(); this.syncQuirkData(); }
    @Override public float getMaxXp() { return 100.0f + (this.level * 50.0f); }
    @Unique private void checkLevelUp() {
        float max = getMaxXp();
        while (this.xp >= max) {
            this.xp -= max;
            this.level++;
            max = getMaxXp();
            if (!this.getWorld().isClient && (Object)this instanceof PlayerEntity player) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.sendMessage(Text.literal("Quirk Level Up! (" + this.level + ")").formatted(Formatting.GOLD, Formatting.BOLD), true);
            }
        }
    }
    @Override public int getSelectedSlot() { return selectedSlot; }
    @Override public void setSelectedSlot(int slot) { this.selectedSlot = slot; this.syncQuirkData(); }
    @Override public void cycleSlot(int direction) {
        Quirk quirk = getQuirk();
        if (quirk == null || quirk.getAbilities().isEmpty()) return;
        int max = quirk.getAbilities().size();
        int current = this.selectedSlot;
        for (int i = 0; i < max; i++) {
            current = (current + direction) % max;
            if (current < 0) current = max - 1;
            Ability ability = quirk.getAbility(current);
            if (ability != null && this.level >= ability.getRequiredLevel()) {
                this.selectedSlot = current;
                this.syncQuirkData();
                return;
            }
        }
    }
    @Override public float getStamina() { return stamina; }
    @Override public float getMaxStamina() { return maxStamina; }
    @Override public void setStamina(float stamina) { this.stamina = MathHelper.clamp(stamina, 0, maxStamina); this.syncQuirkData(); }
    @Override public void setMaxStamina(float max) { this.maxStamina = Math.max(1.0f, max); if (this.stamina > this.maxStamina) this.stamina = this.maxStamina; this.syncQuirkData(); }
    @Override public void consumeStamina(float amount) { this.stamina = MathHelper.clamp(this.stamina - amount, 0, maxStamina); this.syncQuirkData(); }
    @Override public int getCooldown(int slot) { if (slot >= 0 && slot < cooldowns.length) return cooldowns[slot]; return 0; }
    @Override public void setCooldown(int slot, int ticks) { if (slot >= 0 && slot < cooldowns.length) { cooldowns[slot] = ticks; this.syncQuirkData(); } }
    @Override public void tickCooldowns() { for (int i = 0; i < cooldowns.length; i++) { if (cooldowns[i] > 0) cooldowns[i]--; } }

    @Override public void addWarpAnchor(Vec3d pos, RegistryKey<World> dimension) {
        if (this.anchorPositions.size() >= 5) {
            if (this.selectedAnchorIndex >= 0 && this.selectedAnchorIndex < this.anchorPositions.size()) {
                this.anchorPositions.set(this.selectedAnchorIndex, pos);
                this.anchorDimensions.set(this.selectedAnchorIndex, dimension);
            }
        } else {
            this.anchorPositions.add(pos);
            this.anchorDimensions.add(dimension);
            this.selectedAnchorIndex = this.anchorPositions.size() - 1;
        }
        this.syncQuirkData();
    }

    @Override public void removeWarpAnchor(int index) { if (index >= 0 && index < anchorPositions.size()) { anchorPositions.remove(index); anchorDimensions.remove(index); if (selectedAnchorIndex >= anchorPositions.size()) selectedAnchorIndex = Math.max(0, anchorPositions.size() - 1); this.syncQuirkData(); } }
    @Override public Vec3d getWarpAnchorPos(int index) { if (index >= 0 && index < anchorPositions.size()) return anchorPositions.get(index); return null; }
    @Override public RegistryKey<World> getWarpAnchorDim(int index) { if (index >= 0 && index < anchorDimensions.size()) return anchorDimensions.get(index); return null; }
    @Override public int getWarpAnchorCount() { return anchorPositions.size(); }
    @Override public int getSelectedAnchorIndex() { return selectedAnchorIndex; }
    @Override public void cycleSelectedAnchor(int direction) { if (anchorPositions.isEmpty()) return; selectedAnchorIndex = (selectedAnchorIndex + direction) % anchorPositions.size(); if (selectedAnchorIndex < 0) selectedAnchorIndex = anchorPositions.size() - 1; this.syncQuirkData(); }
    @Override public void setPortal(Vec3d origin, int ticks) { this.portalOrigin = origin; this.portalTimer = ticks; }
    @Override public Vec3d getPortalOrigin() { return this.portalOrigin; }
    @Override public int getPortalTimer() { return this.portalTimer; }
    @Override public void tickPortal() { if (this.portalTimer > 0) this.portalTimer--; if (this.portalImmunity > 0) this.portalImmunity--; }
    @Override public int getPortalImmunity() { return this.portalImmunity; }
    @Override public void setPortalImmunity(int ticks) { this.portalImmunity = ticks; }

    @Override public void setPlacementState(int state, Vec3d originalPos, GameMode originalMode) { this.placementState = state; this.placementOrigin = originalPos; this.originalGameMode = originalMode; this.syncQuirkData(); }
    @Override public int getPlacementState() { return this.placementState; }
    @Override public Vec3d getPlacementOrigin() { return this.placementOrigin; }
    @Override public GameMode getOriginalGameMode() { return this.originalGameMode; }
    @Override public void setTempRiftA(Vec3d pos) { this.tempRiftA = pos; }
    @Override public Vec3d getTempRiftA() { return this.tempRiftA; }

    @Override public void setRift(Vec3d a, Vec3d b, int ticks) { this.riftA = a; this.riftB = b; this.riftTimer = ticks; }
    @Override public Vec3d getRiftA() { return this.riftA; }
    @Override public Vec3d getRiftB() { return this.riftB; }
    @Override public int getRiftTimer() { return this.riftTimer; }
    @Override public void tickRift() { if (this.riftTimer > 0) this.riftTimer--; }

    @Override public boolean isRegenActive() { return this.regenActive; }
    @Override public void setRegenActive(boolean active) { this.regenActive = active; this.syncQuirkData(); }

    // --- ALL FOR ONE IMPLEMENTATION ---
    @Override public boolean isAllForOne() { return isAllForOne; }
    @Override public void setAllForOne(boolean isAFO) { this.isAllForOne = isAFO; this.syncQuirkData(); }

    @Override public List<String> getStolenQuirks() { return stolenQuirks; }

    @Override public void addStolenQuirk(String quirkId) {
        if (quirkId.equals("plusultra:all_for_one") && stolenQuirks.contains(quirkId)) {
            return;
        }
        stolenQuirks.add(quirkId);
        this.syncQuirkData();
    }

    @Override public void removeStolenQuirk(String quirkId) {
        stolenQuirks.remove(quirkId);

        if (!stolenQuirks.contains(quirkId) && activePassives.contains(quirkId)) {
            activePassives.remove(quirkId);
            if ((Object)this instanceof PlayerEntity player) {
                // UPDATED: Use display name
                Quirk q = QuirkRegistry.get(new Identifier(quirkId));
                Text name = q != null ? q.getName() : Text.literal(quirkId);
                player.sendMessage(Text.literal("Passive disabled: ").append(name).append(" (Quirk lost)").formatted(Formatting.RED), true);
            }
        }

        this.syncQuirkData();
    }

    @Override public List<String> getActivePassives() { return activePassives; }
    @Override public void togglePassive(String quirkId) {
        if (activePassives.contains(quirkId)) activePassives.remove(quirkId);
        else activePassives.add(quirkId);
        this.syncQuirkData();
    }

    @Override public boolean isStealActive() { return stealActive; }
    @Override public void setStealActive(boolean active) { this.stealActive = active; this.syncQuirkData(); }

    @Override public boolean isGiveActive() { return giveActive; }
    @Override public void setGiveActive(boolean active) { this.giveActive = active; this.syncQuirkData(); }

    @Override public String getQuirkToGive() { return quirkToGive; }
    @Override public void setQuirkToGive(String quirkId) { this.quirkToGive = quirkId; this.syncQuirkData(); }


    @Override public void syncQuirkData() {
        if (this.getWorld().isClient) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(this.getId());

        boolean has = (this.quirkId != null);
        buf.writeBoolean(has);
        if (has) buf.writeString(this.quirkId.toString());
        buf.writeBoolean(this.isAwakened);
        buf.writeInt(this.selectedSlot);
        buf.writeFloat(this.stamina);
        buf.writeFloat(this.maxStamina);
        buf.writeInt(this.level);
        buf.writeFloat(this.xp);
        buf.writeInt(this.cooldowns.length);
        for (int cd : this.cooldowns) buf.writeInt(cd);

        int count = anchorPositions.size();
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Vec3d pos = anchorPositions.get(i);
            buf.writeDouble(pos.x); buf.writeDouble(pos.y); buf.writeDouble(pos.z);
            buf.writeString(anchorDimensions.get(i).getValue().toString());
        }
        buf.writeInt(this.selectedAnchorIndex);
        buf.writeInt(this.placementState);
        buf.writeBoolean(this.regenActive);

        buf.writeBoolean(this.isAllForOne);
        buf.writeInt(this.stolenQuirks.size());
        for(String s : this.stolenQuirks) buf.writeString(s);
        buf.writeInt(this.activePassives.size());
        for(String s : this.activePassives) buf.writeString(s);
        buf.writeBoolean(this.stealActive);
        buf.writeBoolean(this.giveActive);
        buf.writeString(this.quirkToGive);

        for (ServerPlayerEntity player : PlayerLookup.tracking((Entity)(Object)this)) {
            ServerPlayNetworking.send(player, PlusUltraNetworking.SYNC_DATA_PACKET, buf);
        }

        if ((Object)this instanceof ServerPlayerEntity self) {
            ServerPlayNetworking.send(self, PlusUltraNetworking.SYNC_DATA_PACKET, buf);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeQuirkData(NbtCompound nbt, CallbackInfo ci) {
        if (this.quirkId != null) nbt.putString("QuirkId", this.quirkId.toString());
        nbt.putBoolean("IsAwakened", this.isAwakened);
        nbt.putBoolean("HasReceivedStarter", this.hasReceivedStarter); // NEW
        nbt.putInt("SelectedSlot", this.selectedSlot);
        nbt.putFloat("Stamina", this.stamina);
        nbt.putFloat("MaxStamina", this.maxStamina);
        nbt.putIntArray("Cooldowns", this.cooldowns);
        nbt.putInt("QuirkLevel", this.level);
        nbt.putFloat("QuirkXp", this.xp);
        nbt.putBoolean("RegenActive", this.regenActive);

        nbt.putBoolean("IsAFO", this.isAllForOne);
        NbtList stolenList = new NbtList();
        for(String s : stolenQuirks) stolenList.add(NbtString.of(s));
        nbt.put("StolenQuirks", stolenList);

        NbtList passiveList = new NbtList();
        for(String s : activePassives) passiveList.add(NbtString.of(s));
        nbt.put("ActivePassives", passiveList);

        NbtList anchorList = new NbtList();
        for (int i = 0; i < anchorPositions.size(); i++) {
            NbtCompound anchorTag = new NbtCompound();
            Vec3d pos = anchorPositions.get(i);
            anchorTag.putDouble("X", pos.x); anchorTag.putDouble("Y", pos.y); anchorTag.putDouble("Z", pos.z);
            anchorTag.putString("Dim", anchorDimensions.get(i).getValue().toString());
            anchorList.add(anchorTag);
        }
        nbt.put("WarpAnchors", anchorList);
        nbt.putInt("SelectedAnchorIndex", this.selectedAnchorIndex);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readQuirkData(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("QuirkId")) this.quirkId = new Identifier(nbt.getString("QuirkId"));
        else this.quirkId = null;
        this.isAwakened = nbt.getBoolean("IsAwakened");
        this.hasReceivedStarter = nbt.getBoolean("HasReceivedStarter"); // NEW
        this.selectedSlot = nbt.getInt("SelectedSlot");
        if (nbt.contains("Stamina")) this.stamina = nbt.getFloat("Stamina");
        if (nbt.contains("MaxStamina")) this.maxStamina = nbt.getFloat("MaxStamina");
        if (nbt.contains("Cooldowns")) this.cooldowns = nbt.getIntArray("Cooldowns");
        if (nbt.contains("QuirkLevel")) this.level = nbt.getInt("QuirkLevel");
        if (nbt.contains("QuirkXp")) this.xp = nbt.getFloat("QuirkXp");
        if (nbt.contains("RegenActive")) this.regenActive = nbt.getBoolean("RegenActive");

        this.isAllForOne = nbt.getBoolean("IsAFO");
        this.stolenQuirks.clear();
        if (nbt.contains("StolenQuirks")) {
            NbtList list = nbt.getList("StolenQuirks", NbtElement.STRING_TYPE);
            for(NbtElement e : list) stolenQuirks.add(e.asString());
        }
        this.activePassives.clear();
        if (nbt.contains("ActivePassives")) {
            NbtList list = nbt.getList("ActivePassives", NbtElement.STRING_TYPE);
            for(NbtElement e : list) activePassives.add(e.asString());
        }

        this.anchorPositions.clear(); this.anchorDimensions.clear();
        if (nbt.contains("WarpAnchors")) {
            NbtList anchorList = nbt.getList("WarpAnchors", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < anchorList.size(); i++) {
                NbtCompound anchorTag = anchorList.getCompound(i);
                this.anchorPositions.add(new Vec3d(anchorTag.getDouble("X"), anchorTag.getDouble("Y"), anchorTag.getDouble("Z")));
                this.anchorDimensions.add(RegistryKey.of(RegistryKeys.WORLD, new Identifier(anchorTag.getString("Dim"))));
            }
        }
        if (nbt.contains("SelectedAnchorIndex")) this.selectedAnchorIndex = nbt.getInt("SelectedAnchorIndex");

        Quirk q = getQuirk();
        if (q != null && this.cooldowns.length != q.getAbilities().size()) {
            this.cooldowns = new int[q.getAbilities().size()];
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickStaminaAndCooldowns(CallbackInfo ci) {
        if (this.quirkId != null) {
            Quirk quirk = getQuirk();
            if (quirk != null) quirk.onTick((LivingEntity)(Object)this);
            this.tickCooldowns();
            this.tickPortal();
            this.tickRift();
        }

        if (!this.activePassives.isEmpty()) {
            for (String passiveId : this.activePassives) {
                if (this.quirkId != null && this.quirkId.toString().equals(passiveId)) continue;
                Quirk q = QuirkRegistry.get(new Identifier(passiveId));
                if (q != null) q.onTick((LivingEntity)(Object)this);
            }
        }

        if (this.stamina < this.maxStamina) this.stamina = Math.min(this.stamina + 0.05f, this.maxStamina);
    }
}