package com.ultimatelogs.commands;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.database.models.DeathRecord;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeathsCommand implements CommandExecutor, TabCompleter {

    private final UltimateLogs plugin;

    public DeathsCommand(UltimateLogs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ultimatelogs.deaths") && !sender.isOp()) {
            MessageUtil.send(sender, plugin.getLogsConfig().getNoPermissionMessage());
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getGuiManager().openDeathHistory(player, player.getName(), 0, () -> plugin.getGuiManager().openStage1(player));
            } else {
                MessageUtil.send(sender, "<red>Usage: /deaths <player> [page]</red>");
            }
            return true;
        }

        String targetName = args[0];
        int page = 0;
        if (args.length >= 2) {
            try {
                page = Math.max(0, Integer.parseInt(args[1]) - 1);
            } catch (NumberFormatException ignored) {}
        }

        if (sender instanceof Player staff) {
            plugin.getGuiManager().openDeathHistory(staff, targetName, page, () -> plugin.getGuiManager().openStage1(staff));
        } else {
            // Console query
            plugin.getDatabaseManager().queryDeathHistory(targetName, 10, page * 10).thenAccept(list -> {
                MessageUtil.send(sender, "<gradient:#f43f5e:#be123c><bold>=== Death Investigation Archive: " + targetName + " ===</bold></gradient>");
                TimeIntervalFormatter fmt = plugin.getTimeIntervalFormatter();
                if (list.isEmpty()) {
                    MessageUtil.send(sender, "<gray>No deaths recorded for " + targetName + ".</gray>");
                    return;
                }
                int count = 1;
                for (DeathRecord rec : list) {
                    MessageUtil.send(sender, "<red>#" + count++ + "</red> <gray>" + fmt.formatDate(rec.timestamp()) + " " + fmt.formatTime(rec.timestamp()) + "</gray> - <yellow>" + rec.deathMessage() + "</yellow> <dark_gray>@ " + rec.worldName() + " [" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</dark_gray>");
                }
            });
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String q = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(q)) list.add(p.getName());
            }
            return list;
        }
        return Collections.emptyList();
    }
}
