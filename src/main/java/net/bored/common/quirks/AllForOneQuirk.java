package net.bored.common.quirks;

import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Ability;
import net.bored.api.quirk.Quirk;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class AllForOneQuirk extends Quirk {

    public AllForOneQuirk() {
        super(new Identifier("plusultra", "all_for_one"), 0x000000); // Black Icon
    }

    @Override
    public void registerAbilities() {
        // Ability 1: Steal - Available at Level 1
        this.addAbility(new Ability("Steal", 100, 50, 1) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                data.setStealActive(true);
                data.setGiveActive(false);

                if (user instanceof PlayerEntity player) {
                    player.sendMessage(Text.literal("Steal Ready: Hit a target to take their quirk.").formatted(Formatting.DARK_RED), true);
                }
                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.5f, 0.5f);
                return true;
            }
        });

        // Ability 2: Give - Available at Level 5 (Changed from 1)
        this.addAbility(new Ability("Give", 100, 50, 5) {
            @Override
            public boolean onActivate(World world, LivingEntity user) {
                if (world.isClient || !(user instanceof IQuirkData data)) return false;

                String toGive = data.getQuirkToGive();
                if (toGive == null || toGive.isEmpty()) {
                    if (user instanceof PlayerEntity player) {
                        player.sendMessage(Text.literal("No quirk selected to give! (Press V)").formatted(Formatting.RED), true);
                    }
                    return false;
                }

                data.setGiveActive(true);
                data.setStealActive(false);

                if (user instanceof PlayerEntity player) {
                    player.sendMessage(Text.literal("Give Ready: Hit a target to grant " + toGive).formatted(Formatting.GOLD), true);
                }
                world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 1.5f);
                return true;
            }
        });
    }
}