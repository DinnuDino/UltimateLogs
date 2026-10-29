package com.ultimatelogs.commands;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.gui.GuiHolder;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class UltimateLogsCommand implements CommandExecutor, TabCompleter {

    private final UltimateLogs plugin;
    private final TimeParser timeParser;

    public UltimateLogsCommand(UltimateLogs plugin) {
        this.plugin = plugin;
        this.timeParser = new TimeParser(plugin.getLogsConfig().getZoneId());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ultimatelogs.view") && !sender.isOp()) {
            MessageUtil.send(sender, plugin.getLogsConfig().getNoPermissionMessage());
            return true;
        }

        Player playerSender = sender instanceof Player p ? p : null;

        // /logs with no arguments opens the main GUI hub
        if (args.length == 0) {
            if (playerSender != null) {
                plugin.getGuiManager().openStage1(playerSender);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("flush")) {
            if (!sender.hasPermission("ultimatelogs.admin") && !sender.isOp()) {
                MessageUtil.send(sender, plugin.getLogsConfig().getNoPermissionMessage());
                return true;
            }
            plugin.getDatabaseManager().forceFlush();
            MessageUtil.send(sender, plugin.getLogsConfig().getPrefix() + "<green>Flushed all queued audit records to database.</green>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ultimatelogs.admin") && !sender.isOp()) {
                MessageUtil.send(sender, plugin.getLogsConfig().getNoPermissionMessage());
                return true;
            }
            plugin.reloadPluginConfig();
            MessageUtil.send(sender, plugin.getLogsConfig().getPrefix() + "<green>Configuration reloaded successfully.</green>");
            return true;
        }

        String targetName = args[0];

        // Subsystem shortcuts: /logs <player> <deaths|echest|pos|items|actions>
        if (args.length >= 2) {
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "deaths", "death" -> {
                    if (playerSender != null) {
                        plugin.getGuiManager().openDeathHistory(playerSender, targetName, 0, () -> plugin.getGuiManager().openStage1(playerSender));
                    }
                    return true;
                }
                case "echest", "ec", "enderchest" -> {
                    if (playerSender != null) {
                        if (args.length >= 3) {
                            Instant targetTime = timeParser.parse(args[2]);
                            plugin.getGuiManager().executeCategoryAction(playerSender, GuiHolder.Subsystem.ENDER_CHEST, targetName, targetTime != null ? targetTime : Instant.now(), args[2], () -> plugin.getGuiManager().openStage1(playerSender));
                        } else {
                            plugin.getGuiManager().openEnderChestInspection(playerSender, targetName, true, () -> plugin.getGuiManager().openStage1(playerSender));
                        }
                    }
                    return true;
                }
                case "pos", "position" -> {
                    if (playerSender != null) {
                        plugin.getGuiManager().openPositionHistory(playerSender, targetName, 0, () -> plugin.getGuiManager().openStage1(playerSender));
                    }
                    return true;
                }
                case "items" -> {
                    if (playerSender != null) {
                        plugin.getGuiManager().executeCategoryAction(playerSender, GuiHolder.Subsystem.ITEM_TRANSACTIONS, targetName, Instant.now(), "Latest", () -> plugin.getGuiManager().openStage1(playerSender));
                    }
                    return true;
                }
                case "actions" -> {
                    if (playerSender != null) {
                        plugin.getGuiManager().executeCategoryAction(playerSender, GuiHolder.Subsystem.BLOCK_ACTIONS, targetName, Instant.now(), "Latest", () -> plugin.getGuiManager().openStage1(playerSender));
                    }
                    return true;
                }
            }

            // /logs <player> <time> (e.g. /logs Steve 10m)
            String timeInput = args.length >= 3 ? args[1] + " " + args[2] : args[1];
            Instant targetTime = timeParser.parse(timeInput);
            if (targetTime != null) {
                if (playerSender != null) {
                    plugin.getGuiManager().executeCategoryAction(playerSender, GuiHolder.Subsystem.INVENTORY, targetName, targetTime, timeInput, () -> plugin.getGuiManager().openStage1(playerSender));
                }
                return true;
            }
        }

        // Default: /logs <player>
        if (playerSender != null) {
            plugin.getGuiManager().openInventoryInspection(playerSender, targetName, true, () -> plugin.getGuiManager().openStage1(playerSender));
        } else {
            MessageUtil.send(sender, "<red>Console must specify a query time or use /deaths <player>.</red>");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gradient:#c084fc:#f472b6><bold>=== Ultimate Logs (Paper 26.2) ===</bold></gradient>");
        MessageUtil.send(sender, "<yellow>/logs</yellow> <gray>- Open interactive Stage 1 audit hub</gray>");
        MessageUtil.send(sender, "<yellow>/inv <player> [time|history|live]</yellow> <gray>- Inspect player inventory (live/snapshot)</gray>");
        MessageUtil.send(sender, "<yellow>/ec <player> [time|history|live]</yellow> <gray>- Inspect player Ender Chest</gray>");
        MessageUtil.send(sender, "<yellow>/pos <player> [time|history]</yellow> <gray>- View coordinate trail & breadcrumbs</gray>");
        MessageUtil.send(sender, "<yellow>/deaths <player> [page]</yellow> <gray>- Complete death history & item recovery</gray>");
        MessageUtil.send(sender, "<yellow>/logs flush</yellow> <gray>- Force flush queued audit records to DB</gray>");
        MessageUtil.send(sender, "<yellow>/logs reload</yellow> <gray>- Reload configuration</gray>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String q = args[0].toLowerCase();
            Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String sub : List.of("help", "flush", "reload")) {
                if (sub.startsWith(q)) set.add(sub);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(q)) set.add(p.getName());
            }
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().toLowerCase().startsWith(q)) {
                    set.add(op.getName());
                }
            }
            return new ArrayList<>(set);
        }
        if (args.length == 2) {
            return List.of("deaths", "echest", "pos", "items", "actions", "live", "history", "5m", "10m", "1h", "1d");
        }
        return Collections.emptyList();
    }
}
