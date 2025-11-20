package net.bored.network;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class PlusUltraNetworking {

    public static final Identifier ACTIVATE_ABILITY_PACKET = new Identifier("plusultra", "activate_ability");
    public static final Identifier CYCLE_ABILITY_PACKET = new Identifier("plusultra", "cycle_ability");
    public static final Identifier SYNC_DATA_PACKET = new Identifier("plusultra", "sync_data");

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY_PACKET, PlusUltraNetworking::handleActivate);
        ServerPlayNetworking.registerGlobalReceiver(CYCLE_ABILITY_PACKET, PlusUltraNetworking::handleCycle);
    }

    // --- Server Handlers ---

    private static void handleActivate(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;
            Quirk quirk = data.getQuirk();

            if (quirk != null) {
                int slot = data.getSelectedSlot();
                Ability ability = quirk.getAbility(slot);

                if (ability != null) {
                    // 1. Check Cooldown
                    if (data.getCooldown(slot) > 0) {
                        return;
                    }

                    // 2. Check Stamina (Dynamic Cost)
                    float cost = ability.getCost(player); // Changed to getCost(player)

                    if (data.getStamina() >= cost) {
                        if (ability.onActivate(player.getWorld(), player)) {
                            data.consumeStamina(cost);

                            // 3. Set Cooldown
                            data.setCooldown(slot, ability.getCooldown());
                        }
                    } else {
                        // Not enough stamina
                    }
                }
            }
        });
    }

    private static void handleCycle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int direction = buf.readInt();
        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;
            data.cycleSlot(direction);
        });
    }
}