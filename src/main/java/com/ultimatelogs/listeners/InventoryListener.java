package com.ultimatelogs.listeners;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import com.ultimatelogs.gui.*;
import com.ultimatelogs.utils.ItemBuilder;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class InventoryListener implements Listener {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final DatabaseManager db;

    public InventoryListener(UltimateLogs plugin, LogsConfig config, DatabaseManager db) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
    }

    private boolean isUltimateLogsGui(Inventory inv) {
        if (inv == null) return false;
        return inv.getHolder() instanceof UltimateLogsGui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupSecurity(PlayerAttemptPickupItemEvent event) {
        if (event.getItem() == null) return;
        ItemStack item = event.getItem().getItemStack();
        if (ItemBuilder.isGuiButton(item)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (!config.isLogItemEvents()) return;
        Player player = event.getPlayer();
        if (event.getItem() == null) return;
        ItemStack item = event.getItem().getItemStack();
        if (ItemBuilder.isGuiButton(item)) return;
        db.logItemEvent("pickup", player, player, item, "Picked up item from ground");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropSecurity(PlayerDropItemEvent event) {
        if (event.getItemDrop() == null) return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (ItemBuilder.isGuiButton(item)) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            ItemBuilder.cleanPlayerInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!config.isLogItemEvents()) return;
        Player player = event.getPlayer();
        if (event.getItemDrop() == null) return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (ItemBuilder.isGuiButton(item)) return;
        db.logItemEvent("drop", player, player, item, "Dropped item to ground");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGlobalInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Clean leftover button if clicked inside player's own inventory
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv != null && clickedInv.getType() == InventoryType.PLAYER) {
            ItemStack clickedItem = event.getCurrentItem();
            if (ItemBuilder.isGuiButton(clickedItem)) {
                event.setCancelled(true);
                event.setCurrentItem(null);
                player.updateInventory();
                return;
            }
        }

        // Clean leftover button on cursor if outside an UltimateLogs GUI
        if (ItemBuilder.isGuiButton(event.getCursor()) && !isUltimateLogsGui(event.getInventory())) {
            event.setCancelled(true);
            event.getView().setCursor(null);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && ItemBuilder.isGuiButton(item)) {
            event.setCancelled(true);
            ItemBuilder.cleanPlayerInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ItemBuilder.cleanPlayerInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) return;

        ItemBuilder.cleanPlayerInventory(player);

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.ENDER_CHEST) {
            db.recordInventorySnapshot(player, "echest_close", UUID.randomUUID().toString());
            db.logEnderChestEvent("close", player, null, "Closed Ender Chest");
        } else if (type != InventoryType.CRAFTING && type != InventoryType.PLAYER) {
            if (config.isLogContainerTransactions()) {
                db.recordInventorySnapshot(player, "container_close", UUID.randomUUID().toString());
            }
        }
    }
}
