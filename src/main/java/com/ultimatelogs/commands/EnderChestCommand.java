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

public class EnderChestCommand implements CommandExecutor, TabCompleter {

    private final UltimateLogs plugin;
    private final TimeParser timeParser;

    public EnderChestCommand(UltimateLogs plugin) {
        this.plugin = plugin;
        this.timeParser = new TimeParser(plugin.getLogsConfig().getZoneId());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        com.ultimatelogs.utils.ItemBuilder.cleanPlayerInventory(player);

        // If no arguments, open player's own Ender Chest
        if (args.length == 0) {
            player.openInventory(player.getEnderChest());
            return true;
        }

        if (!player.hasPermission("ultimatelogs.enderchest") && !player.isOp()) {
            MessageUtil.send(player, plugin.getLogsConfig().getNoPermissionMessage());
            return true;
        }

        String targetName = args[0];

        // /ec <player> history
        if (args.length >= 2 && args[1].equalsIgnoreCase("history")) {
            plugin.getGuiManager().openSnapshotHistory(player, GuiHolder.Subsystem.ENDER_CHEST, targetName, 0, () -> plugin.getGuiManager().openStage1(player));
            return true;
        }

        // /ec <player> live
        if (args.length >= 2 && args[1].equalsIgnoreCase("live")) {
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                plugin.getGuiManager().openEnderChestInspection(player, onlineTarget.getName(), true, () -> plugin.getGuiManager().openStage1(player));
            } else {
                MessageUtil.send(player, "<red>Player <white>" + targetName + "</white> is not online. Loading latest snapshot instead...</red>");
                plugin.getGuiManager().openEnderChestInspection(player, targetName, false, () -> plugin.getGuiManager().openStage1(player));
            }
            return true;
        }

        // /ec <player> <time>
        if (args.length >= 2) {
            String timeInput = args.length >= 3 ? args[1] + " " + args[2] : args[1];
            Instant targetTime = timeParser.parse(timeInput);
            if (targetTime != null) {
                plugin.getGuiManager().executeCategoryAction(player, GuiHolder.Subsystem.ENDER_CHEST, targetName, targetTime, timeInput, () -> plugin.getGuiManager().openStage1(player));
                return true;
            } else {
                MessageUtil.send(player, "<red>Invalid time format: <yellow>" + timeInput + "</yellow>. Use: <yellow>10m</yellow>, <yellow>1h</yellow>, or <yellow>DD/MM/YYYY</yellow>.</red>");
                return true;
            }
        }

        // Default: /ec <player> -> opens live if online, else automatically loads latest snapshot
        plugin.getGuiManager().openEnderChestInspection(player, targetName, true, () -> plugin.getGuiManager().openStage1(player));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String q = args[0].toLowerCase();
            Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
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
            return List.of("live", "history", "5m", "10m", "1h", "1d");
        }
        return Collections.emptyList();
    }
}
