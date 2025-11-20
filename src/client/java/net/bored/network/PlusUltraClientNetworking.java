package net.bored.network;

import net.bored.api.data.IQuirkData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class PlusUltraClientNetworking {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlusUltraNetworking.SYNC_DATA_PACKET, PlusUltraClientNetworking::handleSync);
    }

    private static void handleSync(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        boolean hasQuirk = buf.readBoolean();
        String quirkIdStr = hasQuirk ? buf.readString() : null;
        boolean isAwakened = buf.readBoolean();
        int slot = buf.readInt();
        float stamina = buf.readFloat();
        float maxStamina = buf.readFloat();
        int level = buf.readInt();
        float xp = buf.readFloat();

        int cdLength = buf.readInt();
        int[] cooldowns = new int[cdLength];
        for (int i = 0; i < cdLength; i++) cooldowns[i] = buf.readInt();

        int anchorCount = buf.readInt();
        Vec3d[] anchors = new Vec3d[anchorCount];
        String[] dims = new String[anchorCount];
        for(int i=0; i<anchorCount; i++) {
            anchors[i] = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            dims[i] = buf.readString();
        }
        int selectedAnchor = buf.readInt();

        // NEW: Read Placement State
        int placementState = buf.readInt();

        client.execute(() -> {
            if (client.player == null) return;
            IQuirkData data = (IQuirkData) client.player;

            if (hasQuirk) data.setQuirk(new Identifier(quirkIdStr));
            else data.setQuirk(null);

            data.setAwakened(isAwakened);
            data.setSelectedSlot(slot);
            data.setMaxStamina(maxStamina);
            data.setStamina(stamina);
            data.setLevel(level);
            data.setXp(xp);
            for (int i = 0; i < cdLength; i++) data.setCooldown(i, cooldowns[i]);

            for(int i=0; i<anchorCount; i++) {
                data.addWarpAnchor(anchors[i], RegistryKey.of(RegistryKeys.WORLD, new Identifier(dims[i])));
            }
            int current = data.getSelectedAnchorIndex();
            int diff = selectedAnchor - current;
            if (diff != 0) data.cycleSelectedAnchor(diff);

            // Set State
            data.setPlacementState(placementState, null, GameMode.SURVIVAL); // Client doesn't need original pos memory
        });
    }
}