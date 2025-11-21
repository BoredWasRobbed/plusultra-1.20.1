package net.bored.client.gui;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class QuirkHud implements HudRenderCallback {

    private static final int STAMINA_COLOR = 0xFF00E5FF;
    private static final int AWAKENED_COLOR = 0xFFFF0000;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int COOLDOWN_COLOR = 0xFFFF5555;
    private static final int XP_COLOR = 0xFF00FF00;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        if (!(client.player instanceof IQuirkData data)) return;
        Quirk quirk = data.getQuirk();
        if (quirk == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        TextRenderer font = client.textRenderer;

        int x = width - 110;
        int y = height - 20;
        int barWidth = 100;
        int rightEdge = x + barWidth;

        // --- 1. STAMINA BAR ---
        float maxStamina = data.getMaxStamina();
        float currentStamina = data.getStamina();
        if (maxStamina <= 0) maxStamina = 1;
        int fillWidth = (int) ((currentStamina / maxStamina) * barWidth);

        context.fill(x, y, rightEdge, y + 5, BACKGROUND_COLOR);
        context.fill(x, y, x + fillWidth, y + 5, data.isAwakened() ? AWAKENED_COLOR : STAMINA_COLOR);
        context.drawText(font, "Stamina", x, y - 10, 0xFFFFFF, true);
        context.drawText(font, (int)currentStamina + "/" + (int)maxStamina, rightEdge - font.getWidth((int)currentStamina + "/" + (int)maxStamina), y - 10, 0xAAAAAA, true);

        // --- 2. ABILITIES ---
        List<Ability> abilities = quirk.getAbilities();
        int abilityY = y - 25;
        int selectedSlot = data.getSelectedSlot();

        for (int i = abilities.size() - 1; i >= 0; i--) {
            Ability ability = abilities.get(i);
            if (data.getLevel() < ability.getRequiredLevel()) continue;

            boolean isSelected = (i == selectedSlot);
            String displayText = ability.getName();
            int textColor;
            int cd = data.getCooldown(i);

            if (cd > 0) {
                displayText += String.format(" (%.1fs)", cd / 20.0f);
                textColor = COOLDOWN_COLOR;
            } else {
                // UPDATED: Use getCost(player) to reflect dynamic/scaled costs
                int cost = ability.getCost(client.player);
                displayText += " [" + cost + "]";
                textColor = isSelected ? 0xFFD700 : 0xAAAAAA;
            }

            if (isSelected) displayText = "> " + displayText + " <";

            // DRAW ABILITY NAME
            context.drawText(font, displayText, rightEdge - font.getWidth(displayText), abilityY, textColor, true);

            // DRAW INFO (Anchor/Rift) *ABOVE* THE NAME
            if (isSelected) {
                if (ability.getName().equals("Gate Anchor")) {
                    abilityY -= 10; // Move UP before drawing anchor text
                    int idx = data.getSelectedAnchorIndex();
                    int count = data.getWarpAnchorCount();
                    Vec3d pos = data.getWarpAnchorPos(idx);
                    String anchorText = (pos != null)
                            ? String.format("Anchor %d/%d: %.0f, %.0f, %.0f", (idx + 1), count, pos.x, pos.y, pos.z)
                            : "No Anchors Saved";
                    int anchorColor = (pos != null) ? 0xAAAAAA : 0xFF5555;
                    context.drawText(font, anchorText, rightEdge - font.getWidth(anchorText), abilityY, anchorColor, true);
                }
                else if (ability.getName().equals("Dimensional Rift")) {
                    int state = data.getPlacementState();
                    if (state > 0) {
                        abilityY -= 10; // Move UP
                        String info = (state == 1) ? "Select Point A" : "Select Point B";
                        context.drawText(font, info, rightEdge - font.getWidth(info), abilityY, 0x55FFFF, true);
                    }
                }
            }

            abilityY -= 12; // Move up for next ability
        }

        int headerY = abilityY - 5;

        // --- 3. QUIRK NAME ---
        String nameText = quirk.getName().getString();
        int nameColor = quirk.getIconColor();
        if (data.isAwakened()) {
            nameText = "§k||§r " + nameText + " §k||";
            nameColor = AWAKENED_COLOR;
        }
        context.drawText(font, nameText, rightEdge - font.getWidth(nameText), headerY, nameColor, true);

        // --- 4. LEVEL & XP (High above name) ---
        int levelY = headerY - 20;

        int xpBarWidth = 40;
        int xpBarX = rightEdge - xpBarWidth;
        context.fill(xpBarX, levelY + 10, rightEdge, levelY + 11, BACKGROUND_COLOR);

        float maxXp = data.getMaxXp();
        float currentXp = data.getXp();
        int xpFill = (int)((currentXp / maxXp) * xpBarWidth);
        context.fill(xpBarX, levelY + 10, xpBarX + xpFill, levelY + 11, XP_COLOR);

        String lvlText = "Lvl " + data.getLevel();
        context.drawText(font, lvlText, rightEdge - font.getWidth(lvlText), levelY, 0xFFFF55, true);
    }
}