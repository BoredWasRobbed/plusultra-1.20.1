package net.bored.client.gui;

import net.bored.network.PlusUltraNetworking;
import net.bored.registry.QuirkRegistry;
import net.bored.api.quirk.Quirk;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class StealSelectionScreen extends Screen {

    private final int targetId;
    private final List<String> quirks;

    public StealSelectionScreen(int targetId, List<String> quirks) {
        super(Text.literal("Steal Quirk"));
        this.targetId = targetId;
        this.quirks = quirks;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, "Choose Quirk to Steal", centerX, centerY - 80, 0xFF0000);

        int y = centerY - 50;
        for (String qId : quirks) {
            String displayName = qId;
            Identifier id = Identifier.tryParse(qId);
            if (id != null) {
                Quirk q = QuirkRegistry.get(id);
                if (q != null) displayName = q.getName().getString();
            }

            // Don't show AFO itself if somehow present (safety)
            if (qId.equals("plusultra:all_for_one")) continue;

            int textWidth = this.textRenderer.getWidth(displayName);
            int x = centerX - (textWidth / 2);

            boolean hovered = (mouseY >= y && mouseY < y + 12 && mouseX >= x && mouseX <= x + textWidth);

            if (hovered) {
                context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x80FF0000);
            }

            context.drawTextWithShadow(this.textRenderer, displayName, x, y, hovered ? 0xFFFF00 : 0xFFFFFF);
            y += 15;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int y = centerY - 50;

        for (String qId : quirks) {
            if (qId.equals("plusultra:all_for_one")) continue;

            String displayName = qId;
            Identifier id = Identifier.tryParse(qId);
            if (id != null) {
                Quirk q = QuirkRegistry.get(id);
                if (q != null) displayName = q.getName().getString();
            }

            int textWidth = this.textRenderer.getWidth(displayName);
            int x = centerX - (textWidth / 2);

            if (mouseY >= y && mouseY < y + 12 && mouseX >= x && mouseX <= x + textWidth) {
                // Send confirmation packet
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(targetId);
                buf.writeString(qId);
                ClientPlayNetworking.send(PlusUltraNetworking.STEAL_CONFIRM_PACKET, buf);
                this.close();
                return true;
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