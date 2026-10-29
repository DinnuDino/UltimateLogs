package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.database.models.EnderChestSnapshotRecord;
import com.ultimatelogs.database.models.InventorySnapshotRecord;
import com.ultimatelogs.utils.ItemBuilder;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SnapshotHistoryGui implements UltimateLogsGui {

    private static final int[] USABLE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final UltimateLogs plugin;
    private final GuiHolder.Subsystem subsystem;
    private final String targetPlayerName;
    private final int page;
    private final Runnable backAction;
    private final Inventory inventory;

    private final List<InventorySnapshotRecord> invSnapshots = new ArrayList<>();
    private final List<EnderChestSnapshotRecord> ecSnapshots = new ArrayList<>();

    public SnapshotHistoryGui(UltimateLogs plugin, GuiHolder.Subsystem subsystem, String targetPlayerName, int page, Runnable backAction) {
        this.plugin = plugin;
        this.subsystem = subsystem;
        this.targetPlayerName = targetPlayerName;
        this.page = Math.max(0, page);
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>" + subsystem.getEmoji() + " " + subsystem.getDisplayName().toUpperCase(Locale.ROOT) + " SNAPSHOTS</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>"));

        buildGui();
        loadSnapshotsAsync();
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
        ItemStack header = new ItemBuilder(subsystem == GuiHolder.Subsystem.ENDER_CHEST ? Material.ENDER_CHEST : Material.CHEST)
                .name("<gradient:#c084fc:#f472b6><bold>" + subsystem.getDisplayName().toUpperCase(Locale.ROOT) + " SNAPSHOT ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Target Player: <white>" + targetPlayerName + "</white></gray>",
                        "<gray>Category: <aqua>" + subsystem.getDisplayName() + "</aqua></gray>",
                        "<gray>Page: <yellow>" + (page + 1) + "</yellow></gray>",
                        "<gray>Click any snapshot below to inspect full contents</gray>",
                        "<gray>with complete equipment, real-time date, and interval.</gray>",
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
                        "<gray>selection or time menu.</gray>",
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

    private void loadSnapshotsAsync() {
        if (subsystem == GuiHolder.Subsystem.ENDER_CHEST) {
            plugin.getDatabaseManager().queryEnderChestSnapshotHistory(targetPlayerName, 28, page * 28).thenAccept(list -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ecSnapshots.clear();
                    ecSnapshots.addAll(list);
                    populateEnderChestEntries();
                });
            });
        } else {
            plugin.getDatabaseManager().queryInventorySnapshotHistory(targetPlayerName, 28, page * 28).thenAccept(list -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    invSnapshots.clear();
                    invSnapshots.addAll(list);
                    populateInventoryEntries();
                });
            });
        }
    }

    private void populateInventoryEntries() {
        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();

        for (int i = 0; i < invSnapshots.size() && i < USABLE_SLOTS.length; i++) {
            InventorySnapshotRecord snap = invSnapshots.get(i);
            int slot = USABLE_SLOTS[i];

            Material icon = switch (snap.snapshotReason().toLowerCase(Locale.ROOT)) {
                case "death" -> Material.SKELETON_SKULL;
                case "join", "quit" -> Material.ENDER_PEARL;
                case "echest_close" -> Material.ENDER_CHEST;
                case "container_close" -> Material.BARREL;
                default -> Material.CHEST;
            };

            List<String> lore = new ArrayList<>(List.of(
                    "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                    "<gray>Reason: <light_purple>" + snap.snapshotReason() + "</light_purple></gray>",
                    "<gray>Snapshot ID: <yellow>" + snap.snapshotId() + "</yellow></gray>",
                    "<gray>Item Count: <aqua>" + snap.getItemCount() + " items</aqua></gray>",
                    "<gray>Location: <white>" + snap.worldName() + "</white> <yellow>[" + (int) snap.x() + ", " + (int) snap.y() + ", " + (int) snap.z() + "]</yellow></gray>",
                    "<gray>Exp / Health: <gold>" + snap.expLevel() + " Levels</gold> | <red>" + String.format(Locale.US, "%.1f", snap.health()) + " ❤</red></gray>"
            ));
            lore.addAll(formatter.buildIntervalLore(snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds()));
            lore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");
            lore.add("<yellow><bold>CLICK TO INSPECT SNAPSHOT</bold></yellow>");

            ItemStack item = new ItemBuilder(icon)
                    .name("<gradient:#c084fc:#f472b6><bold>" + snap.snapshotReason().toUpperCase(Locale.ROOT) + "</bold></gradient> <gray>•</gray> <yellow>" + formatter.formatTime(snap.endTimestamp()) + "</yellow>")
                    .lore(lore)
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(slot, item);
        }

        if (invSnapshots.isEmpty()) {
            ItemStack emptyItem = new ItemBuilder(Material.GLASS_BOTTLE)
                    .name("<gray><i>No inventory snapshots recorded for " + targetPlayerName + "</i></gray>")
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(22, emptyItem);
        }

        setupPagination(invSnapshots.size());
    }

    private void populateEnderChestEntries() {
        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();

        for (int i = 0; i < ecSnapshots.size() && i < USABLE_SLOTS.length; i++) {
            EnderChestSnapshotRecord snap = ecSnapshots.get(i);
            int slot = USABLE_SLOTS[i];

            List<String> lore = new ArrayList<>(List.of(
                    "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                    "<gray>Reason: <light_purple>" + snap.snapshotReason() + "</light_purple></gray>",
                    "<gray>Snapshot ID: <yellow>" + snap.snapshotId() + "</yellow></gray>",
                    "<gray>Items inside: <aqua>" + snap.getItemCount() + " items</aqua></gray>",
                    "<gray>Location: <white>" + snap.worldName() + "</white> <yellow>[" + (int) snap.x() + ", " + (int) snap.y() + ", " + (int) snap.z() + "]</yellow></gray>"
            ));
            lore.addAll(formatter.buildIntervalLore(snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds()));
            lore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");
            lore.add("<yellow><bold>CLICK TO INSPECT ENDER CHEST</bold></yellow>");

            ItemStack item = new ItemBuilder(Material.ENDER_CHEST)
                    .name("<gradient:#e879f9:#c084fc><bold>ECHEST SNAPSHOT</bold></gradient> <gray>•</gray> <yellow>" + formatter.formatTime(snap.endTimestamp()) + "</yellow>")
                    .lore(lore)
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(slot, item);
        }

        if (ecSnapshots.isEmpty()) {
            ItemStack emptyItem = new ItemBuilder(Material.GLASS_BOTTLE)
                    .name("<gray><i>No Ender Chest snapshots recorded for " + targetPlayerName + "</i></gray>")
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(22, emptyItem);
        }

        setupPagination(ecSnapshots.size());
    }

    private void setupPagination(int currentCount) {
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>◀ PREVIOUS PAGE</bold></aqua>")
                    .lore(List.of("<gray>Go to Page " + page + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(48, prev);
        }
        if (currentCount == 28) {
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

        staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);

        // Slot 45: BACK BUTTON
        if (rawSlot == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (backAction != null) {
                    backAction.run();
                } else {
                    plugin.getGuiManager().openStage3(staff, subsystem, targetPlayerName);
                }
            });
            return;
        }

        if (rawSlot == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
            return;
        }

        if (rawSlot == 48 && page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> new SnapshotHistoryGui(plugin, subsystem, targetPlayerName, page - 1, backAction).open(staff));
            return;
        }
        int total = subsystem == GuiHolder.Subsystem.ENDER_CHEST ? ecSnapshots.size() : invSnapshots.size();
        if (rawSlot == 50 && total == 28) {
            Bukkit.getScheduler().runTask(plugin, () -> new SnapshotHistoryGui(plugin, subsystem, targetPlayerName, page + 1, backAction).open(staff));
            return;
        }

        // Click snapshot items
        for (int i = 0; i < USABLE_SLOTS.length; i++) {
            if (rawSlot == USABLE_SLOTS[i]) {
                if (subsystem == GuiHolder.Subsystem.ENDER_CHEST && i < ecSnapshots.size()) {
                    EnderChestSnapshotRecord snap = ecSnapshots.get(i);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        EnderChestInspectionGui gui = new EnderChestInspectionGui(
                                plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                snap.contents(), false, null, () -> new SnapshotHistoryGui(plugin, subsystem, targetPlayerName, page, backAction).open(staff)
                        );
                        staff.openInventory(gui.getInventory());
                    });
                    return;
                } else if (subsystem == GuiHolder.Subsystem.INVENTORY && i < invSnapshots.size()) {
                    InventorySnapshotRecord snap = invSnapshots.get(i);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        InventoryInspectionGui gui = new InventoryInspectionGui(
                                plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                snap.inventory(), snap.armor(), snap.offhand(), snap.expLevel(), snap.totalExp(), snap.health(), snap.hunger(), snap.gamemode(),
                                false, null, () -> new SnapshotHistoryGui(plugin, subsystem, targetPlayerName, page, backAction).open(staff)
                        );
                        staff.openInventory(gui.getInventory());
                    });
                    return;
                }
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }
}
