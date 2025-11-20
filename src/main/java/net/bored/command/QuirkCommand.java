package net.bored.command;

import com.mojang.brigadier.CommandDispatcher;
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

public class QuirkCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("quirk")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("target", EntityArgumentType.players())
                                .then(CommandManager.argument("quirk_id", IdentifierArgumentType.identifier())
                                        .suggests(QUIRK_SUGGESTIONS)
                                        .executes(QuirkCommand::setQuirk)
                                )
                        )
                )
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("target", EntityArgumentType.players())
                                .executes(QuirkCommand::clearQuirk)
                        )
                )
                .then(CommandManager.literal("list")
                        .executes(QuirkCommand::listQuirks)
                )
        );
    }

    private static final SuggestionProvider<ServerCommandSource> QUIRK_SUGGESTIONS = (context, builder) -> {
        return CommandSource.suggestIdentifiers(QuirkRegistry.QUIRK.getIds(), builder);
    };

    private static int setQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");
        Identifier quirkId = IdentifierArgumentType.getIdentifier(context, "quirk_id");
        Quirk quirk = QuirkRegistry.get(quirkId);

        if (quirk == null) {
            context.getSource().sendError(Text.literal("Quirk not found: " + quirkId));
            return 0;
        }

        for (ServerPlayerEntity player : players) {
            // CAST TO INTERFACE
            IQuirkData data = (IQuirkData) player;
            data.setQuirk(quirkId);
            quirk.onEquip(player);
            context.getSource().sendFeedback(() -> Text.literal("Set quirk for " + player.getName().getString() + " to " + quirkId).formatted(Formatting.GREEN), true);
        }
        return 1;
    }

    private static int clearQuirk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "target");

        for (ServerPlayerEntity player : players) {
            // CAST TO INTERFACE
            IQuirkData data = (IQuirkData) player;
            data.setQuirk(null);
            context.getSource().sendFeedback(() -> Text.literal("Cleared quirk for " + player.getName().getString()).formatted(Formatting.YELLOW), true);
        }
        return 1;
    }

    private static int listQuirks(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal("Registered Quirks:").formatted(Formatting.GOLD), false);
        for (Identifier id : QuirkRegistry.QUIRK.getIds()) {
            context.getSource().sendFeedback(() -> Text.literal("- " + id.toString()).formatted(Formatting.WHITE), false);
        }
        return 1;
    }
}