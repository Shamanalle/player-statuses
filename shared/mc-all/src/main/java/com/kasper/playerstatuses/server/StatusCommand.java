package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.CommandReply;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /playerstatuses} and {@code /afk} on a Fabric, Forge or NeoForge server. The text after the
 * command goes to {@link StatusCommands} as it is; the reply comes back as coloured chat lines.
 */
public final class StatusCommand {

    private StatusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(StatusCommands.NAME)
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList("status", "reload", "help"), builder))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args")))));
        // Not registered when it is off, so a /afk of another mod or plugin stays usable
        if (ServerSide.core().settings().get(ServerSettings.AFK_COMMAND)) {
            dispatcher.register(Commands.literal(StatusCommands.AFK).executes(StatusCommand::afk));
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args) {
        CommandSourceStack source = ctx.getSource();
        int online = source.getServer().getPlayerList().getPlayerCount();
        return reply(source, StatusCommands.run(args, of(source), ServerSide.core(), online, System.currentTimeMillis()));
    }

    private static int afk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        return reply(source, StatusCommands.afk(of(source), ServerSide.core(), System.currentTimeMillis()));
    }

    private static StatusCommands.Source of(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return new StatusCommands.Source() {
            @Override
            public UUID player() {
                return player == null ? null : player.getUUID();
            }

            @Override
            public String language() {
                return player == null ? "" : PlayerLanguage.of(player);
            }

            @Override
            public boolean isAdmin() {
                return AdminPermission.isAdmin(source);
            }
        };
    }

    private static int reply(CommandSourceStack source, CommandReply reply) {
        for (CommandReply.Line line : reply.lines()) {
            MutableComponent text = Component.literal("");
            for (CommandReply.Span span : line.spans()) {
                text.append(Component.literal(span.text()).withStyle(color(span.style())));
            }
            if (reply.success()) {
                source.sendSuccess(() -> text, false);
            } else {
                source.sendFailure(text);
            }
        }
        return reply.success() ? 1 : 0;
    }

    private static ChatFormatting color(CommandReply.Style style) {
        switch (style) {
            case TITLE:
                return ChatFormatting.GOLD;
            case LABEL:
                return ChatFormatting.GRAY;
            case OK:
                return ChatFormatting.GREEN;
            case WARN:
                return ChatFormatting.YELLOW;
            case ERROR:
                return ChatFormatting.RED;
            case MUTED:
                return ChatFormatting.DARK_GRAY;
            default:
                return ChatFormatting.WHITE;
        }
    }
}
