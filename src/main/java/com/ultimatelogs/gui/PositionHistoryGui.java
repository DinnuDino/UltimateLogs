package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.database.models.PositionSnapshotRecord;
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
import java.util.Locale;

public class PositionHistoryGui implements UltimateLogsGui {

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
    private final List<PositionSnapshotRecord> records = new ArrayList<>();

    public PositionHistoryGui(UltimateLogs plugin, String targetPlayerName, int page, Runnable backAction) {
        this.plugin = plugin;
        this.targetPlayerName = targetPlayerName;
        this.page = Math.max(0, page);
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#38bdf8:#0284c7><bold>📍 POSITION HISTORY</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>"));

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
        ItemStack header = new ItemBuilder(Material.RECOVERY_COMPASS)
                .name("<gradient:#38bdf8:#0284c7><bold>COORDINATE BREADCRUMBS ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Target Player: <white>" + targetPlayerName + "</white></gray>",
                        "<gray>Page: <yellow>" + (page + 1) + "</yellow></gray>",
                        "<gray>Tracks historical positions, teleports, and</gray>",
                        "<gray>durations spent in each location.</gray>",
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
        plugin.getDatabaseManager().queryPositionSnapshotHistory(targetPlayerName, 28, page * 28).thenAccept(list -> {
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
            PositionSnapshotRecord rec = records.get(i);
            int slot = USABLE_SLOTS[i];

            Material icon = switch (rec.snapshotReason().toLowerCase(Locale.ROOT)) {
                case "death" -> Material.SKELETON_SKULL;
                case "join", "quit" -> Material.ENDER_PEARL;
                case "teleport", "respawn" -> Material.COMPASS;
                default -> Material.MAP;
            };

            List<String> lore = new ArrayList<>(List.of(
                    "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                    "<gray>Reason: <light_purple>" + rec.snapshotReason() + "</light_purple></gray>",
                    "<gray>Coordinates: <white>" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "</white></gray>",
                    "<gray>World: <aqua>" + rec.worldName() + "</aqua></gray>"
            ));
            lore.addAll(formatter.buildIntervalLore(rec.startTimestamp(), rec.endTimestamp(), rec.durationSeconds()));
            lore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");
            lore.add("<yellow>• Left-Click:</yellow> <green><bold>Teleport to Location</bold></green>");
            lore.add("<yellow>• Right-Click:</yellow> <light_purple><bold>Inspect Inventory Snapshot</bold></light_purple>");
            lore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");

            ItemStack item = new ItemBuilder(icon)
                    .name("<light_purple>" + rec.snapshotReason().toUpperCase(Locale.ROOT) + "</light_purple> <yellow>[" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</yellow>")
                    .lore(lore)
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(slot, item);
        }

        if (records.isEmpty()) {
            ItemStack emptyItem = new ItemBuilder(Material.GLASS_BOTTLE)
                    .name("<gray><i>No position records found for " + targetPlayerName + "</i></gray>")
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
                    plugin.getGuiManager().openStage3(staff, GuiHolder.Subsystem.POSITION, targetPlayerName);
                }
            });
            return;
        }

        if (rawSlot == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
            return;
        }

        if (rawSlot == 48 && page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> new PositionHistoryGui(plugin, targetPlayerName, page - 1, backAction).open(staff));
            return;
        }
        if (rawSlot == 50 && records.size() == 28) {
            Bukkit.getScheduler().runTask(plugin, () -> new PositionHistoryGui(plugin, targetPlayerName, page + 1, backAction).open(staff));
            return;
        }

        // Check clicked entries
        for (int i = 0; i < USABLE_SLOTS.length; i++) {
            if (rawSlot == USABLE_SLOTS[i] && i < records.size()) {
                PositionSnapshotRecord rec = records.get(i);

                if (click.isRightClick()) {
                    // View inventory around this position record
                    plugin.getDatabaseManager().queryInventorySnapshotBefore(targetPlayerName, rec.endTimestamp()).thenAccept(snap -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (snap != null) {
                                InventoryInspectionGui gui = new InventoryInspectionGui(
                                        plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                        snap.inventory(), snap.armor(), snap.offhand(), snap.expLevel(), snap.totalExp(), snap.health(), snap.hunger(), snap.gamemode(),
                                        false, null, () -> new PositionHistoryGui(plugin, targetPlayerName, page, backAction).open(staff)
                                );
                                staff.openInventory(gui.getInventory());
                            } else {
                                MessageUtil.send(staff, "<red>No inventory snapshot recorded around this timestamp.</red>");
                            }
                        });
                    });
                    return;
                }

                // Left Click: Teleport
                World world = com.ultimatelogs.commands.PositionCommand.findWorld(rec.worldName());
                if (world != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        staff.closeInventory();
                        Location dest = new Location(world, rec.x(), rec.y(), rec.z(), rec.yaw(), rec.pitch());
                        staff.teleportAsync(dest).thenAccept(success -> {
                            if (success) {
                                MessageUtil.send(staff, "<green>✔ Teleported to coordinate breadcrumb of <white>" + targetPlayerName + "</white> at <yellow>" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "</yellow> (<aqua>" + world.getName() + "</aqua>).</green>");
                                staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                            }
                        });
                    });
                } else {
                    MessageUtil.send(staff, "<red>World " + rec.worldName() + " is not currently loaded.</red>");
                }
                return;
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }
}
