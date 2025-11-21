package net.bored.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bored.api.data.IQuirkData;
import net.bored.api.quirk.Quirk;
import net.bored.registry.QuirkRegistry;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Collection;

public class PlusUltraCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("plusultra")
                .requires(source -> source.hasPermissionLevel(2))

                .then(CommandManager.literal("quirk")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                                .suggests(QUIRK_SUGGESTIONS)
                                                .executes(PlusUltraCommand::setQuirk))))
                        // NEW: Add Command
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                                .suggests(QUIRK_SUGGESTIONS)
                                                .executes(PlusUltraCommand::addQuirk))))
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .executes(PlusUltraCommand::clearQuirk)))
                        .then(CommandManager.literal("list").executes(PlusUltraCommand::listQuirks))
                )

                .then(CommandManager.literal("stamina")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0))
                                                .executes(ctx -> setStamina(ctx, false)))))
                        .then(CommandManager.literal("max")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(1))
                                                .executes(ctx -> setStamina(ctx, true)))))
                )

                .then(CommandManager.literal("level")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1))
                                                .executes(PlusUltraCommand::setLevel))))
                )
                .then(CommandManager.literal("xp")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(1))
                                                .executes(PlusUltraCommand::addXp))))
                )
                .then(CommandManager.literal("stat")
                        .then(CommandManager.literal("give")
                                .then(CommandManager.argument("target", EntityArgumentType.players())
                                        .then(CommandManager.argument("points", IntegerArgumentType.integer(1))
                                                .executes(PlusUltraCommand::giveStatPoints))))
                )
        );
    }

    private static final SuggestionProvider<ServerCommandSource> QUIRK_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(QuirkRegistry.QUIRK.getIds(), builder);

    private static int setQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        Identifier quirkId = IdentifierArgumentType.getIdentifier(context, "quirk_id");
        Quirk quirk = QuirkRegistry.get(quirkId);
        if (quirk == null) return 0;

        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;

            // 1. WIPE STORAGE: Ensure no previous quirks remain
            data.getStolenQuirks().clear();

            // 2. RESET AFO FLAG: Ensure All For One status is cleared
            // This fixes the issue where switching off AFO leaves the flag true
            data.setAllForOne(false);

            // 3. Set Active Quirk (Overwrites active slot)
            data.setQuirk(quirkId);
            quirk.onEquip(player);
            player.sendMessage(Text.literal("Your quirk has been set to ").append(quirk.getName()).formatted(Formatting.GREEN), false);
        }
        context.getSource().sendFeedback(() -> Text.literal("Set quirk (and wiped storage/AFO) for " + players.size() + " players").formatted(Formatting.GREEN), true);
        return 1;
    }

    // NEW: Add Quirk to Storage
    private static int addQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        Identifier quirkId = IdentifierArgumentType.getIdentifier(context, "quirk_id");

        // Validate quirk exists
        Quirk quirk = QuirkRegistry.get(quirkId);
        if (quirk == null) return 0;

        String idString = quirkId.toString();

        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;

            // 1. Add CURRENTLY ACTIVE quirk to storage if it's not already there.
            if (data.hasQuirk()) {
                String activeId = data.getQuirk().getId().toString();
                if (!data.getStolenQuirks().contains(activeId)) {
                    data.addStolenQuirk(activeId);
                    // Optional: Notify player?
                    // player.sendMessage(Text.literal("Saved previous quirk to storage: " + activeId).formatted(Formatting.GRAY), false);
                }
            }

            // 2. Add NEW quirk to storage if it's not already there (and not active).
            boolean isActive = data.hasQuirk() && data.getQuirk().getId().toString().equals(idString);
            boolean isStored = data.getStolenQuirks().contains(idString);

            if (!isActive && !isStored) {
                data.addStolenQuirk(idString);
                // UPDATED: Use Formal Name instead of ID
                player.sendMessage(Text.literal("Added quirk to storage: ").append(quirk.getName()).formatted(Formatting.AQUA), false);
            }
        }
        context.getSource().sendFeedback(() -> Text.literal("Added quirk to storage for " + players.size() + " players").formatted(Formatting.AQUA), true);
        return 1;
    }

    private static int clearQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;
            data.setQuirk(null);
            data.getStolenQuirks().clear();
            data.setAllForOne(false); // Also clear AFO flag on clear
            player.sendMessage(Text.literal("Your quirk has been cleared.").formatted(Formatting.YELLOW), false);
        }
        context.getSource().sendFeedback(() -> Text.literal("Cleared quirk for " + players.size() + " players").formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int listQuirks(CommandContext<ServerCommandSource> context) {
        // UPDATED: List now shows formal names
        for (Identifier id : QuirkRegistry.QUIRK.getIds()) {
            Quirk q = QuirkRegistry.get(id);
            if (q != null) {
                context.getSource().sendFeedback(() -> Text.literal("- ").append(q.getName()).formatted(Formatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int setStamina(CommandContext<ServerCommandSource> context, boolean isMax) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        float amount = FloatArgumentType.getFloat(context, "amount");
        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;
            if (isMax) data.setMaxStamina(amount);
            else data.setStamina(amount);
            player.sendMessage(Text.literal("Your " + (isMax ? "MAX " : "") + "stamina was set to " + amount).formatted(Formatting.AQUA), false);
        }
        context.getSource().sendFeedback(() -> Text.literal("Updated stamina for " + players.size() + " players").formatted(Formatting.AQUA), true);
        return 1;
    }

    private static int setLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        int level = IntegerArgumentType.getInteger(context, "level");
        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;
            int oldLevel = data.getLevel();
            data.setLevel(level);

            if (level > oldLevel) {
                int points = (level - oldLevel);
                data.addStatPoints(points);
                player.sendMessage(Text.literal("Level set to " + level + ". +" + points + " Stat Points.").formatted(Formatting.GOLD), false);
            } else {
                player.sendMessage(Text.literal("Level set to " + level).formatted(Formatting.GOLD), false);
            }
        }
        context.getSource().sendFeedback(() -> Text.literal("Set Level to " + level + " for " + players.size() + " players").formatted(Formatting.GOLD), true);
        return 1;
    }

    private static int addXp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        float amount = FloatArgumentType.getFloat(context, "amount");
        for (ServerPlayerEntity player : players) {
            ((IQuirkData) player).addXp(amount);
            player.sendMessage(Text.literal("Received " + amount + " XP").formatted(Formatting.GREEN), true);
        }
        context.getSource().sendFeedback(() -> Text.literal("Added " + amount + " XP to " + players.size() + " players").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int giveStatPoints(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        int points = IntegerArgumentType.getInteger(context, "points");
        for (ServerPlayerEntity player : players) {
            IQuirkData data = (IQuirkData) player;
            data.addStatPoints(points);
            player.sendMessage(Text.literal("Received " + points + " Stat Points!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        }
        context.getSource().sendFeedback(() -> Text.literal("Gave " + points + " points to " + players.size() + " players").formatted(Formatting.LIGHT_PURPLE), true);
        return 1;
    }
}