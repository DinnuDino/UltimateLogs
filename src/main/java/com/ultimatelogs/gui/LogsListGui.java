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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LogsListGui implements UltimateLogsGui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public record LogDisplayEntry(Material icon, String title, List<String> details) {}

    private final UltimateLogs plugin;
    private final GuiHolder.Subsystem subsystem;
    private final String targetPlayerName;
    private final String displayTime;
    private final List<LogDisplayEntry> entries;
    private final int page;
    private final Runnable backAction;
    private final Inventory inventory;

    public LogsListGui(UltimateLogs plugin, GuiHolder.Subsystem subsystem, String targetPlayerName,
                       String displayTime, List<LogDisplayEntry> entries, int page, Runnable backAction) {
        this.plugin = plugin;
        this.subsystem = subsystem;
        this.targetPlayerName = targetPlayerName;
        this.displayTime = displayTime;
        this.entries = entries;
        this.page = Math.max(0, page);
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>" + subsystem.getEmoji() + " " + subsystem.getDisplayName().toUpperCase() + " LOGS</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>"));

        buildGui();
    }

    private void buildGui() {
        ItemStack filler = ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack border = ItemBuilder.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int s : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) {
            inventory.setItem(s, border);
        }

        // Header at Slot 4
        ItemStack header = new ItemBuilder(Material.BOOK)
                .name("<gradient:#c084fc:#f472b6><bold>" + subsystem.getDisplayName().toUpperCase() + " AUDIT TRAIL</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Target Player: <white>" + targetPlayerName + "</white></gray>",
                        "<gray>Time Filter: <yellow>≤ " + displayTime + "</yellow></gray>",
                        "<gray>Records Found: <gold>" + entries.size() + "</gold></gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(4, header);

        // Paginate entries
        int pageSize = SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / pageSize));
        int safePage = Math.min(page, totalPages - 1);
        int start = safePage * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        for (int i = start; i < end; i++) {
            LogDisplayEntry entry = entries.get(i);
            int slot = SLOTS[i - start];
            ItemStack item = new ItemBuilder(entry.icon())
                    .name(entry.title())
                    .lore(entry.details())
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(slot, item);
        }

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

        // Pagination buttons
        if (safePage > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>◀ PREVIOUS PAGE</bold></aqua>")
                    .lore(List.of("<gray>Page " + safePage + " of " + totalPages + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(48, prev);
        }
        if (safePage < totalPages - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>NEXT PAGE ▶</bold></aqua>")
                    .lore(List.of("<gray>Page " + (safePage + 2) + " of " + totalPages + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build();
            inventory.setItem(50, next);
        }

        // Slot 53: Close
        ItemStack closeBtn = new ItemBuilder(Material.BARRIER)
                .name("<red><bold>CLOSE MENU</bold></red>")
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(53, closeBtn);
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

        int pageSize = SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / pageSize));
        if (rawSlot == 48 && page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> new LogsListGui(plugin, subsystem, targetPlayerName, displayTime, entries, page - 1, backAction).open(staff));
            return;
        }
        if (rawSlot == 50 && page < totalPages - 1) {
            Bukkit.getScheduler().runTask(plugin, () -> new LogsListGui(plugin, subsystem, targetPlayerName, displayTime, entries, page + 1, backAction).open(staff));
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }
}
