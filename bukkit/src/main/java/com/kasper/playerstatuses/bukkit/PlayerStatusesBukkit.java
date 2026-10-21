package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.CommandReply;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Paper / Purpur / Folia / Spigot / Bukkit plugin: the server half of the mod, without a client.
 * Uses the Bukkit API only, so one jar runs on every one of them from 1.16.5 on.
 */
public final class PlayerStatusesBukkit extends JavaPlugin implements Listener {

    private static final int RELOAD_CHECK_TICKS = 40;

    private final StatusServer core = new StatusServer();
    private Scheduling scheduling;
    private int ticks;

    @Override
    public void onEnable() {
        ModLog.use((level, text) -> getLogger().log(level == ModLog.Level.INFO ? Level.INFO
                : level == ModLog.Level.WARN ? Level.WARNING : Level.SEVERE, text));
        core.start(getDataFolder().toPath().resolve(ServerSettings.FILE));
        scheduling = Scheduling.create(this);
        getServer().getPluginManager().registerEvents(this, this);
        if (core.settings().get(ServerSettings.AFK_COMMAND)) {
            registerAfk();
        }
        scheduling.everyTick(this::tick);
    }

    @Override
    public void onDisable() {
        if (scheduling != null) {
            scheduling.cancelAll();
        }
    }

    private void tick() {
        core.relay().tick(System.currentTimeMillis());
        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            core.reloadIfChanged();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        core.relay().remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        reply(sender, StatusCommands.run(String.join(" ", args), source(sender), core,
                getServer().getOnlinePlayers().size(), System.currentTimeMillis()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> out = new java.util.ArrayList<>();
        for (String s : Arrays.asList("status", "reload", "help")) {
            if (s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * {@code /afk} is not in plugin.yml, so a server that turns it off keeps the {@code /afk} of another
     * plugin; when it is on it is added here (as {@code /playerstatuses:afk} too).
     */
    private void registerAfk() {
        try {
            CommandMap map = (CommandMap) getServer().getClass().getMethod("getCommandMap").invoke(getServer());
            Command afk = new Command(StatusCommands.AFK, "Mark yourself AFK until you move", "/afk", Collections.emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    reply(sender, StatusCommands.afk(source(sender), core, System.currentTimeMillis()));
                    return true;
                }
            };
            afk.setPermission("playerstatuses.afk");
            map.register(getName().toLowerCase(java.util.Locale.ROOT), afk);
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("Could not add /afk: " + e);
        }
    }

    private StatusCommands.Source source(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return new StatusCommands.Source() {
            @Override
            public UUID player() {
                return player == null ? null : player.getUniqueId();
            }

            @Override
            public String language() {
                return player == null ? "" : player.getLocale();
            }

            @Override
            public boolean isAdmin() {
                return player == null || sender.hasPermission("playerstatuses.admin");
            }
        };
    }

    private static void reply(CommandSender sender, CommandReply reply) {
        for (CommandReply.Line line : reply.lines()) {
            StringBuilder b = new StringBuilder();
            for (CommandReply.Span span : line.spans()) {
                b.append(color(span.style())).append(span.text());
            }
            sender.sendMessage(b.toString());
        }
    }

    private static ChatColor color(CommandReply.Style style) {
        switch (style) {
            case TITLE:
                return ChatColor.GOLD;
            case LABEL:
                return ChatColor.GRAY;
            case OK:
                return ChatColor.GREEN;
            case WARN:
                return ChatColor.YELLOW;
            case ERROR:
                return ChatColor.RED;
            case MUTED:
                return ChatColor.DARK_GRAY;
            default:
                return ChatColor.WHITE;
        }
    }
}
