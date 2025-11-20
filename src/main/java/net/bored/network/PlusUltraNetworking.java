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

                    // UPDATED: Allow execution if the ability specifically permits it during cooldown
                    boolean isOnCooldown = data.getCooldown(slot) > 0;
                    if (isOnCooldown && !ability.canUseWhileOnCooldown()) {
                        return;
                    }

                    // If we are bypassing cooldown, we assume onActivate handles its own logic/costs for that state
                    // otherwise we check stamina normally
                    float cost = ability.getCost(player);

                    // If on cooldown (and allowed), we skip standard stamina check here and let the ability handle it,
                    // OR we assume the alternate effect might be free (like closing a rift).
                    // For safety, if NOT on cooldown, we enforce stamina.
                    if (!isOnCooldown && data.getStamina() < cost) return;

                    if (ability.onActivate(player.getWorld(), player)) {
                        // Only apply standard costs if we weren't already on cooldown
                        // (Prevents double dipping if the ability uses the bypass to just toggle something off)
                        if (!isOnCooldown) {
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
        boolean isSneaking = buf.readBoolean();

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