package com.ultimatelogs.listeners;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PositionTrackerListener implements Listener {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final DatabaseManager db;

    public PositionTrackerListener(UltimateLogs plugin, LogsConfig config, DatabaseManager db) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        String reason = "teleport_" + event.getCause().name().toLowerCase();
        db.recordPositionSnapshot(player, reason);
        db.recordInventorySnapshot(player, reason, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        db.recordPositionSnapshot(player, "respawn");
        db.recordInventorySnapshot(player, "respawn", null);
    }
}
