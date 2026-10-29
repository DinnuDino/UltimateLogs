package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.utils.ItemBuilder;
import com.ultimatelogs.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CategorySelectGui implements UltimateLogsGui {

    private final UltimateLogs plugin;
    private final Inventory inventory;

    public CategorySelectGui(UltimateLogs plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>⚡ ULTIMATELOGS</bold></gradient> <dark_gray>▪</dark_gray> <gray>Investigation Hub</gray>"));
        buildGui();
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

        // Title Header / Logo (Slot 4)
        inventory.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("<gradient:#38bdf8:#c084fc><bold>⚡ ULTIMATELOGS ENTERPRISE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Select an investigation subsystem below to</gray>",
                        "<gray>inspect live and historical player activity.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Row 2: Main inspection categories
        // Slot 11: Inventory
        inventory.setItem(11, new ItemBuilder(Material.CHEST)
                .name("<gradient:#38bdf8:#0284c7><bold>🎒 INVENTORY AUDIT & RECOVERY</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Inspect real-time live inventories, point-in-time</gray>",
                        "<gray>historical snapshots, and safe item recovery.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 13: Ender Chest
        inventory.setItem(13, new ItemBuilder(Material.ENDER_CHEST)
                .name("<gradient:#e879f9:#c084fc><bold>📦 ENDER CHEST AUDIT & HISTORY</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Inspect live player Ender Chests and historical</gray>",
                        "<gray>snapshots recorded at every session and event.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 15: Position & Coordinates
        inventory.setItem(15, new ItemBuilder(Material.RECOVERY_COMPASS)
                .name("<gradient:#34d399:#059669><bold>📍 COORDINATE TRAIL & BREADCRUMBS</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Track high-frequency coordinate breadcrumbs,</gray>",
                        "<gray>teleport locations, and stationary intervals.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Row 3 / 4: Additional subsystems
        // Slot 29: Deaths (Highlighted!)
        inventory.setItem(29, new ItemBuilder(Material.SKELETON_SKULL)
                .name("<gradient:#f43f5e:#be123c><bold>💀 DEATH INVESTIGATION ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Look into ALL recorded deaths for any player.</gray>",
                        "<gray>Recovers inventories and ender chests from bugs,</gray>",
                        "<gray>even if player died many times afterwards!</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 31: Item Events
        inventory.setItem(31, new ItemBuilder(Material.DIAMOND)
                .name("<gradient:#facc15:#ca8a04><bold>💎 ITEM TRANSACTIONS & DROPS</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Track item pickups, drops, container transactions,</gray>",
                        "<gray>crafting, and death item drops.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 33: Block Actions & Sessions
        inventory.setItem(33, new ItemBuilder(Material.CLOCK)
                .name("<gradient:#a78bfa:#7c3aed><bold>⏱ SESSIONS & BLOCK ACTIONS</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>View player join/leave history, play durations,</gray>",
                        "<gray>IP addresses, and world block actions.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 49: Close
        inventory.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("<red><bold>CLOSE MENU</bold></red>")
                .asGuiButton()
                .hideAllFlags()
                .build());
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

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);

        if (slot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
            return;
        }

        GuiHolder.Subsystem sub = switch (slot) {
            case 11 -> GuiHolder.Subsystem.INVENTORY;
            case 13 -> GuiHolder.Subsystem.ENDER_CHEST;
            case 15 -> GuiHolder.Subsystem.POSITION;
            case 29 -> GuiHolder.Subsystem.DEATHS;
            case 31 -> GuiHolder.Subsystem.ITEM_TRANSACTIONS;
            case 33 -> GuiHolder.Subsystem.SESSIONS;
            default -> null;
        };

        if (sub != null) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openStage2(staff, sub, 0));
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.1f);
    }
}
