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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class StatScreen extends Screen {

    private final IQuirkData data;
    private float animationProgress = 0.0f;

    // Layout constants
    private static final int STAT_COLOR = 0x00E5FF; // Cyan
    private static final int STAT_BG_COLOR = 0x40000000; // Transparent Black
    private static final int LABEL_COLOR = 0xDDDDDD; // Light Gray

    // Widgets
    private CustomStatButton[] upgradeButtons = new CustomStatButton[5];
    private TextFieldWidget amountField;

    // Store initial positions to calculate slide animations
    private int baseButtonX;
    private int[] baseButtonYs = new int[5];
    private int baseFieldX;
    private int baseFieldY;
    private int statsStartX;
    private int statsStartY;

    public StatScreen() {
        super(Text.literal("Stats"));
        this.data = (IQuirkData) MinecraftClient.getInstance().player;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // FIXED: Reverted to original position logic (Center + Offset)
        // instead of centering within the remaining right-hand space.
        this.statsStartX = centerX + 40;
        this.statsStartY = centerY - 60;
        int spacing = 30;

        // Position Buttons next to the bars
        this.baseButtonX = this.statsStartX + 140; // 130 (bar) + 10 padding

        // Position Field BELOW the stat bars
        // Field width is 40. Center of bar is statsStartX + 65.
        // Field X = statsStartX + 65 - 20 = statsStartX + 45.
        this.baseFieldX = this.statsStartX + 45;
        this.baseFieldY = this.statsStartY + (5 * spacing) + 5; // 5px padding below last bar

        // --- Amount Input Field ---
        this.amountField = new TextFieldWidget(this.textRenderer, baseFieldX, baseFieldY, 40, 16, Text.literal("Amount"));
        this.amountField.setText("1");
        this.amountField.setMaxLength(3);
        this.amountField.setChangedListener(text -> {
            // Force numbers only
            if (!text.matches("\\d*")) {
                this.amountField.setText(text.replaceAll("[^\\d]", ""));
            }
        });
        this.addDrawableChild(this.amountField);

        // --- Custom Buttons ---
        for (int i = 0; i < 5; i++) {
            final int statIndex = i;
            this.baseButtonYs[i] = this.statsStartY + (i * spacing) - 6;

            upgradeButtons[i] = new CustomStatButton(baseButtonX, baseButtonYs[i], 20, 20, Text.literal("+"), button -> {
                int amount = 1;
                try {
                    String text = amountField.getText();
                    if (text != null && !text.isEmpty()) {
                        amount = Integer.parseInt(text);
                    }
                } catch (NumberFormatException ignored) {}

                // Don't send if 0 or negative
                if (amount <= 0) return;

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(statIndex);
                buf.writeInt(amount); // Send amount
                ClientPlayNetworking.send(PlusUltraNetworking.UPGRADE_STAT_PACKET, buf);
            });
            this.addDrawableChild(upgradeButtons[i]);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Smooth animation from 0.0 to 1.0
        animationProgress = MathHelper.lerp(delta * 0.1f, animationProgress, 1.0f);
        if (Math.abs(1.0f - animationProgress) < 0.001f) animationProgress = 1.0f;

        this.renderInGameBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // --- Player Model & Info ---
        float leftSlideOffset = (1.0f - animationProgress) * -150.0f;
        float leftAlpha = animationProgress;
        int playerX = centerX - 100 + (int)leftSlideOffset;
        int playerY = centerY + 60;

        int glowColorStart = (int)(leftAlpha * 128) << 24; // Black with alpha
        int glowColorEnd = 0x00000000;
        context.fillGradient(playerX - 70, centerY - 120, playerX + 70, centerY + 90, glowColorStart, glowColorEnd);

        InventoryScreen.drawEntity(context, playerX, playerY, 80, (float)(playerX) - mouseX, (float)(playerY - 120) - mouseY, this.client.player);

        context.getMatrices().push();
        context.getMatrices().translate(playerX, centerY - 130, 0);
        float titleScale = 1.5f;
        context.getMatrices().scale(titleScale, titleScale, 1f);
        Quirk quirk = data.getQuirk();
        String quirkName = (quirk != null) ? quirk.getName().getString().toUpperCase() : "QUIRKLESS";
        int quirkColor = (quirk != null) ? quirk.getIconColor() : 0xAAAAAA;
        context.drawCenteredTextWithShadow(this.textRenderer, quirkName, 0, 0, quirkColor);
        context.getMatrices().pop();

        String levelText = "LVL " + data.getLevel();
        context.drawCenteredTextWithShadow(this.textRenderer, levelText, playerX, playerY + 15, 0xFFD700);

        int xpBarWidth = 100;
        int xpBarX = playerX - (xpBarWidth / 2);
        int xpBarY = playerY + 28;
        float xpRatio = data.getXp() / data.getMaxXp();
        context.fill(xpBarX, xpBarY, xpBarX + xpBarWidth, xpBarY + 2, 0x80000000);
        context.fill(xpBarX, xpBarY, xpBarX + (int)(xpBarWidth * xpRatio), xpBarY + 2, 0xFF00E5FF);

        // --- Stats List ---
        int spacing = 30;
        int points = data.getStatPoints();

        // Points Header Animation
        if (points > 0) {
            float pulse = (float)Math.sin(System.currentTimeMillis() / 500.0) * 0.05f + 1.0f;
            int pointsAlpha = (int)(animationProgress * 255) << 24;
            context.getMatrices().push();
            // Center header relative to stats block (statsStartX + 65 is center)
            context.getMatrices().translate(this.statsStartX + 65, this.statsStartY - 30, 0);
            context.getMatrices().scale(pulse, pulse, 1f);
            context.drawCenteredTextWithShadow(this.textRenderer, points + " PTS AVAILABLE", 0, 0, 0xFFFF55 | pointsAlpha);
            context.getMatrices().pop();
        }

        // Animate slide in
        drawAnimatedStatRow(context, "Strength", data.getStrengthStat(), this.statsStartX, this.statsStartY, 0, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Health", data.getHealthStat(), this.statsStartX, this.statsStartY + spacing, 1, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Speed", data.getSpeedStat(), this.statsStartX, this.statsStartY + (spacing * 2), 2, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Stamina", data.getStaminaStat(), this.statsStartX, this.statsStartY + (spacing * 3), 3, mouseX, mouseY, points);
        drawAnimatedStatRow(context, "Defense", data.getDefenseStat(), this.statsStartX, this.statsStartY + (spacing * 4), 4, mouseX, mouseY, points);

        // --- Animate Buttons & Field ---
        // Calculate slide offset (same math as rows but uniform for controls)
        float controlProgress = MathHelper.clamp((animationProgress * 1.5f) - 0.0f, 0.0f, 1.0f); // Delay 0
        controlProgress = 1.0f - (float)Math.pow(1.0f - controlProgress, 3);
        float xOffset = (1.0f - controlProgress) * 100.0f;

        boolean hasPoints = points > 0;

        // "Buy:" Label - Below stats, to left of field
        if (hasPoints) {
            int alpha = (int)(animationProgress * 255);
            if (alpha > 5) {
                // Position label to the left of the field
                int labelX = this.baseFieldX - 25 + (int)xOffset;
                int labelY = this.baseFieldY + 4; // Center vertically with field (16px high)
                context.drawTextWithShadow(this.textRenderer, "Buy:", labelX, labelY, 0xAAAAAA | (alpha << 24));
            }
        }

        // Update Text Field Position
        if (this.amountField != null) {
            this.amountField.setX(this.baseFieldX + (int)xOffset);
            this.amountField.visible = hasPoints;
        }

        // Update Buttons
        int[] currentStats = {data.getStrengthStat(), data.getHealthStat(), data.getSpeedStat(), data.getStaminaStat(), data.getDefenseStat()};
        for (int i = 0; i < 5; i++) {
            if (upgradeButtons[i] != null) {
                boolean notMaxed = currentStats[i] < 50;
                upgradeButtons[i].visible = hasPoints && notMaxed;
                upgradeButtons[i].setX(this.baseButtonX + (int)xOffset);
            }
        }

        // Render Children (Buttons & Text Field)
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.Drawable d) {
                d.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawAnimatedStatRow(DrawContext context, String name, int value, int x, int y, int index, int mouseX, int mouseY, int pointsAvailable) {
        float rowDelay = index * 0.1f;
        float rowProgress = MathHelper.clamp((animationProgress * 1.5f) - rowDelay, 0.0f, 1.0f);
        rowProgress = 1.0f - (float)Math.pow(1.0f - rowProgress, 3);

        float xOffset = (1.0f - rowProgress) * 100.0f;
        int currentX = x + (int)xOffset;

        int alpha = (int)(rowProgress * 255);
        if (alpha < 5) return;
        int colorAlpha = alpha << 24;

        context.drawTextWithShadow(this.textRenderer, name, currentX, y, LABEL_COLOR | colorAlpha);

        String valText = String.valueOf(value);
        int valColor = (value >= 50) ? 0xFFA500 : STAT_COLOR;
        context.drawTextWithShadow(this.textRenderer, valText, currentX + 110, y, valColor | colorAlpha);

        int barX = currentX;
        int barY = y + 12;
        int barWidth = 130;
        int barHeight = 4;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, STAT_BG_COLOR);

        float fillRatio = (float)value / 50.0f;
        int fillWidth = (int)(barWidth * fillRatio * rowProgress);

        int barColor = STAT_COLOR;
        if (value >= 50) barColor = 0xFFA500;
        else if (pointsAvailable > 0) barColor = 0xFFFFFF;

        context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor | 0xFF000000);

        if (fillWidth > 0) {
            context.fill(barX + fillWidth - 1, barY - 1, barX + fillWidth + 1, barY + barHeight + 1, 0xFFFFFFFF);
        }

        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= y && mouseY <= y + 16) {
            context.drawBorder(barX - 1, barY - 1, barWidth + 2, barHeight + 2, 0x80FFFFFF);
        }
    }

    @Override
    public void renderBackground(DrawContext context) {
        // No default dirt background
    }

    public void renderInGameBackground(DrawContext context) {
        context.fillGradient(0, 0, this.width, this.height, 0xCC000000, 0xAA000000);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- Custom Button Class ---
    private class CustomStatButton extends ButtonWidget {
        public CustomStatButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            // Opacity calculation from outer class animationProgress
            int alpha = (int)(StatScreen.this.animationProgress * 255);
            if (alpha < 5) return;

            // Manually check hover since we are overriding render entirely
            this.hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;

            // Apply alpha to colors
            int baseColor = this.isHovered() ? 0xFFFFFF : 0x00E5FF;
            int borderColor = baseColor | (alpha << 24);

            int bgBase = this.isHovered() ? 0x4000E5FF : 0x20000000;
            // Combine alpha with background (bit tricky with pre-existing alpha in hex)
            // Just scaling the alpha channel roughly
            int bgAlpha = (bgBase >> 24) & 0xFF;
            int scaledBgAlpha = (int)(bgAlpha * StatScreen.this.animationProgress);
            int bgColor = (scaledBgAlpha << 24) | (bgBase & 0x00FFFFFF);

            // Draw Box Background
            context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

            // Draw Border
            context.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);

            // Draw Text ("+")
            int textX = this.getX() + (this.width / 2) - (MinecraftClient.getInstance().textRenderer.getWidth(this.getMessage()) / 2);
            int textY = this.getY() + (this.height / 2) - 4;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), textX, textY, borderColor);
        }
    }
}