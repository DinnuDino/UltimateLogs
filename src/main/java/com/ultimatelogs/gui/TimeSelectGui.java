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

import java.time.Instant;
import java.util.List;

public class TimeSelectGui implements UltimateLogsGui {

    private final UltimateLogs plugin;
    private final GuiHolder.Subsystem subsystem;
    private final String targetPlayerName;
    private final Inventory inventory;

    public TimeSelectGui(UltimateLogs plugin, GuiHolder.Subsystem subsystem, String targetPlayerName) {
        this.plugin = plugin;
        this.subsystem = subsystem;
        this.targetPlayerName = targetPlayerName;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>" + subsystem.getEmoji() + " " + targetPlayerName + "</bold></gradient> <dark_gray>▪</dark_gray> <gray>Time Filter</gray>"));

        buildGui();
    }

    private void buildGui() {
        // Border: Glass panes
        ItemStack border = ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int s : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) {
            inventory.setItem(s, border);
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
        boolean isOnline = onlineTarget != null && onlineTarget.isOnline();
        boolean isLiveEligible = (subsystem == GuiHolder.Subsystem.INVENTORY || subsystem == GuiHolder.Subsystem.ENDER_CHEST) && isOnline;

        // Slot 13: Live / Latest
        inventory.setItem(13, new ItemBuilder(Material.NETHER_STAR)
                .name(isLiveEligible ? "<gradient:#a7f3d0:#34d399><bold>⚡ LIVE ACTIVE INSPECTION</bold></gradient>" : "<gradient:#e879f9:#c084fc><bold>⏱ LATEST LOG RECORD</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        isLiveEligible ? "<gray>Real-time active inspection with live 10-tick</gray>" : "<gray>Queries the most recent database snapshot</gray>",
                        isLiveEligible ? "<gray>synchronization for <white>" + targetPlayerName + "</white>.</gray>" : "<gray>recorded for <white>" + targetPlayerName + "</white>.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Row 3: Quick rewind presets
        inventory.setItem(20, createPresetItem(Material.FEATHER, "5 MINUTES AGO (-5m)", 300));
        inventory.setItem(21, createPresetItem(Material.CLOCK, "15 MINUTES AGO (-15m)", 900));
        inventory.setItem(22, createPresetItem(Material.COMPASS, "1 HOUR AGO (-1h)", 3600));
        inventory.setItem(23, createPresetItem(Material.SPYGLASS, "6 HOURS AGO (-6h)", 21600));
        inventory.setItem(24, createPresetItem(Material.BOOK, "12 HOURS AGO (-12h)", 43200));
        inventory.setItem(25, createPresetItem(Material.ENDER_EYE, "24 HOURS AGO (-24h)", 86400));

        // Slot 31: BROWSE SNAPSHOT ARCHIVE
        inventory.setItem(31, new ItemBuilder(Material.CHEST_MINECART)
                .name("<gradient:#facc15:#ca8a04><bold>📜 BROWSE SNAPSHOT ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Opens paginated database archive of</gray>",
                        "<gray>every single snapshot recorded for</gray>",
                        "<white>" + targetPlayerName + "</white> <gray>in this category.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO BROWSE ARCHIVE</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        // Slot 45: BACK BUTTON (To Stage 2)
        ItemStack backBtn = new ItemBuilder(Material.ARROW)
                .name("<gold><bold>⬅ BACK TO PLAYERS</bold></gold>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Return to player selection.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO GO BACK</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(45, backBtn);

        // Slot 49: Type in chat
        ItemStack chatBtn = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("<gradient:#ec4899:#db2777><bold>💬 TYPE TIME IN CHAT</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Type exact time, duration, or date directly</gray>",
                        "<gray>in chat (e.g. <yellow>10m</yellow>, <yellow>1h</yellow>, <yellow>15:30:00</yellow>, <yellow>22/10/2026</yellow>).</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO ENTER CHAT PROMPT</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(49, chatBtn);

        // Slot 53: Close
        inventory.setItem(53, new ItemBuilder(Material.BARRIER)
                .name("<red><bold>CLOSE MENU</bold></red>")
                .asGuiButton()
                .hideAllFlags()
                .build());
    }

    private ItemStack createPresetItem(Material mat, String label, long minusSeconds) {
        return new ItemBuilder(mat)
                .name("<gradient:#e879f9:#c084fc><bold>⏱ " + label + "</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Query audit state approximately</gray>",
                        "<gray>" + label.toLowerCase() + " from current time.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO QUERY</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
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
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openStage2(staff, subsystem, 0));
            return;
        }

        if (rawSlot == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
            return;
        }

        // Slot 13: Live / Latest
        if (rawSlot == 13) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getGuiManager().executeCategoryAction(staff, subsystem, targetPlayerName, Instant.now(), "Latest", () -> new TimeSelectGui(plugin, subsystem, targetPlayerName).open(staff));
            });
            return;
        }

        // Preset rewind slots
        long minus = switch (rawSlot) {
            case 20 -> 300L;
            case 21 -> 900L;
            case 22 -> 3600L;
            case 23 -> 21600L;
            case 24 -> 43200L;
            case 25 -> 86400L;
            default -> -1L;
        };

        if (minus > 0) {
            Instant targetTime = Instant.now().minusSeconds(minus);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getGuiManager().executeCategoryAction(staff, subsystem, targetPlayerName, targetTime, targetTime.toString(), () -> new TimeSelectGui(plugin, subsystem, targetPlayerName).open(staff));
            });
            return;
        }

        // Slot 31: BROWSE SNAPSHOT ARCHIVE
        if (rawSlot == 31) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (subsystem == GuiHolder.Subsystem.DEATHS) {
                    plugin.getGuiManager().openDeathHistory(staff, targetPlayerName, 0, () -> new TimeSelectGui(plugin, subsystem, targetPlayerName).open(staff));
                } else if (subsystem == GuiHolder.Subsystem.POSITION) {
                    plugin.getGuiManager().openPositionHistory(staff, targetPlayerName, 0, () -> new TimeSelectGui(plugin, subsystem, targetPlayerName).open(staff));
                } else {
                    plugin.getGuiManager().openSnapshotHistory(staff, subsystem, targetPlayerName, 0, () -> new TimeSelectGui(plugin, subsystem, targetPlayerName).open(staff));
                }
            });
            return;
        }

        // Slot 49: Chat prompt
        if (rawSlot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().promptChatTime(staff, subsystem, targetPlayerName));
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.3f);
    }
}
