package net.bored.network;

import net.bored.api.data.IQuirkData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
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
        int entityId = buf.readInt(); // Read Entity ID

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
        int placementState = buf.readInt();
        boolean regenActive = buf.readBoolean();

        // AFO Data
        boolean isAFO = buf.readBoolean();
        int stolenCount = buf.readInt();
        String[] stolen = new String[stolenCount];
        for(int i=0; i<stolenCount; i++) stolen[i] = buf.readString();

        int passiveCount = buf.readInt();
        String[] passives = new String[passiveCount];
        for(int i=0; i<passiveCount; i++) passives[i] = buf.readString();

        boolean stealActive = buf.readBoolean();
        boolean giveActive = buf.readBoolean();
        String quirkToGive = buf.readString();

        client.execute(() -> {
            if (client.world == null) return;

            Entity entity = client.world.getEntityById(entityId);
            if (!(entity instanceof IQuirkData data)) return; // Works for Player AND Mobs now

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

            data.setPlacementState(placementState, null, GameMode.SURVIVAL);
            data.setRegenActive(regenActive);

            data.setAllForOne(isAFO);

            data.getStolenQuirks().clear();
            for(String s : stolen) data.addStolenQuirk(s);

            data.getActivePassives().clear();
            for(String s : passives) data.togglePassive(s);

            data.setStealActive(stealActive);
            data.setGiveActive(giveActive);
            data.setQuirkToGive(quirkToGive);
        });
    }
}