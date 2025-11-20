package net.bored.mixin;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Quirk;
import net.bored.network.PlusUltraNetworking;
import net.bored.registry.QuirkRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerQuirkMixin extends LivingEntity implements IQuirkData {

    protected PlayerQuirkMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Unique private Identifier quirkId = null;
    @Unique private boolean isAwakened = false;
    @Unique private int selectedSlot = 0;
    @Unique private float stamina = 100.0f;
    @Unique private float maxStamina = 100.0f; // Removed 'final'
    @Unique private int[] cooldowns = new int[0];

    @Unique private Vec3d warpAnchorPos = null;
    @Unique private RegistryKey<World> warpAnchorDim = null;

    // --- INTERFACE IMPLEMENTATION ---

    @Override
    public Quirk getQuirk() {
        if (quirkId == null) return null;
        return QuirkRegistry.get(quirkId);
    }

    @Override
    public void setQuirk(Identifier id) {
        this.quirkId = id;
        this.isAwakened = false;
        this.selectedSlot = 0;
        // Don't reset max stamina on quirk change, but reset current
        this.stamina = this.maxStamina;
        this.warpAnchorPos = null;

        Quirk q = getQuirk();
        if (q != null) {
            this.cooldowns = new int[q.getAbilities().size()];
        } else {
            this.cooldowns = new int[0];
        }

        this.syncQuirkData();
    }

    @Override
    public boolean hasQuirk() {
        return this.quirkId != null;
    }

    @Override
    public boolean isAwakened() {
        return this.isAwakened;
    }

    @Override
    public void setAwakened(boolean awakened) {
        this.isAwakened = awakened;
        this.syncQuirkData();
    }

    @Override
    public int getSelectedSlot() {
        return selectedSlot;
    }

    @Override
    public void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
        this.syncQuirkData();
    }

    @Override
    public void cycleSlot(int direction) {
        Quirk quirk = getQuirk();
        if (quirk == null || quirk.getAbilities().isEmpty()) return;

        int max = quirk.getAbilities().size();
        this.selectedSlot = (this.selectedSlot + direction) % max;

        if (this.selectedSlot < 0) this.selectedSlot = max - 1;

        this.syncQuirkData();
    }

    @Override
    public float getStamina() {
        return stamina;
    }

    @Override
    public float getMaxStamina() {
        return maxStamina;
    }

    @Override
    public void setStamina(float stamina) {
        this.stamina = MathHelper.clamp(stamina, 0, maxStamina);
        this.syncQuirkData();
    }

    @Override
    public void setMaxStamina(float max) {
        this.maxStamina = Math.max(1.0f, max); // Prevent 0 or negative max
        if (this.stamina > this.maxStamina) {
            this.stamina = this.maxStamina;
        }
        this.syncQuirkData();
    }

    @Override
    public void consumeStamina(float amount) {
        this.stamina = MathHelper.clamp(this.stamina - amount, 0, maxStamina);
        this.syncQuirkData();
    }

    @Override
    public int getCooldown(int slot) {
        if (slot >= 0 && slot < cooldowns.length) {
            return cooldowns[slot];
        }
        return 0;
    }

    @Override
    public void setCooldown(int slot, int ticks) {
        if (slot >= 0 && slot < cooldowns.length) {
            cooldowns[slot] = ticks;
            this.syncQuirkData();
        }
    }

    @Override
    public void tickCooldowns() {
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
            }
        }
    }

    @Override
    public void setWarpAnchor(Vec3d pos, RegistryKey<World> dimension) {
        this.warpAnchorPos = pos;
        this.warpAnchorDim = dimension;
    }

    @Override
    public Vec3d getWarpAnchorPos() {
        return this.warpAnchorPos;
    }

    @Override
    public RegistryKey<World> getWarpAnchorDim() {
        return this.warpAnchorDim;
    }

    // --- SYNCING LOGIC ---

    @Override
    public void syncQuirkData() {
        if (this.getWorld().isClient) return;
        if (!((Object)this instanceof ServerPlayerEntity player)) return;

        PacketByteBuf buf = PacketByteBufs.create();
        boolean has = (this.quirkId != null);
        buf.writeBoolean(has);
        if (has) {
            buf.writeString(this.quirkId.toString());
        }
        buf.writeBoolean(this.isAwakened);
        buf.writeInt(this.selectedSlot);
        buf.writeFloat(this.stamina);
        buf.writeFloat(this.maxStamina); // NEW: Sync Max Stamina

        buf.writeInt(this.cooldowns.length);
        for (int cd : this.cooldowns) {
            buf.writeInt(cd);
        }

        ServerPlayNetworking.send(player, PlusUltraNetworking.SYNC_DATA_PACKET, buf);
    }

    // --- NBT SAVE/LOAD ---

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeQuirkData(NbtCompound nbt, CallbackInfo ci) {
        if (this.quirkId != null) {
            nbt.putString("QuirkId", this.quirkId.toString());
        }
        nbt.putBoolean("IsAwakened", this.isAwakened);
        nbt.putInt("SelectedSlot", this.selectedSlot);
        nbt.putFloat("Stamina", this.stamina);
        nbt.putFloat("MaxStamina", this.maxStamina); // Save Max
        nbt.putIntArray("Cooldowns", this.cooldowns);

        if (this.warpAnchorPos != null && this.warpAnchorDim != null) {
            NbtCompound anchorTag = new NbtCompound();
            anchorTag.putDouble("X", this.warpAnchorPos.x);
            anchorTag.putDouble("Y", this.warpAnchorPos.y);
            anchorTag.putDouble("Z", this.warpAnchorPos.z);
            anchorTag.putString("Dim", this.warpAnchorDim.getValue().toString());
            nbt.put("WarpAnchor", anchorTag);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readQuirkData(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("QuirkId")) {
            this.quirkId = new Identifier(nbt.getString("QuirkId"));
        } else {
            this.quirkId = null;
        }
        this.isAwakened = nbt.getBoolean("IsAwakened");
        this.selectedSlot = nbt.getInt("SelectedSlot");
        if (nbt.contains("Stamina")) {
            this.stamina = nbt.getFloat("Stamina");
        }
        if (nbt.contains("MaxStamina")) {
            this.maxStamina = nbt.getFloat("MaxStamina"); // Load Max
        }
        if (nbt.contains("Cooldowns")) {
            this.cooldowns = nbt.getIntArray("Cooldowns");
        }

        if (nbt.contains("WarpAnchor")) {
            NbtCompound anchorTag = nbt.getCompound("WarpAnchor");
            this.warpAnchorPos = new Vec3d(anchorTag.getDouble("X"), anchorTag.getDouble("Y"), anchorTag.getDouble("Z"));
            this.warpAnchorDim = RegistryKey.of(RegistryKeys.WORLD, new Identifier(anchorTag.getString("Dim")));
        }

        Quirk q = getQuirk();
        if (q != null && this.cooldowns.length != q.getAbilities().size()) {
            this.cooldowns = new int[q.getAbilities().size()];
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickStaminaAndCooldowns(CallbackInfo ci) {
        if (this.quirkId != null) {
            Quirk quirk = getQuirk();
            if (quirk != null) {
                quirk.onTick((PlayerEntity)(Object)this);
            }
            this.tickCooldowns();
        }

        if (this.stamina < this.maxStamina) {
            this.stamina = Math.min(this.stamina + 0.05f, this.maxStamina);
        }
    }
}