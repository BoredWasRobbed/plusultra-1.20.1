package net.bored.api.quirk;

import net.bored.api.data.IQuirkData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public abstract class Quirk {

    private final Identifier id;
    private final List<Ability> abilities = new ArrayList<>();
    private final int iconColor;

    // AWAKENING SYSTEM
    private AwakeningCondition awakeningCondition = (player, data) -> false; // Default: Never awaken

    public Quirk(Identifier id, int iconColor) {
        this.id = id;
        this.iconColor = iconColor;
        this.registerAbilities();
        this.registerAwakening(); // New hook for subclasses
    }

    public abstract void registerAbilities();

    /**
     * Override this to set your awakening condition.
     * Example: this.setAwakeningCondition((player, data) -> player.getHealth() < 5);
     */
    public void registerAwakening() {
        // Default implementation does nothing
    }

    public void onTick(PlayerEntity player) {
        if (player.getWorld().isClient) return;

        // Check Awakening Logic
        if (player instanceof IQuirkData data) {
            // If not yet awakened, check condition
            if (!data.isAwakened() && this.awakeningCondition.shouldAwaken(player, data)) {
                data.setAwakened(true);
                this.onAwaken(player);
            }

            // If awakened, run passive awakened effects
            if (data.isAwakened()) {
                this.onTickAwakened(player);
            }
        }
    }

    /**
     * Triggers once when the awakening happens.
     */
    public void onAwaken(PlayerEntity player) {
        player.sendMessage(Text.literal("QUIRK AWAKENED!").formatted(Formatting.RED, Formatting.BOLD), true);
        // Play sound or particle effects here
    }

    /**
     * Runs every tick ONLY if awakened.
     */
    public void onTickAwakened(PlayerEntity player) {
        // Override for awakened passives
    }

    public void onEquip(PlayerEntity player) {
        player.sendMessage(Text.literal("You have obtained: ").append(this.getName()), true);
    }

    public void onUnequip(PlayerEntity player) {
        // Cleanup logic
    }

    // --- Getters & Setters ---

    protected void setAwakeningCondition(AwakeningCondition condition) {
        this.awakeningCondition = condition;
    }

    protected void addAbility(Ability ability) {
        this.abilities.add(ability);
    }

    public List<Ability> getAbilities() {
        return abilities;
    }

    public Ability getAbility(int index) {
        if (index >= 0 && index < abilities.size()) {
            return abilities.get(index);
        }
        return null;
    }

    public Identifier getId() {
        return id;
    }

    public Text getName() {
        return Text.translatable("quirk." + id.getNamespace() + "." + id.getPath());
    }

    public int getIconColor() {
        return iconColor;
    }
}