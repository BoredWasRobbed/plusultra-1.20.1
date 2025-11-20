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

                // NEW: Leveling Commands
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
            data.setQuirk(quirkId);
            quirk.onEquip(player);
            context.getSource().sendFeedback(() -> Text.literal("Set quirk to " + quirkId).formatted(Formatting.GREEN), true);
        }
        return 1;
    }

    private static int clearQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(context, "target")) {
            ((IQuirkData) player).setQuirk(null);
            context.getSource().sendFeedback(() -> Text.literal("Cleared quirk").formatted(Formatting.YELLOW), true);
        }
        return 1;
    }

    private static int listQuirks(CommandContext<ServerCommandSource> context) {
        for (Identifier id : QuirkRegistry.QUIRK.getIds()) context.getSource().sendFeedback(() -> Text.literal("- " + id).formatted(Formatting.WHITE), false);
        return 1;
    }

    private static int setStamina(CommandContext<ServerCommandSource> context, boolean isMax) throws CommandSyntaxException {
        float amount = FloatArgumentType.getFloat(context, "amount");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(context, "target")) {
            IQuirkData data = (IQuirkData) player;
            if (isMax) data.setMaxStamina(amount);
            else data.setStamina(amount);
            context.getSource().sendFeedback(() -> Text.literal("Set " + (isMax ? "MAX " : "") + "stamina to " + amount).formatted(Formatting.AQUA), true);
        }
        return 1;
    }

    // NEW
    private static int setLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int level = IntegerArgumentType.getInteger(context, "level");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(context, "target")) {
            ((IQuirkData) player).setLevel(level);
            context.getSource().sendFeedback(() -> Text.literal("Set Level to " + level).formatted(Formatting.GOLD), true);
        }
        return 1;
    }

    private static int addXp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        float amount = FloatArgumentType.getFloat(context, "amount");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(context, "target")) {
            ((IQuirkData) player).addXp(amount);
            context.getSource().sendFeedback(() -> Text.literal("Added " + amount + " XP").formatted(Formatting.GREEN), true);
        }
        return 1;
    }
}