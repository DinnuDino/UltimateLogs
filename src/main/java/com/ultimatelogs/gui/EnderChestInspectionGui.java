package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.utils.ItemBuilder;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnderChestInspectionGui implements UltimateLogsGui {

    private final UltimateLogs plugin;
    private final Inventory inventory;
    private final String targetPlayerName;
    private final UUID targetUuid;
    private final String snapshotId;
    private final Instant startTimestamp;
    private final Instant endTimestamp;
    private final int durationSeconds;
    private final boolean isLive;
    private final Player liveTarget;
    private final Runnable backAction;
    private boolean hasOfflineChanges = false;
    private BukkitTask liveSyncTask;

    public EnderChestInspectionGui(UltimateLogs plugin, String targetPlayerName, String snapshotId,
                                   Instant startTimestamp, Instant endTimestamp, int durationSeconds,
                                   ItemStack[] echestContents, boolean isLive, Player liveTarget, Runnable backAction) {
        this(plugin, targetPlayerName, null, snapshotId, startTimestamp, endTimestamp, durationSeconds,
                echestContents, isLive, liveTarget, backAction);
    }

    public EnderChestInspectionGui(UltimateLogs plugin, String targetPlayerName, UUID targetUuid, String snapshotId,
                                   Instant startTimestamp, Instant endTimestamp, int durationSeconds,
                                   ItemStack[] echestContents, boolean isLive, Player liveTarget, Runnable backAction) {
        this.plugin = plugin;
        this.targetPlayerName = targetPlayerName;
        this.targetUuid = targetUuid != null ? targetUuid : (liveTarget != null ? liveTarget.getUniqueId() : Bukkit.getOfflinePlayer(targetPlayerName).getUniqueId());
        this.snapshotId = snapshotId != null ? snapshotId : "live";
        this.startTimestamp = startTimestamp != null ? startTimestamp : Instant.now();
        this.endTimestamp = endTimestamp != null ? endTimestamp : Instant.now();
        this.durationSeconds = durationSeconds;
        this.isLive = isLive;
        this.liveTarget = liveTarget;
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 36, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>▶ ENDER CHEST</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>" + (isLive ? " <green>(Live)</green>" : " <yellow>(Snapshot)</yellow>")));

        buildGui(echestContents);

        if (this.isLive && this.liveTarget != null && this.liveTarget.isOnline()) {
            int interval = plugin.getLogsConfig().getLiveSyncTicks();
            this.liveSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLiveFromTarget, interval, interval);
        }
    }

    private void buildGui(ItemStack[] contents) {
        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();

        // Slots 0-26: The 27 Ender Chest slots
        if (contents != null) {
            for (int i = 0; i < Math.min(27, contents.length); i++) {
                if (contents[i] != null && !contents[i].getType().isAir()) {
                    inventory.setItem(i, contents[i].clone());
                }
            }
        }

        // Fill bottom control row (27 to 35)
        for (int i = 27; i < 36; i++) {
            inventory.setItem(i, ItemBuilder.pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Slot 27: BACK BUTTON
        ItemStack backBtn = new ItemBuilder(Material.ARROW)
                .name("<gold><bold>⬅ RETURN / BACK</bold></gold>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Click to return to the previous</gray>",
                        "<gray>snapshot archive or menu.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO GO BACK</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(27, backBtn);

        // Slot 30: Quick Switch to Inventory
        ItemStack toInvBtn = new ItemBuilder(Material.CHEST)
                .name("<gradient:#38bdf8:#0284c7><bold>🎒 SWITCH TO INVENTORY</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Inspect player's full inventory,</gray>",
                        "<gray>armor, offhand, and XP levels.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow>CLICK TO OPEN INVENTORY</yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(30, toInvBtn);

        // Slot 31: Real-time and Interval Information
        List<String> infoLore = new ArrayList<>(List.of(
                "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                "<gray>Player: <white>" + targetPlayerName + "</white></gray>",
                isLive ? "<gray>Status: <green><bold>ONLINE (Live 10-tick Sync)</bold></green></gray>" : "<gray>Status: <yellow>Historical Snapshot</yellow></gray>"
        ));
        infoLore.addAll(formatter.buildIntervalLore(startTimestamp, endTimestamp, durationSeconds));
        infoLore.add("<green><bold>• Taking Items: ENABLED</bold></green>");
        infoLore.add(isLive ? "<green><bold>• Placing Items: ENABLED</bold></green>" : "<red><bold>• Placing Items: BLOCKED</bold></red>");
        infoLore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");

        ItemStack infoItem = new ItemBuilder(isLive ? Material.NETHER_STAR : Material.CLOCK)
                .name(isLive ? "<light_purple><bold>⚡ Live Ender Chest</bold></light_purple>" : "<light_purple><bold>⏱ Ender Chest Time & Interval</bold></light_purple>")
                .lore(infoLore)
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(31, infoItem);

        // Slot 32: Quick Switch to Death History
        ItemStack toDeathsBtn = new ItemBuilder(Material.SKELETON_SKULL)
                .name("<gradient:#f43f5e:#be123c><bold>💀 VIEW DEATH HISTORY</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Browse all recorded deaths for</gray>",
                        "<white>" + targetPlayerName + "</white> <gray>and recover items.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow>CLICK TO VIEW DEATHS</yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(32, toDeathsBtn);

        // Slot 35: Browse Echest Snapshots
        ItemStack toHistoryBtn = new ItemBuilder(Material.CHEST_MINECART)
                .name("<gradient:#e879f9:#c084fc><bold>📜 BROWSE ECHEST ARCHIVE</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Opens paginated database archive of</gray>",
                        "<gray>every recorded Ender Chest snapshot.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow>CLICK TO BROWSE SNAPSHOTS</yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(35, toHistoryBtn);
    }

    private void syncLiveFromTarget() {
        if (liveTarget == null || !liveTarget.isOnline()) return;
        ItemStack[] current = liveTarget.getEnderChest().getContents();
        for (int i = 0; i < 27; i++) {
            ItemStack targetItem = (i < current.length) ? current[i] : null;
            ItemStack guiItem = inventory.getItem(i);
            if (!itemsMatch(guiItem, targetItem)) {
                inventory.setItem(i, targetItem != null ? targetItem.clone() : null);
            }
        }
    }

    private boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a == null || a.getType().isAir()) return b == null || b.getType().isAir();
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private void syncAllToLiveTarget() {
        if (!isLive || liveTarget == null || !liveTarget.isOnline()) return;
        ItemStack[] newContents = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getItem(i);
            newContents[i] = (item != null && !item.getType().isAir() && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        liveTarget.getEnderChest().setContents(newContents);
    }

    private void syncToOfflineTarget(Player staff) {
        if (isLive || !hasOfflineChanges) return;
        ItemStack[] newContents = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getItem(i);
            newContents[i] = (item != null && !item.getType().isAir() && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        plugin.getDatabaseManager().savePendingOfflineEnderChest(
                targetPlayerName, targetUuid, newContents, staff != null ? staff.getName() : "System"
        );
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public boolean isInspectionGui() {
        return true;
    }

    @Override
    public boolean isLive() {
        return this.isLive;
    }

    @Override
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            if (ItemBuilder.isGuiButton(event.getCursor())) {
                event.setCancelled(true);
                event.getView().setCursor(null);
                staff.updateInventory();
            }
            return;
        }

        if (rawSlot >= 36) {
            if (isLive && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
            }
            return;
        }

        // Control bar (slots 27 to 35) is STRICTLY PROTECTED!
        if (rawSlot >= 27 && rawSlot < 36) {
            event.setCancelled(true);
            staff.updateInventory();

            // Slot 27: BACK BUTTON
            if (rawSlot == 27) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (backAction != null) {
                        backAction.run();
                    } else {
                        plugin.getGuiManager().openStage3(staff, GuiHolder.Subsystem.ENDER_CHEST, targetPlayerName);
                    }
                });
                return;
            }

            // Slot 30: Switch to Inventory
            if (rawSlot == 30) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getGuiManager().openInventoryInspection(staff, targetPlayerName, isLive,
                            () -> plugin.getGuiManager().openEnderChestInspection(staff, targetPlayerName, isLive, backAction));
                });
                return;
            }

            // Slot 32: Switch to Deaths
            if (rawSlot == 32) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getGuiManager().openDeathHistory(staff, targetPlayerName, 0,
                            () -> plugin.getGuiManager().openEnderChestInspection(staff, targetPlayerName, isLive, backAction));
                });
                return;
            }

            // Slot 35: Switch to Snapshot Archive
            if (rawSlot == 35) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getGuiManager().openSnapshotHistory(staff, GuiHolder.Subsystem.ENDER_CHEST, targetPlayerName, 0,
                            () -> plugin.getGuiManager().openEnderChestInspection(staff, targetPlayerName, isLive, backAction));
                });
                return;
            }

            return;
        }

        // 3. Ender Chest slots (slots 0 to 26)
        if (ItemBuilder.isGuiButton(event.getCurrentItem())) {
            event.setCancelled(true);
            staff.updateInventory();
            return;
        }

        if (!isLive) {
            // Placing items into historical snapshot / previous logs is strictly BLOCKED!
            if (event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME ||
                event.getAction() == InventoryAction.SWAP_WITH_CURSOR ||
                event.getAction() == InventoryAction.HOTBAR_SWAP ||
                event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD ||
                event.getClick() == ClickType.NUMBER_KEY ||
                event.getClick() == ClickType.DROP ||
                event.getClick() == ClickType.CONTROL_DROP ||
                event.getAction() == InventoryAction.DROP_ALL_SLOT ||
                event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                event.getClick() == ClickType.MIDDLE ||
                event.getAction() == InventoryAction.CLONE_STACK) {
                event.setCancelled(true);
                staff.updateInventory();
                return;
            }

            // Taking items from historical snapshot is ENABLED
            this.hasOfflineChanges = true;
            Bukkit.getScheduler().runTask(plugin, () -> syncToOfflineTarget(staff));
            return;
        }

        // Live mode (isLive == true):
        // Block creative middle click clone and dropping items out of GUI onto ground
        if (event.getClick() == ClickType.MIDDLE ||
            event.getAction() == InventoryAction.CLONE_STACK ||
            event.getClick() == ClickType.DROP ||
            event.getClick() == ClickType.CONTROL_DROP ||
            event.getAction() == InventoryAction.DROP_ALL_SLOT ||
            event.getAction() == InventoryAction.DROP_ONE_SLOT) {
            event.setCancelled(true);
            staff.updateInventory();
            return;
        }

        // Placing, taking, and swapping items in ender chest slots (0 to 26) are ENABLED!
        Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
    }

    @Override
    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!isLive) {
            for (int slot : event.getRawSlots()) {
                if (slot < 36) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.updateInventory();
                    }
                    return;
                }
            }
            return;
        }

        // Live mode: control bar (slots 27 to 35) is strictly protected
        for (int slot : event.getRawSlots()) {
            if (slot >= 27 && slot < 36) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.updateInventory();
                }
                return;
            }
        }

        // Drag into ender chest slots (0 to 26) is ENABLED in live mode
        Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
    }

    @Override
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (liveSyncTask != null) {
            liveSyncTask.cancel();
        }
        if (!isLive && hasOfflineChanges) {
            if (event.getPlayer() instanceof Player staff) {
                syncToOfflineTarget(staff);
                MessageUtil.send(staff, "<green>✔ Offline Ender Chest changes for <white>" + targetPlayerName + "</white> saved. They will sync when they rejoin.</green>");
            }
        }
    }
}
