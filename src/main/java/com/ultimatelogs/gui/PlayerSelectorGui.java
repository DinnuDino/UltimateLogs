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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlayerSelectorGui implements UltimateLogsGui {

    private static final int[] USABLE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final UltimateLogs plugin;
    private final GuiHolder.Subsystem subsystem;
    private final int page;
    private final Inventory inventory;
    private final List<Player> onlinePlayers;

    public PlayerSelectorGui(UltimateLogs plugin, GuiHolder.Subsystem subsystem, int page) {
        this.plugin = plugin;
        this.subsystem = subsystem;
        this.page = Math.max(0, page);
        this.onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        this.onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>" + subsystem.getEmoji() + " " + subsystem.getDisplayName().toUpperCase() + "</bold></gradient> <dark_gray>▪</dark_gray> <gray>Select Player</gray>"));

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

        // Header (Slot 4)
        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name("<gradient:#38bdf8:#c084fc><bold>SELECT TARGET PLAYER</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Subsystem: <aqua>" + subsystem.getDisplayName() + "</aqua></gray>",
                        "<gray>Online Players: <yellow>" + onlinePlayers.size() + "</yellow></gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build());

        int pageSize = USABLE_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) onlinePlayers.size() / pageSize));
        int safePage = Math.min(page, totalPages - 1);
        int start = safePage * pageSize;
        int end = Math.min(start + pageSize, onlinePlayers.size());

        for (int i = start; i < end; i++) {
            Player p = onlinePlayers.get(i);
            int slot = USABLE_SLOTS[i - start];
            inventory.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(p.getName())
                    .name("<gradient:#e879f9:#c084fc><bold>" + p.getName() + "</bold></gradient>")
                    .lore(List.of(
                            "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                            "<gray>Status: <green>Online</green></gray>",
                            "<gray>World: <white>" + p.getWorld().getName() + "</white></gray>",
                            "<gray>Ping: <white>" + p.getPing() + "ms</white></gray>",
                            "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                            "<yellow><bold>CLICK TO SELECT</bold></yellow>"
                    ))
                    .asGuiButton()
                    .hideAllFlags()
                    .build());
        }

        // Slot 45: BACK BUTTON (To Stage 1)
        ItemStack backBtn = new ItemBuilder(Material.ARROW)
                .name("<gold><bold>⬅ BACK TO CATEGORIES</bold></gold>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Return to category selection.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO GO BACK</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(45, backBtn);

        // Slot 49: Type offline username
        ItemStack chatInputBtn = new ItemBuilder(Material.NAME_TAG)
                .name("<gradient:#e879f9:#c084fc><bold>⌨ TYPE CUSTOM / OFFLINE PLAYER</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Type any offline or historical username</gray>",
                        "<gray>directly in server chat to investigate.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO ENTER USERNAME</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(49, chatInputBtn);

        // Pagination buttons
        if (safePage > 0) {
            inventory.setItem(48, new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>◀ PREVIOUS PAGE</bold></aqua>")
                    .lore(List.of("<gray>Go to Page " + safePage + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build());
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(50, new ItemBuilder(Material.ARROW)
                    .name("<aqua><bold>NEXT PAGE ▶</bold></aqua>")
                    .lore(List.of("<gray>Go to Page " + (safePage + 2) + "</gray>"))
                    .asGuiButton()
                    .hideAllFlags()
                    .build());
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
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openStage1(staff));
            return;
        }

        // Slot 49: Offline / Custom username
        if (rawSlot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().promptChatUsername(staff, subsystem));
            return;
        }

        int pageSize = USABLE_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) onlinePlayers.size() / pageSize));
        if (rawSlot == 48 && page > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> new PlayerSelectorGui(plugin, subsystem, page - 1).open(staff));
            return;
        }
        if (rawSlot == 50 && page < totalPages - 1) {
            Bukkit.getScheduler().runTask(plugin, () -> new PlayerSelectorGui(plugin, subsystem, page + 1).open(staff));
            return;
        }

        ItemStack clicked = inventory.getItem(rawSlot);
        if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta skull) {
            if (skull.getOwningPlayer() != null && skull.getOwningPlayer().getName() != null) {
                String targetName = skull.getOwningPlayer().getName();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (subsystem == GuiHolder.Subsystem.DEATHS) {
                        plugin.getGuiManager().openDeathHistory(staff, targetName, 0, () -> new PlayerSelectorGui(plugin, subsystem, page).open(staff));
                    } else {
                        plugin.getGuiManager().openStage3(staff, subsystem, targetName);
                    }
                });
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
    }
}
