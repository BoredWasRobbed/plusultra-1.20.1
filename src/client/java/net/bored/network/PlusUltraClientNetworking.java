package net.bored.network;

import net.bored.api.data.IQuirkData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class PlusUltraClientNetworking {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetworking.SYNC_DATA_PACKET, PlusUltraClientNetworking::handleSync);
    }

    private static void handleSync(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        // Read data
        boolean hasQuirk = buf.readBoolean();
        String quirkIdStr = hasQuirk ? buf.readString() : null;
        boolean isAwakened = buf.readBoolean();
        int slot = buf.readInt();
        float stamina = buf.readFloat();

        // NEW: Read Cooldowns
        int cdLength = buf.readInt();
        int[] cooldowns = new int[cdLength];
        for (int i = 0; i < cdLength; i++) {
            cooldowns[i] = buf.readInt();
        }

        client.execute(() -> {
            if (client.player == null) return;
            IQuirkData data = (IQuirkData) client.player;

            if (hasQuirk) {
                data.setQuirk(new Identifier(quirkIdStr));
            } else {
                data.setQuirk(null);
            }

            data.setAwakened(isAwakened);
            data.setSelectedSlot(slot);
            data.setStamina(stamina);

            // Apply Cooldowns
            for (int i = 0; i < cdLength; i++) {
                data.setCooldown(i, cooldowns[i]);
            }
        });
    }
}