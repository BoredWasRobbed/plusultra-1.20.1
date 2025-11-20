package net.bored.api.quirk;

import net.bored.api.data.IQuirkData;
import net.minecraft.entity.LivingEntity;
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

    private AwakeningCondition awakeningCondition = (user, data) -> false;

    public Quirk(Identifier id, int iconColor) {
        this.id = id;
        this.iconColor = iconColor;
        this.registerAbilities();
        this.registerAwakening();
    }

    public abstract void registerAbilities();

    public void registerAwakening() {
    }

    public void onTick(LivingEntity user) {
        if (user.getWorld().isClient) return;

        if (user instanceof IQuirkData data) {
            if (!data.isAwakened() && this.awakeningCondition.shouldAwaken(user, data)) {
                data.setAwakened(true);
                this.onAwaken(user);
            }

            if (data.isAwakened()) {
                this.onTickAwakened(user);
            }
        }
    }

    public void onAwaken(LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            player.sendMessage(Text.literal("QUIRK AWAKENED!").formatted(Formatting.RED, Formatting.BOLD), true);
        }
    }

    public void onTickAwakened(LivingEntity user) {
    }

    public void onEquip(LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            player.sendMessage(Text.literal("You have obtained: ").append(this.getName()), true);
        }
    }

    public void onUnequip(LivingEntity user) {
    }

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