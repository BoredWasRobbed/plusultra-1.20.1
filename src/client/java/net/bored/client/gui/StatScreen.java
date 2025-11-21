package net.bored.client.gui;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Quirk;
import net.bored.network.PlusUltraNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class StatScreen extends Screen {

    private final IQuirkData data;
    private float openAnim = 0.0f; // 0.0 to 1.0

    // Layout constants
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 180;
    private static final int ROW_HEIGHT = 24;
    private static final int STAT_COLOR = 0x00E5FF; // Cyan
    private static final int LABEL_COLOR = 0xAAAAAA; // Gray

    public StatScreen() {
        super(Text.literal("Stats"));
        this.data = (IQuirkData) MinecraftClient.getInstance().player;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int startY = centerY - (PANEL_HEIGHT / 2) + 50;
        int btnX = centerX + 90;

        // Add Upgrade Buttons (Index: 0=Str, 1=HP, 2=Spd, 3=Stam, 4=Def)
        for (int i = 0; i < 5; i++) {
            final int statIndex = i;
            // Only show button if stat < 100
            this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(statIndex);
                        ClientPlayNetworking.send(PlusUltraNetworking.UPGRADE_STAT_PACKET, buf);
                    })
                    .dimensions(btnX, startY + (i * ROW_HEIGHT), 20, 20)
                    .build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnim = MathHelper.lerp(delta * 0.15f, openAnim, 1.0f);

        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(openAnim, openAnim, 1f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        int x = centerX - (PANEL_WIDTH / 2);
        int y = centerY - (PANEL_HEIGHT / 2);
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xCC000000);
        context.drawBorder(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF00E5FF);

        context.drawCenteredTextWithShadow(this.textRenderer, "Quirk Stats", centerX, y + 15, 0xFFFFFF);

        int points = data.getStatPoints();
        int pointsColor = points > 0 ? 0xFFFF55 : 0x555555;
        context.drawCenteredTextWithShadow(this.textRenderer, "Points Available: " + points, centerX, y + 30, pointsColor);

        // --- Player Model ---
        InventoryScreen.drawEntity(context, x + 50, y + 130, 50, (float)(x + 50) - mouseX, (float)(y + 75 - 50) - mouseY, this.client.player);

        // --- Quirk Name Display ---
        Quirk quirk = data.getQuirk();
        String quirkName = (quirk != null) ? quirk.getName().getString() : "None";
        int quirkColor = (quirk != null) ? quirk.getIconColor() : 0xAAAAAA;

        String display = "Quirk: " + quirkName;

        // Render Text lower down to avoid overlap
        // Calculate scale to fit inside left panel area (approx width 100)
        float scale = 1.0f;
        int maxWidth = 90;
        if (this.textRenderer.getWidth(display) > maxWidth) {
            scale = (float) maxWidth / this.textRenderer.getWidth(display);
        }

        context.getMatrices().push();
        context.getMatrices().translate(x + 50, y + 145, 0); // Position below model
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawCenteredTextWithShadow(this.textRenderer, display, 0, 0, quirkColor);
        context.getMatrices().pop();

        // --- Stats List ---
        int startY = y + 55;
        int labelX = centerX + 10;

        drawStatRow(context, "Strength", data.getStrengthStat(), labelX, startY, 0);
        drawStatRow(context, "Health", data.getHealthStat(), labelX, startY + ROW_HEIGHT, 1);
        drawStatRow(context, "Speed", data.getSpeedStat(), labelX, startY + (ROW_HEIGHT * 2), 2);
        drawStatRow(context, "Stamina", data.getStaminaStat(), labelX, startY + (ROW_HEIGHT * 3), 3);
        drawStatRow(context, "Defense", data.getDefenseStat(), labelX, startY + (ROW_HEIGHT * 4), 4);

        // Hide/Show Buttons logic
        int[] currentStats = {
                data.getStrengthStat(),
                data.getHealthStat(),
                data.getSpeedStat(),
                data.getStaminaStat(),
                data.getDefenseStat()
        };

        int buttonIndex = 0;
        for (var element : this.children()) {
            if (element instanceof ButtonWidget btn) {
                // Visible if user has points AND stat isn't maxed (100)
                boolean notMaxed = currentStats[buttonIndex] < 100;
                btn.visible = (points > 0) && notMaxed;
                buttonIndex++;
            }
        }

        super.render(context, mouseX, mouseY, delta);

        context.getMatrices().pop();
    }

    private void drawStatRow(DrawContext context, String name, int value, int x, int y, int index) {
        context.drawTextWithShadow(this.textRenderer, name, x, y + 6, LABEL_COLOR);

        // FIX 3: Just the number
        String valText = String.valueOf(value);
        context.drawTextWithShadow(this.textRenderer, valText, x + 60, y + 6, STAT_COLOR);

        int barX = x;
        int barWidth = 80;

        // FIX 2: Use 100.0f for percentage calculation
        float percentage = (float)value / 100.0f;
        int fill = (int)(percentage * barWidth);

        int barY = y + 16;
        context.fill(barX, barY, barX + barWidth, barY + 2, 0xFF333333);
        context.fill(barX, barY, barX + fill, barY + 2, STAT_COLOR);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}