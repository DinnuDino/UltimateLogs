package com.ultimatelogs.commands;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import com.ultimatelogs.utils.TimeParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

public class PositionCommand implements CommandExecutor, TabCompleter {

    private final UltimateLogs plugin;
    private final TimeParser timeParser;

    public PositionCommand(UltimateLogs plugin) {
        this.plugin = plugin;
        this.timeParser = new TimeParser(plugin.getLogsConfig().getZoneId());
    }

    public static World findWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        World w = Bukkit.getWorld(worldName);
        if (w != null) return w;
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) return world;
            if (world.getKey().toString().equalsIgnoreCase(worldName)) return world;
            if (world.getKey().value().equalsIgnoreCase(worldName)) return world;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ultimatelogs.position") && !sender.isOp()) {
            MessageUtil.send(sender, plugin.getLogsConfig().getNoPermissionMessage());
            return true;
        }

        if (args.length == 0) {
            MessageUtil.send(sender, "<red>Usage: /pos <player> [time|history|live]</red>");
            return true;
        }

        Player playerSender = sender instanceof Player p ? p : null;

        // Internal click action: /pos tp <player>
        if (args[0].equalsIgnoreCase("tp")) {
            if (playerSender == null) {
                sender.sendMessage("This teleport action can only be run by a player.");
                return true;
            }
            if (args.length < 2) {
                MessageUtil.send(playerSender, "<red>Usage: /pos tp <player></red>");
                return true;
            }
            String target = args[1];
            Player onlineTarget = Bukkit.getPlayerExact(target);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                Location dest = onlineTarget.getLocation();
                playerSender.teleportAsync(dest).thenAccept(success -> {
                    if (success) {
                        MessageUtil.send(playerSender, "<green>✔ Teleported to <white>" + onlineTarget.getName() + "</white> at <yellow>" + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ() + "</yellow> (<aqua>" + dest.getWorld().getName() + "</aqua>).</green>");
                        playerSender.playSound(playerSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                    }
                });
                return true;
            } else {
                plugin.getDatabaseManager().queryPositionSnapshotHistory(target, 1, 0).thenAccept(list -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!list.isEmpty()) {
                            var rec = list.get(0);
                            World w = findWorld(rec.worldName());
                            if (w != null) {
                                Location dest = new Location(w, rec.x(), rec.y(), rec.z(), rec.yaw(), rec.pitch());
                                playerSender.teleportAsync(dest).thenAccept(success -> {
                                    if (success) {
                                        MessageUtil.send(playerSender, "<green>✔ Teleported to last recorded location of <white>" + target + "</white> at <yellow>" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "</yellow> (<aqua>" + w.getName() + "</aqua>).</green>");
                                        playerSender.playSound(playerSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                                    }
                                });
                            } else {
                                MessageUtil.send(playerSender, "<red>World " + rec.worldName() + " is not currently loaded.</red>");
                            }
                        } else {
                            MessageUtil.send(playerSender, "<red>No recorded location found for player <white>" + target + "</white>.</red>");
                        }
                    });
                });
                return true;
            }
        }

        // Internal click action: /pos goto <world> <x> <y> <z> [yaw] [pitch]
        if (args[0].equalsIgnoreCase("goto")) {
            if (playerSender == null) {
                sender.sendMessage("This teleport action can only be run by a player.");
                return true;
            }
            if (args.length < 5) {
                MessageUtil.send(playerSender, "<red>Usage: /pos goto <world> <x> <y> <z> [yaw] [pitch]</red>");
                return true;
            }
            String worldName = args[1];
            World w = findWorld(worldName);
            if (w == null) {
                MessageUtil.send(playerSender, "<red>World " + worldName + " is not currently loaded.</red>");
                return true;
            }
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                float yaw = args.length >= 6 ? Float.parseFloat(args[5]) : playerSender.getLocation().getYaw();
                float pitch = args.length >= 7 ? Float.parseFloat(args[6]) : playerSender.getLocation().getPitch();

                Location dest = new Location(w, x, y, z, yaw, pitch);
                playerSender.teleportAsync(dest).thenAccept(success -> {
                    if (success) {
                        MessageUtil.send(playerSender, "<green>✔ Teleported to <yellow>" + (int) x + ", " + (int) y + ", " + (int) z + "</yellow> (<aqua>" + w.getName() + "</aqua>).</green>");
                        playerSender.playSound(playerSender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                    }
                });
            } catch (NumberFormatException e) {
                MessageUtil.send(playerSender, "<red>Invalid coordinates.</red>");
            }
            return true;
        }

        String targetName = args[0];

        // /pos <player> history or /pos <player> gui
        if (args.length >= 2 && (args[1].equalsIgnoreCase("history") || args[1].equalsIgnoreCase("gui"))) {
            if (playerSender != null) {
                plugin.getGuiManager().openPositionHistory(playerSender, targetName, 0, () -> plugin.getGuiManager().openStage1(playerSender));
            } else {
                sendPositionTrailToConsole(sender, targetName, null);
            }
            return true;
        }

        // /pos <player> <time>
        if (args.length >= 2) {
            String timeInput = args.length >= 3 ? args[1] + " " + args[2] : args[1];
            Instant targetTime = timeParser.parse(timeInput);
            if (targetTime != null) {
                if (playerSender != null) {
                    plugin.getGuiManager().openPositionHistory(playerSender, targetName, 0, () -> plugin.getGuiManager().openStage1(playerSender));
                }
                sendPositionTrailToConsole(sender, targetName, targetTime);
                return true;
            }
        }

        // Default: /pos <player> -> shows location in chat with clickable teleport and history buttons
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            Location loc = onlineTarget.getLocation();
            MessageUtil.send(sender, "<gradient:#6366f1:#a855f7><bold>=== Live Location: " + targetName + " (Online) ===</bold></gradient>");
            MessageUtil.send(sender, "<gray>World: <white>" + loc.getWorld().getName() + "</white> | <aqua>X:</aqua> <yellow>" + loc.getBlockX() + "</yellow> <aqua>Y:</aqua> <yellow>" + loc.getBlockY() + "</yellow> <aqua>Z:</aqua> <yellow>" + loc.getBlockZ() + "</yellow></gray>");
            MessageUtil.send(sender, "<gray>Ping: <white>" + onlineTarget.getPing() + "ms</white> | Health: <red>" + String.format(Locale.US, "%.1f", onlineTarget.getHealth()) + " ❤</red> | Gamemode: <white>" + onlineTarget.getGameMode().name() + "</white></gray>");

            Component tpBtn = Component.text("[⚡ Click to Teleport]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/pos tp " + onlineTarget.getName()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to instantly teleport to " + targetName, NamedTextColor.YELLOW)));

            Component historyBtn = Component.text("[📜 Browse Trail GUI]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/pos " + onlineTarget.getName() + " history"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to open breadcrumb trail GUI", NamedTextColor.YELLOW)));

            sender.sendMessage(tpBtn.append(Component.space()).append(Component.space()).append(historyBtn));
        } else {
            // Player offline: query latest recorded position
            plugin.getDatabaseManager().queryPositionSnapshotHistory(targetName, 1, 0).thenAccept(list -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!list.isEmpty()) {
                        var rec = list.get(0);
                        TimeIntervalFormatter fmt = plugin.getTimeIntervalFormatter();
                        String intervalStr = fmt.formatIntervalWithDuration(rec.startTimestamp(), rec.endTimestamp(), rec.durationSeconds());

                        MessageUtil.send(sender, "<gradient:#6366f1:#a855f7><bold>=== Last Recorded Position: " + targetName + " (Offline) ===</bold></gradient>");
                        MessageUtil.send(sender, "<gray>Time: <yellow>" + intervalStr + "</yellow></gray>");
                        MessageUtil.send(sender, "<gray>World: <white>" + rec.worldName() + "</white> | <aqua>X:</aqua> <yellow>" + (int) rec.x() + "</yellow> <aqua>Y:</aqua> <yellow>" + (int) rec.y() + "</yellow> <aqua>Z:</aqua> <yellow>" + (int) rec.z() + "</yellow></gray>");
                        MessageUtil.send(sender, "<gray>Trigger: <light_purple>" + rec.snapshotReason() + "</light_purple> | Health: <red>" + String.format(Locale.US, "%.1f", rec.health()) + " ❤</red></gray>");

                        Component tpBtn = Component.text("[⚡ Click to Teleport]", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/pos goto " + rec.worldName() + " " + rec.x() + " " + rec.y() + " " + rec.z() + " " + rec.yaw() + " " + rec.pitch()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to instantly teleport to last known location", NamedTextColor.YELLOW)));

                        Component historyBtn = Component.text("[📜 Browse Trail GUI]", NamedTextColor.AQUA, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/pos " + targetName + " history"))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to open breadcrumb trail GUI", NamedTextColor.YELLOW)));

                        sender.sendMessage(tpBtn.append(Component.space()).append(Component.space()).append(historyBtn));
                    } else {
                        MessageUtil.send(sender, "<red>No position records found for player <white>" + targetName + "</white>.</red>");
                    }
                });
            });
        }
        return true;
    }

    private void sendPositionTrailToConsole(CommandSender sender, String targetName, Instant time) {
        plugin.getDatabaseManager().queryPositionSnapshotHistory(targetName, 10, 0).thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageUtil.send(sender, "<gradient:#6366f1:#a855f7><bold>=== Coordinate Trail: " + targetName + " ===</bold></gradient>");
                TimeIntervalFormatter fmt = plugin.getTimeIntervalFormatter();
                for (var rec : list) {
                    MessageUtil.send(sender, "<dark_gray>•</dark_gray> <gray>" + fmt.formatDate(rec.endTimestamp()) + " " + fmt.formatTime(rec.endTimestamp()) + "</gray> <white>" + rec.worldName() + "</white> <yellow>[" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</yellow> <aqua>(" + rec.snapshotReason() + ")</aqua>");
                }
            });
        });
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
            if (args[0].equalsIgnoreCase("tp")) {
                String q = args[1].toLowerCase();
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
            return List.of("live", "history", "5m", "10m", "1h", "1d");
        }
        return Collections.emptyList();
    }
}
