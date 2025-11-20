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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class PlusUltraNetworking {

    public static final Identifier ACTIVATE_ABILITY_PACKET = new Identifier("plusultra", "activate_ability");
    public static final Identifier CYCLE_ABILITY_PACKET = new Identifier("plusultra", "cycle_ability");
    public static final Identifier SYNC_DATA_PACKET = new Identifier("plusultra", "sync_data");

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY_PACKET, PlusUltraNetworking::handleActivate);
        ServerPlayNetworking.registerGlobalReceiver(CYCLE_ABILITY_PACKET, PlusUltraNetworking::handleCycle);
    }

    private static void handleActivate(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;
            Quirk quirk = data.getQuirk();

            if (quirk != null) {
                int slot = data.getSelectedSlot();
                Ability ability = quirk.getAbility(slot);

                if (ability != null) {
                    if (data.getLevel() < ability.getRequiredLevel()) {
                        player.sendMessage(Text.literal("Locked! Requires Level " + ability.getRequiredLevel()).formatted(Formatting.RED), true);
                        return;
                    }

                    if (data.getCooldown(slot) > 0) return;

                    float cost = ability.getCost(player);
                    if (data.getStamina() >= cost) {
                        if (ability.onActivate(player.getWorld(), player)) {
                            data.consumeStamina(cost);
                            data.setCooldown(slot, ability.getCooldown());
                            data.addXp(5.0f);
                        }
                    }
                }
            }
        });
    }

    private static void handleCycle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int direction = buf.readInt();
        boolean isSneaking = buf.readBoolean(); // NEW

        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;

            if (isSneaking) {
                // Cycle Anchor
                data.cycleSelectedAnchor(direction);
                player.sendMessage(Text.literal("Anchor Selected: " + (data.getSelectedAnchorIndex() + 1) + "/" + data.getWarpAnchorCount()).formatted(Formatting.LIGHT_PURPLE), true);
            } else {
                // Cycle Ability
                data.cycleSlot(direction);
            }
        });
    }
}