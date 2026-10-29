package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.database.models.DeathRecord;
import com.ultimatelogs.utils.ItemBuilder;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DeathHistoryGui implements UltimateLogsGui {

    private static final int[] USABLE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final UltimateLogs plugin;
    private final String targetPlayerName;
    private final int page;
    private final Runnable backAction;
    private final Inventory inventory;
    private final List<DeathRecord> records = new ArrayList<>();

    public DeathHistoryGui(UltimateLogs plugin, String targetPlayerName, int page, Runnable backAction) {
        this.plugin = plugin;
        this.targetPlayerName = targetPlayerName;
        this.page = Math.max(0, page);
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#f43f5e:#be123c><bold>💀 DEATH HISTORY</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>"));

        buildGui();
        loadRecordsAsync();
    }

    private void buildGui() {
        // Border: Glass panes
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE));
            } else {
                inventory.setItem(i, ItemBuilder.pane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        // Header at Slot 4
        ItemStack header = new ItemBuilder(Material.SKELETON_SKULL)
                .name("<gradient:#f43f5e:#be123c><bold>DEATH INVESTIGATION ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Target Player: <white>" + targetPlayerName + "</white></gray>",
                        "<gray>Page: <yellow>" + (page + 1) + "</yellow></gray>",
                        "<gray>Browse every past recorded death. Inspect items,</gray>",
                        "<gray>ender chest, and coordinates even if player died</gray>",
                        "<gray>many times afterwards!</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(4, header);

        // Slot 45: BACK BUTTON
        ItemStack backBtn = new ItemBuilder(Material.ARROW)
                .name("<gold><bold>⬅ RETURN / BACK</bold></gold>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Click to return to the previous</gray>",
                        "<gray>category or player selection.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO GO BACK</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(45, backBtn);

        // Slot 53: Close
        ItemStack closeBtn = new ItemBuilder(Material.BARRIER)
                .name("<red><bold>CLOSE MENU</bold></red>")
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(53, closeBtn);
    }

    private void loadRecordsAsync() {
        plugin.getDatabaseManager().queryDeathHistory(targetPlayerName, 28, page * 28).thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                records.clear();
                records.addAll(list);
                populateEntries();
            });
        });
    }

    private void populateEntries() {
        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();

        for (int i = 0; i < records.size() && i < USABLE_SLOTS.length; i++) {
            DeathRecord rec = records.get(i);
            int slot = USABLE_SLOTS[i];

            Material icon = rec.killerType().equalsIgnoreCase("PLAYER") ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;

            ItemStack item = new ItemBuilder(icon)
                    .name("<red><bold>Death #" + ((page * 28) + i + 1) + "</bold></red> <gray>•</gray> <yellow>" + formatter.formatTime(rec.timestamp()) + "</yellow>")
                    .lore(List.of(
                            "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                            "<gray>Date: <white>" + formatter.formatDate(rec.timestamp()) + "</white></gray>",
                            "<gray>Time: <yellow>" + formatter.formatTime(rec.timestamp()) + "</yellow></gray>",
                            "<gray>Cause: <red>" + rec.deathCause() + "</red></gray>",
                            "<gray>Message: <white>" + rec.deathMessage() + "</white></gray>",
                            "<gray>Killer: <gold>" + rec.killerName() + "</gold> <gray>(" + rec.killerType() + ")</gray></gray>",
                            "<gray>World: <white>" + rec.worldName() + "</white> <yellow>[" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</yellow></gray>",
                            "<gray>Lost Items: <aqua>" + rec.itemCount() + " items</aqua> <dark_gray>(" + rec.dropCount() + " dropped)</dark_gray></gray>",
                            "<gray>Lost Experience: <gold>" + rec.expLevel() + " Levels (" + rec.totalExp() + " XP)</gold></gray>",
                            "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                            "<yellow>• Left-Click:</yellow> <green><bold>Inspect Death Inventory</bold></green>",
                            "<yellow>• Right-Click:</yellow> <light_purple><bold>Inspect Death Ender Chest</bold></light_purple>",
                            "<yellow>• Shift+Left:</yellow> <aqua><bold>Teleport to Death Location</bold></aqua>",
                            "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                    ))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(slot, item);
        }

        if (records.isEmpty()) {
            ItemStack emptyItem = new ItemBuilder(Material.GLASS_BOTTLE)
                    .name("<gray><i>No deaths recorded for " + targetPlayerName + "</i></gray>")
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(22, emptyItem);
        }

        // Pagination buttons
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>◀ PREVIOUS PAGE</bold></aqua>")
                    .lore(List.of("<gray>Go to Page " + page + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(48, prev);
        }
        if (records.size() == 28) {
            ItemStack next = new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>NEXT PAGE ▶</bold></aqua>")
                    .lore(List.of("<gray>Go to Page " + (page + 2) + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(50, next);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public boolean isInspectionGui() {
        return false;
    }

    @Override
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) return;

        ClickType click = event.getClick();
        staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);

        // Slot 45: BACK BUTTON
        if (rawSlot == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (backAction != null) {
                    backAction.run();
                } else {
                    plugin.getGuiManager().openStage3(staff, GuiHolder.Subsystem.DEATHS, targetPlayerName);
                }
            });
            return;
        }

        if (rawSlot == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
            return;
        }

        if (rawSlot == 48 && page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> new DeathHistoryGui(plugin, targetPlayerName, page - 1, backAction).open(staff));
            return;
        }
        if (rawSlot == 50 && records.size() == 28) {
            Bukkit.getScheduler().runTask(plugin, () -> new DeathHistoryGui(plugin, targetPlayerName, page + 1, backAction).open(staff));
            return;
        }

        // Check if clicked a death record item
        for (int i = 0; i < USABLE_SLOTS.length; i++) {
            if (rawSlot == USABLE_SLOTS[i] && i < records.size()) {
                DeathRecord rec = records.get(i);

                if (click.isShiftClick() && click.isLeftClick()) {
                    // Teleport staff to death location
                    World world = com.ultimatelogs.commands.PositionCommand.findWorld(rec.worldName());
                    if (world != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            staff.closeInventory();
                            Location dest = new Location(world, rec.x(), rec.y(), rec.z(), rec.yaw(), rec.pitch());
                            staff.teleportAsync(dest).thenAccept(success -> {
                                if (success) {
                                    MessageUtil.send(staff, "<green>✔ Teleported to death location of <white>" + targetPlayerName + "</white> at <yellow>" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "</yellow> (<aqua>" + world.getName() + "</aqua>).</green>");
                                    staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                                }
                            });
                        });
                    } else {
                        MessageUtil.send(staff, "<red>World " + rec.worldName() + " is not currently loaded.</red>");
                    }
                    return;
                }

                if (click.isRightClick()) {
                    // Inspect Ender Chest at death
                    ItemStack[] echest = rec.enderChestItems();
                    if (echest == null) {
                        echest = plugin.getInventorySerializer().deserializeItemArray("");
                    }
                    ItemStack[] finalEchest = echest;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        EnderChestInspectionGui gui = new EnderChestInspectionGui(
                                plugin, targetPlayerName, rec.deathId(), rec.timestamp(), rec.timestamp(), 1,
                                finalEchest, false, null, () -> new DeathHistoryGui(plugin, targetPlayerName, page, backAction).open(staff)
                        );
                        staff.openInventory(gui.getInventory());
                        MessageUtil.send(staff, "<green>Opened Ender Chest at moment of death for <white>" + targetPlayerName + "</white>.</green>");
                    });
                    return;
                }

                // Left-Click: Inspect Death Inventory
                final int deathNumber = (page * 28) + i + 1;
                plugin.getDatabaseManager().queryInventorySnapshotById(rec.inventorySnapshotId()).thenAccept(snap -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (snap != null) {
                            InventoryInspectionGui gui = new InventoryInspectionGui(
                                    plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                    snap.inventory(), snap.armor(), snap.offhand(), snap.expLevel(), snap.totalExp(), snap.health(), snap.hunger(), snap.gamemode(),
                                    false, null, () -> new DeathHistoryGui(plugin, targetPlayerName, page, backAction).open(staff)
                            );
                            staff.openInventory(gui.getInventory());
                            MessageUtil.send(staff, "<green>Opened Inventory Snapshot from Death #" + deathNumber + " of <white>" + targetPlayerName + "</white>.</green>");
                        } else {
                            MessageUtil.send(staff, "<red>Snapshot data for this death could not be found.</red>");
                        }
                    });
                });
                return;
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }
}
