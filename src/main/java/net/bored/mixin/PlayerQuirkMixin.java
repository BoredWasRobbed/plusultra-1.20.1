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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
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
    @Unique private final float maxStamina = 100.0f;
    @Unique private int[] cooldowns = new int[0];

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
        this.stamina = this.maxStamina;

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
    public void setStamina(float stamina) {
        this.stamina = MathHelper.clamp(stamina, 0, maxStamina);
        this.syncQuirkData();
    }

    @Override
    public void consumeStamina(float amount) {
        this.stamina = MathHelper.clamp(this.stamina - amount, 0, maxStamina);
        this.syncQuirkData();
    }

    // --- COOLDOWN LOGIC ---

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
            this.syncQuirkData(); // Sync when cooldown is set so client sees it immediately
        }
    }

    @Override
    public void tickCooldowns() {
        boolean changed = false;
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
                // Optimization: Don't sync every single tick, rely on Client-side prediction or sync only on 0
                // or sync every 20 ticks if needed. For now, we won't sync every tick to save bandwidth.
                // The HUD can predict the countdown if we just send the start value.
            }
        }
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

        // NEW: Write Cooldowns to packet
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
        nbt.putIntArray("Cooldowns", this.cooldowns);
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
        if (nbt.contains("Cooldowns")) {
            this.cooldowns = nbt.getIntArray("Cooldowns");
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