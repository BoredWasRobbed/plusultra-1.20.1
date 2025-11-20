package net.bored.network;

import net.bored.PlusUltra;
import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class PlusUltraNetworking {

    public static final Identifier ACTIVATE_ABILITY_PACKET = new Identifier("plusultra", "activate_ability");
    public static final Identifier CYCLE_ABILITY_PACKET = new Identifier("plusultra", "cycle_ability");
    public static final Identifier SYNC_DATA_PACKET = new Identifier("plusultra", "sync_data");
    public static final Identifier AFO_OPERATION_PACKET = new Identifier("plusultra", "afo_operation");

    // NEW PACKETS
    public static final Identifier OPEN_STEAL_SELECTION_PACKET = new Identifier("plusultra", "open_steal_selection");
    public static final Identifier STEAL_CONFIRM_PACKET = new Identifier("plusultra", "steal_confirm");

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ACTIVATE_ABILITY_PACKET, PlusUltraNetworking::handleActivate);
        ServerPlayNetworking.registerGlobalReceiver(CYCLE_ABILITY_PACKET, PlusUltraNetworking::handleCycle);
        ServerPlayNetworking.registerGlobalReceiver(AFO_OPERATION_PACKET, PlusUltraNetworking::handleAFO);
        ServerPlayNetworking.registerGlobalReceiver(STEAL_CONFIRM_PACKET, PlusUltraNetworking::handleStealConfirm);
    }

    // ... existing handleActivate ...
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

                    boolean isOnCooldown = data.getCooldown(slot) > 0;
                    if (isOnCooldown && !ability.canUseWhileOnCooldown()) {
                        return;
                    }

                    float cost = ability.getCost(player);

                    if (!isOnCooldown && data.getStamina() < cost) return;

                    if (ability.onActivate(player.getWorld(), player)) {
                        if (!isOnCooldown) {
                            data.consumeStamina(cost);
                            data.setCooldown(slot, ability.getCooldown());

                            if (ability.grantsXpOnActivate()) {
                                data.addXp(5.0f);
                            }
                        }
                    }
                }
            }
        });
    }

    // ... existing handleCycle ...
    private static void handleCycle(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int direction = buf.readInt();
        boolean isSneaking = buf.readBoolean();

        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;

            if (isSneaking) {
                data.cycleSelectedAnchor(direction);
                player.sendMessage(Text.literal("Anchor Selected: " + (data.getSelectedAnchorIndex() + 1) + "/" + data.getWarpAnchorCount()).formatted(Formatting.LIGHT_PURPLE), true);
            } else {
                data.cycleSlot(direction);
            }
        });
    }

    // ... existing handleAFO ...
    private static void handleAFO(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int opCode = buf.readInt(); // 0 = Equip, 1 = Toggle Passive, 2 = Select Give
        String quirkId = buf.readString();

        server.execute(() -> {
            IQuirkData data = (IQuirkData) player;

            if (opCode == 0) {
                boolean hasQuirk = data.getStolenQuirks().contains(quirkId);
                boolean isAFOBase = quirkId.equals("plusultra:all_for_one") && data.isAllForOne();

                if (hasQuirk || isAFOBase) {
                    data.setQuirk(new Identifier(quirkId));
                    player.sendMessage(Text.literal("Equipped: " + quirkId).formatted(Formatting.GOLD), true);
                }
            } else if (opCode == 1) {
                if (!data.isAllForOne()) {
                    player.sendMessage(Text.literal("Only All For One can toggle multiple passives!").formatted(Formatting.RED), true);
                    return;
                }
                if (data.getStolenQuirks().contains(quirkId)) {
                    data.togglePassive(quirkId);
                    boolean nowActive = data.getActivePassives().contains(quirkId);
                    player.sendMessage(Text.literal("Passive " + (nowActive ? "ON" : "OFF") + ": " + quirkId).formatted(nowActive ? Formatting.GREEN : Formatting.RED), true);
                }
            } else if (opCode == 2) {
                if (!data.isAllForOne()) {
                    player.sendMessage(Text.literal("Only All For One can give quirks!").formatted(Formatting.RED), true);
                    return;
                }
                if (data.getStolenQuirks().contains(quirkId)) {
                    data.setQuirkToGive(quirkId);
                    player.sendMessage(Text.literal("Ready to Give: " + quirkId).formatted(Formatting.YELLOW), true);
                }
            }
        });
    }

    // NEW: Handle the actual steal after selection
    private static void handleStealConfirm(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int targetId = buf.readInt();
        String quirkIdToSteal = buf.readString();

        server.execute(() -> {
            Entity entity = player.getWorld().getEntityById(targetId);
            if (entity instanceof IQuirkData target && player instanceof IQuirkData attacker) {

                // Validation checks
                if (!attacker.isAllForOne() || !attacker.isStealActive()) return;
                if (!target.getStolenQuirks().contains(quirkIdToSteal) && !(target.hasQuirk() && target.getQuirk().getId().toString().equals(quirkIdToSteal))) {
                    player.sendMessage(Text.literal("Target no longer possesses that quirk.").formatted(Formatting.RED), true);
                    return;
                }

                // Execute Steal
                attacker.addStolenQuirk(quirkIdToSteal);

                // Remove from target
                target.removeStolenQuirk(quirkIdToSteal);
                if (target.hasQuirk() && target.getQuirk().getId().toString().equals(quirkIdToSteal)) {
                    target.setQuirk(null);
                }

                // Target Auto-swap fallback
                List<String> remaining = target.getStolenQuirks();
                if (!remaining.isEmpty()) {
                    String nextQuirkId = remaining.get(0);
                    target.setQuirk(new Identifier(nextQuirkId));
                }

                attacker.setStealActive(false);
                player.sendMessage(Text.literal("Stolen: " + quirkIdToSteal).formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);

                // Trigger Visuals
                PlusUltra.spawnBlackLightning(player.getServerWorld(), player.getPos(), entity.getPos());
            }
        });
    }
}