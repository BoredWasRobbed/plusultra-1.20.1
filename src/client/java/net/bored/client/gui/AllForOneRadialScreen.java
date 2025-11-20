package net.bored.client.gui;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Quirk;
import net.bored.network.PlusUltraNetworking;
import net.bored.registry.QuirkRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AllForOneRadialScreen extends Screen {

    private final IQuirkData data;

    public AllForOneRadialScreen(IQuirkData data) {
        super(Text.literal("All For One"));
        this.data = data;
    }

    // Helper to get valid display list
    private List<String> getDisplayQuirks() {
        List<String> quirks = new ArrayList<>();

        // FIXED: Only add AFO if the player is actually an AFO user
        if (data.isAllForOne()) {
            quirks.add("plusultra:all_for_one");
        }

        // Add owned quirks (inventory)
        for (String q : data.getStolenQuirks()) {
            // Avoid duplicates in the visual list
            if (!quirks.contains(q)) {
                quirks.add(q);
            }
        }
        return quirks;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.renderBackground(context);

        int centerX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, "Quirk Selection", centerX, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Left Click: Equip | Right Click: Toggle Passive | Middle Click: Select to Give", centerX, 35, 0xAAAAAA);

        List<String> quirks = getDisplayQuirks();

        int y = 60;
        for (String qId : quirks) {
            String displayName = qId;
            Identifier id = Identifier.tryParse(qId);
            if (id != null) {
                Quirk q = QuirkRegistry.get(id);
                if (q != null) displayName = q.getName().getString();
            }

            int color = 0xFFFFFF;
            String prefix = "";

            Identifier current = data.getQuirk() != null ? data.getQuirk().getId() : null;
            if (current != null && current.toString().equals(qId)) {
                color = 0xFFFF00;
                prefix = "[EQUIPPED] ";
            }

            if (data.getActivePassives().contains(qId)) {
                prefix += "(Passive ON) ";
            }

            if (qId.equals(data.getQuirkToGive())) {
                prefix += "{GIVE READY} ";
                color = 0xFFA500;
            }

            String fullText = prefix + displayName;
            int textWidth = this.textRenderer.getWidth(fullText);
            int x = centerX - (textWidth / 2);

            if (mouseY >= y && mouseY < y + 12 && mouseX >= x && mouseX <= x + textWidth) {
                context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x80FFFFFF);
            }

            context.drawTextWithShadow(this.textRenderer, fullText, x, y, color);
            y += 15;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        List<String> quirks = getDisplayQuirks();

        int y = 60;
        for (String qId : quirks) {
            String displayName = qId;
            Identifier id = Identifier.tryParse(qId);
            if (id != null) {
                Quirk q = QuirkRegistry.get(id);
                if (q != null) displayName = q.getName().getString();
            }

            String prefix = "";
            Identifier current = data.getQuirk() != null ? data.getQuirk().getId() : null;
            if (current != null && current.toString().equals(qId)) prefix = "[EQUIPPED] ";
            if (data.getActivePassives().contains(qId)) prefix += "(Passive ON) ";
            if (qId.equals(data.getQuirkToGive())) prefix += "{GIVE READY} ";

            String fullText = prefix + displayName;
            int textWidth = this.textRenderer.getWidth(fullText);
            int x = centerX - (textWidth / 2);

            if (mouseY >= y && mouseY < y + 12 && mouseX >= x && mouseX <= x + textWidth) {
                int opCode = -1;
                if (button == 0) opCode = 0;
                if (button == 1) opCode = 1;
                if (button == 2) opCode = 2;

                if (opCode != -1) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(opCode);
                    buf.writeString(qId);
                    ClientPlayNetworking.send(PlusUltraNetworking.AFO_OPERATION_PACKET, buf);
                    // Keep menu open for rapid toggling
                    return true;
                }
            }
            y += 15;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}