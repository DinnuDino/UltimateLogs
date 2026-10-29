package com.ultimatelogs.listeners;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class WorldActionListener implements Listener {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final DatabaseManager db;

    public WorldActionListener(UltimateLogs plugin, LogsConfig config, DatabaseManager db) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.isLogBlockActions()) return;
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        db.logAction("block_place", player, loc, event.getBlock().getType().name(), "Placed block");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isLogBlockActions()) return;
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        db.logAction("block_break", player, loc, event.getBlock().getType().name(), "Broke block");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.isLogBlockActions()) return;
        if (event.getClickedBlock() == null) return;
        String blockName = event.getClickedBlock().getType().name();
        if (blockName.contains("CHEST") || blockName.contains("SHULKER") || blockName.contains("BARREL") || blockName.contains("DOOR") || blockName.contains("GATE") || blockName.contains("LEVER") || blockName.contains("BUTTON")) {
            Player player = event.getPlayer();
            db.logAction("interact", player, event.getClickedBlock().getLocation(), blockName, "Interacted with " + blockName);
        }
    }
}
