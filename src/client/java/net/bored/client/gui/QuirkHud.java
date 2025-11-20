package net.bored.client.gui;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class QuirkHud implements HudRenderCallback {

    private static final int STAMINA_COLOR = 0xFF00E5FF; // Cyan
    private static final int AWAKENED_COLOR = 0xFFFF0000; // Red
    private static final int BACKGROUND_COLOR = 0x80000000; // Semi-transparent Black
    private static final int COOLDOWN_COLOR = 0xFFFF5555; // Light Red

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        if (!(client.player instanceof IQuirkData data)) return;
        Quirk quirk = data.getQuirk();
        if (quirk == null) return;

        // REMOVED: data.tickCooldowns()
        // The PlayerQuirkMixin already handles ticking this 20 times a second.
        // Calling it here causes it to tick every frame (FPS), making it too fast.

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        TextRenderer font = client.textRenderer;

        int x = width - 110;
        int y = height - 20;
        int barWidth = 100;
        int rightEdge = x + barWidth;

        // --- RENDER STAMINA BAR ---

        float maxStamina = 100.0f;
        float currentStamina = data.getStamina();
        int barHeight = 5;
        int fillWidth = (int) ((currentStamina / maxStamina) * barWidth);

        context.fill(x, y, rightEdge, y + barHeight, BACKGROUND_COLOR);
        int color = data.isAwakened() ? AWAKENED_COLOR : STAMINA_COLOR;
        context.fill(x, y, x + fillWidth, y + barHeight, color);

        context.drawText(font, "Stamina", x, y - 10, 0xFFFFFF, true);
        String staminaText = (int)currentStamina + "/" + (int)maxStamina;
        int staminaTextWidth = font.getWidth(staminaText);
        context.drawText(font, staminaText, rightEdge - staminaTextWidth, y - 10, 0xAAAAAA, true);

        // --- RENDER ABILITIES ---

        List<Ability> abilities = quirk.getAbilities();
        if (abilities.isEmpty()) return;

        int abilityY = y - 25;
        int selectedSlot = data.getSelectedSlot();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            Ability ability = abilities.get(i);
            boolean isSelected = (i == selectedSlot);

            String displayText = ability.getName();
            int textColor;

            int cd = data.getCooldown(i);

            if (cd > 0) {
                // Cooldown Display
                float seconds = cd / 20.0f;
                displayText = displayText + String.format(" (%.1fs)", seconds);
                textColor = COOLDOWN_COLOR;
            } else {
                // Ready Display
                displayText = displayText + " [" + ability.getStaminaCost() + "]";
                textColor = isSelected ? 0xFFD700 : 0xAAAAAA;
            }

            if (isSelected) {
                displayText = "> " + displayText + " <";
            }

            int textWidth = font.getWidth(displayText);
            context.drawText(font, displayText, rightEdge - textWidth, abilityY, textColor, true);

            abilityY -= 12;
        }

        // --- RENDER QUIRK NAME (Uncapitalized) ---

        String nameText = quirk.getName().getString(); // REMOVED .toUpperCase()
        if (data.isAwakened()) {
            nameText = "§k||§r " + nameText + " §k||";
            int nameWidth = font.getWidth(nameText);
            context.drawText(font, nameText, rightEdge - nameWidth, abilityY - 5, AWAKENED_COLOR, true);
        } else {
            int nameWidth = font.getWidth(nameText);
            context.drawText(font, nameText, rightEdge - nameWidth, abilityY - 5, quirk.getIconColor(), true);
        }
    }
}