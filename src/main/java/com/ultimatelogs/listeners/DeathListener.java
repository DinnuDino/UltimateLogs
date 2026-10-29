package com.ultimatelogs.listeners;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class DeathListener implements Listener {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final DatabaseManager db;

    public DeathListener(UltimateLogs plugin, LogsConfig config, DatabaseManager db) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!config.isLogDeaths()) return;

        Player player = event.getEntity();
        String message = event.deathMessage() != null
                ? PlainTextComponentSerializer.plainText().serialize(event.deathMessage())
                : player.getName() + " died";

        String cause = "UNKNOWN";
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage != null) {
            cause = lastDamage.getCause().name();
        }

        String killerName = "None";
        String killerType = "ENVIRONMENT";

        Player killer = player.getKiller();
        if (killer != null) {
            killerName = killer.getName();
            killerType = "PLAYER";
        } else if (lastDamage instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            killerName = damager.getName();
            killerType = damager.getType().name();
        }

        int dropCount = 0;
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) {
                dropCount += drop.getAmount();
            }
        }

        db.recordDeath(player, message, cause, killerName, killerType, event.getDroppedExp(), dropCount);

        if (config.isLogItemEvents()) {
            for (ItemStack drop : event.getDrops()) {
                if (drop != null && !drop.getType().isAir()) {
                    db.logItemEvent("death_drop", player, player, drop, "Dropped on death: " + message);
                }
            }
        }
    }
}
