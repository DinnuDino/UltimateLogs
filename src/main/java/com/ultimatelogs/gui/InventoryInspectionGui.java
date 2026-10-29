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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InventoryInspectionGui implements UltimateLogsGui {

    private static final String[] ARMOR_NAMES = {
            "Empty Helmet Slot", "Empty Chestplate Slot", "Empty Leggings Slot", "Empty Boots Slot"
    };

    private final UltimateLogs plugin;
    private final Inventory inventory;
    private final String targetPlayerName;
    private final UUID targetUuid;
    private final ItemStack offhand;
    private final String snapshotId;
    private final Instant startTimestamp;
    private final Instant endTimestamp;
    private final int durationSeconds;
    private final boolean isLive;
    private final Player liveTarget;
    private final Runnable backAction;
    private boolean hasOfflineChanges = false;
    private BukkitTask liveSyncTask;

    public InventoryInspectionGui(UltimateLogs plugin, String targetPlayerName, String snapshotId,
                                  Instant startTimestamp, Instant endTimestamp, int durationSeconds,
                                  ItemStack[] storage, ItemStack[] armor, ItemStack offhand,
                                  int expLevel, int totalExp, double health, int foodLevel, String gamemode,
                                  boolean isLive, Player liveTarget, Runnable backAction) {
        this(plugin, targetPlayerName, null, snapshotId, startTimestamp, endTimestamp, durationSeconds,
                storage, armor, offhand, expLevel, totalExp, health, foodLevel, gamemode, isLive, liveTarget, backAction);
    }

    public InventoryInspectionGui(UltimateLogs plugin, String targetPlayerName, UUID targetUuid, String snapshotId,
                                  Instant startTimestamp, Instant endTimestamp, int durationSeconds,
                                  ItemStack[] storage, ItemStack[] armor, ItemStack offhand,
                                  int expLevel, int totalExp, double health, int foodLevel, String gamemode,
                                  boolean isLive, Player liveTarget, Runnable backAction) {
        this.plugin = plugin;
        this.targetPlayerName = targetPlayerName;
        this.targetUuid = targetUuid != null ? targetUuid : (liveTarget != null ? liveTarget.getUniqueId() : Bukkit.getOfflinePlayer(targetPlayerName).getUniqueId());
        this.offhand = offhand != null ? offhand.clone() : null;
        this.snapshotId = snapshotId != null ? snapshotId : "live";
        this.startTimestamp = startTimestamp != null ? startTimestamp : Instant.now();
        this.endTimestamp = endTimestamp != null ? endTimestamp : Instant.now();
        this.durationSeconds = durationSeconds;
        this.isLive = isLive;
        this.liveTarget = liveTarget;
        this.backAction = backAction;

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(
                "<gradient:#c084fc:#f472b6><bold>▶ INVENTORY</bold></gradient> <dark_gray>▪</dark_gray> <white>" + targetPlayerName + "</white>" + (isLive ? " <green>(Live)</green>" : " <yellow>(Snapshot)</yellow>")));

        buildGui(storage, armor, offhand, expLevel, totalExp, health, foodLevel, gamemode);

        if (this.isLive && this.liveTarget != null && this.liveTarget.isOnline()) {
            int interval = plugin.getLogsConfig().getLiveSyncTicks();
            this.liveSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLiveFromTarget, interval, interval);
        }
    }

    private void buildGui(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, int expLevel, int totalExp, double health, int foodLevel, String gamemode) {
        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();

        // Slot 0: Player Head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetPlayerName));
        headMeta.displayName(MessageUtil.parse("<gradient:#e879f9:#c084fc><bold>" + targetPlayerName + "</bold></gradient>"));
        List<String> headLore = new ArrayList<>(List.of(
                "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                "<gray>Health: <red>" + String.format(Locale.US, "%.1f", health) + " / 20.0 ❤</red></gray>",
                "<gray>Food: <gold>" + foodLevel + " / 20</gold></gray>",
                "<gray>Gamemode: <white>" + gamemode + "</white></gray>",
                isLive ? "<green><bold>• Live Active Inventory</bold></green>" : "<gray>Snapshot: <yellow>" + this.snapshotId + "</yellow></gray>"
        ));
        headLore.addAll(formatter.buildIntervalLore(startTimestamp, endTimestamp, durationSeconds));
        headLore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");
        headMeta.lore(headLore.stream().map(MessageUtil::parse).toList());
        headMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        headMeta.getPersistentDataContainer().set(ItemBuilder.GUI_BUTTON_KEY, PersistentDataType.BYTE, (byte) 1);
        head.setItemMeta(headMeta);
        inventory.setItem(0, head);

        // Slot 1: XP Bottle
        ItemStack xpBottle = new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("<yellow><bold>" + expLevel + " Enchantment Levels</bold></yellow>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Total Experience: <white>" + totalExp + " XP</white></gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(1, xpBottle);

        // Slots 2-5: Armor (Helmet, Chestplate, Leggings, Boots)
        for (int i = 0; i < 4; i++) {
            int armorIdx = 3 - i;
            ItemStack piece = (armor != null && armor.length > armorIdx) ? armor[armorIdx] : null;
            setEquipment(2 + i, piece, ARMOR_NAMES[i]);
        }

        // Slot 6: BACK BUTTON (Universal return option)
        ItemStack backBtn = new ItemBuilder(Material.ARROW)
                .name("<gold><bold>⬅ RETURN / BACK</bold></gold>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Click to return to the previous</gray>",
                        "<gray>snapshot list or selection menu.</gray>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<yellow><bold>CLICK TO GO BACK</bold></yellow>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(6, backBtn);

        // Slot 7: Real-Time & Interval Information
        List<String> infoLore = new ArrayList<>(List.of(
                "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                isLive ? "<gray>Status: <green><bold>ONLINE (Live 10-tick Sync)</bold></green></gray>" : "<gray>Status: <yellow>Historical Snapshot</yellow></gray>"
        ));
        infoLore.addAll(formatter.buildIntervalLore(startTimestamp, endTimestamp, durationSeconds));
        infoLore.add("<green><bold>• Taking Items: ENABLED</bold></green>");
        infoLore.add(isLive ? "<green><bold>• Placing Items: ENABLED</bold></green>" : "<red><bold>• Placing Items: BLOCKED</bold></red>");
        infoLore.add("<dark_gray>▪ --------------------------------- ▪</dark_gray>");

        ItemStack infoItem = new ItemBuilder(isLive ? Material.NETHER_STAR : Material.CLOCK)
                .name(isLive ? "<green><bold>⚡ Real-Time Live Inspection</bold></green>" : "<gold><bold>⏱ Snapshot Time & Interval</bold></gold>")
                .lore(infoLore)
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(7, infoItem);

        // Slot 8: Quick Switch / Subsystem Cross-Links
        ItemStack quickSwitch = new ItemBuilder(Material.CHEST)
                .name("<gradient:#38bdf8:#0284c7><bold>⇄ QUICK SWITCH MENU</bold></gradient>")
                .lore(List.of(
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>",
                        "<gray>Switch view for <white>" + targetPlayerName + "</white>:</gray>",
                        "<yellow>• Left-Click:</yellow> <light_purple>Open Ender Chest</light_purple>",
                        "<yellow>• Right-Click:</yellow> <aqua>Coordinate Trail & Position</aqua>",
                        "<yellow>• Shift+Left:</yellow> <red>View Death History</red>",
                        "<yellow>• Shift+Right:</gold> <gold>Browse Snapshot Archive</gold>",
                        "<dark_gray>▪ --------------------------------- ▪</dark_gray>"
                ))
                .asGuiButton()
                .hideAllFlags()
                .build();
        inventory.setItem(8, quickSwitch);

        // Row 1: 9 Divider Panes (Slots 9 to 17)
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, ItemBuilder.pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Rows 2-4: Storage (Slots 18 to 44) & Row 5: Hotbar (Slots 45 to 53)
        if (storage != null) {
            for (int i = 0; i < 27; i++) {
                int storageIdx = 9 + i;
                if (storageIdx < storage.length && storage[storageIdx] != null && !storage[storageIdx].getType().isAir()) {
                    inventory.setItem(18 + i, storage[storageIdx].clone());
                }
            }
            for (int i = 0; i < 9; i++) {
                if (i < storage.length && storage[i] != null && !storage[i].getType().isAir()) {
                    inventory.setItem(45 + i, storage[i].clone());
                }
            }
        }
    }

    private void setEquipment(int slot, ItemStack item, String placeholderName) {
        if (item != null && !item.getType().isAir() && !ItemBuilder.isGuiButton(item)) {
            inventory.setItem(slot, item.clone());
        } else {
            inventory.setItem(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .name("<dark_gray>" + placeholderName + "</dark_gray>")
                    .asGuiButton()
                    .hideAllFlags()
                    .build());
        }
    }

    private void syncLiveFromTarget() {
        if (liveTarget == null || !liveTarget.isOnline()) return;
        ItemStack[] currentStorage = liveTarget.getInventory().getStorageContents();
        for (int i = 0; i < 27; i++) {
            int storageIdx = 9 + i;
            ItemStack targetItem = (storageIdx < currentStorage.length) ? currentStorage[storageIdx] : null;
            ItemStack guiItem = inventory.getItem(18 + i);
            if (!itemsMatch(guiItem, targetItem)) {
                inventory.setItem(18 + i, targetItem != null ? targetItem.clone() : null);
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack targetItem = (i < currentStorage.length) ? currentStorage[i] : null;
            ItemStack guiItem = inventory.getItem(45 + i);
            if (!itemsMatch(guiItem, targetItem)) {
                inventory.setItem(45 + i, targetItem != null ? targetItem.clone() : null);
            }
        }

        ItemStack[] currentArmor = liveTarget.getInventory().getArmorContents();
        for (int i = 0; i < 4; i++) {
            int armorIdx = 3 - i;
            ItemStack targetArmor = (currentArmor != null && armorIdx < currentArmor.length) ? currentArmor[armorIdx] : null;
            ItemStack guiArmor = inventory.getItem(2 + i);
            if (!itemsMatch(guiArmor, targetArmor)) {
                setEquipment(2 + i, targetArmor, ARMOR_NAMES[i]);
            }
        }
    }

    private boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a == null || a.getType().isAir()) return b == null || b.getType().isAir();
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private void syncAllToLiveTarget() {
        if (!isLive || liveTarget == null || !liveTarget.isOnline()) return;
        ItemStack[] newStorage = new ItemStack[36];
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(45 + i);
            newStorage[i] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getItem(18 + i);
            newStorage[9 + i] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        liveTarget.getInventory().setStorageContents(newStorage);

        ItemStack[] newArmor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            int armorIdx = 3 - i;
            ItemStack item = inventory.getItem(2 + i);
            newArmor[armorIdx] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        liveTarget.getInventory().setArmorContents(newArmor);
    }

    private void syncToOfflineTarget(Player staff) {
        if (isLive || !hasOfflineChanges) return;
        ItemStack[] newStorage = new ItemStack[36];
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(45 + i);
            newStorage[i] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getItem(18 + i);
            newStorage[9 + i] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }

        ItemStack[] newArmor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            int armorIdx = 3 - i;
            ItemStack item = inventory.getItem(2 + i);
            newArmor[armorIdx] = (item != null && !ItemBuilder.isGuiButton(item)) ? item.clone() : null;
        }

        plugin.getDatabaseManager().savePendingOfflineInventory(
                targetPlayerName, targetUuid, newStorage, newArmor, this.offhand, staff != null ? staff.getName() : "System"
        );
    }

    private boolean isValidArmorForSlot(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Material mat = item.getType();
        String name = mat.name();
        return switch (slot) {
            case 2 -> name.endsWith("_HELMET") || mat == Material.TURTLE_HELMET || mat == Material.CARVED_PUMPKIN;
            case 3 -> name.endsWith("_CHESTPLATE") || mat == Material.ELYTRA;
            case 4 -> name.endsWith("_LEGGINGS");
            case 5 -> name.endsWith("_BOOTS");
            default -> false;
        };
    }

    private boolean isFunctionalSlot(int rawSlot) {
        if (rawSlot == 0 || rawSlot == 1 || rawSlot == 6 || rawSlot == 7 || rawSlot == 8) return true;
        if (rawSlot >= 9 && rawSlot <= 17) return true;
        if (rawSlot >= 2 && rawSlot <= 5) {
            if (isLive) {
                return false; // Handled specially for live equipping and taking
            }
            ItemStack item = inventory.getItem(rawSlot);
            return item == null || ItemBuilder.isGuiButton(item);
        }
        return false;
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

        if (rawSlot >= 54) {
            if (isLive && (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
            }
            return;
        }

        // Functional / System buttons (0, 1, 6, 7, 8, 9-17)
        if (isFunctionalSlot(rawSlot)) {
            event.setCancelled(true);
            staff.updateInventory();

            ClickType click = event.getClick();

            // Slot 6: BACK BUTTON
            if (rawSlot == 6) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (backAction != null) {
                        backAction.run();
                    } else {
                        plugin.getGuiManager().openStage3(staff, GuiHolder.Subsystem.INVENTORY, targetPlayerName);
                    }
                });
                return;
            }

            // Slot 8: QUICK SWITCH MENU
            if (rawSlot == 8) {
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
                Runnable returnToInv = () -> plugin.getGuiManager().openInventoryInspection(staff, targetPlayerName, isLive, backAction);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (click.isShiftClick() && click.isLeftClick()) {
                        plugin.getGuiManager().openDeathHistory(staff, targetPlayerName, 0, returnToInv);
                    } else if (click.isShiftClick() && click.isRightClick()) {
                        plugin.getGuiManager().openSnapshotHistory(staff, GuiHolder.Subsystem.INVENTORY, targetPlayerName, 0, returnToInv);
                    } else if (click.isRightClick()) {
                        plugin.getGuiManager().openPositionHistory(staff, targetPlayerName, 0, returnToInv);
                    } else {
                        plugin.getGuiManager().openEnderChestInspection(staff, targetPlayerName, isLive, returnToInv);
                    }
                });
                return;
            }

            return;
        }

        // Armor handling (slots 2 to 5) in live mode:
        if (isLive && rawSlot >= 2 && rawSlot <= 5) {
            ItemStack cursor = staff.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!isValidArmorForSlot(rawSlot, cursor)) {
                    event.setCancelled(true);
                    staff.updateInventory();
                    return;
                }

                event.setCancelled(true);
                ItemStack current = inventory.getItem(rawSlot);
                boolean currentIsRealArmor = current != null && !current.getType().isAir() && !ItemBuilder.isGuiButton(current);

                ItemStack toEquip = cursor.clone();
                toEquip.setAmount(1);
                inventory.setItem(rawSlot, toEquip);

                if (currentIsRealArmor) {
                    staff.setItemOnCursor(current.clone());
                } else {
                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        staff.setItemOnCursor(cursor);
                    } else {
                        staff.setItemOnCursor(null);
                    }
                }
                staff.playSound(staff.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.0f);
                staff.updateInventory();
                Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
                return;
            } else {
                ItemStack current = inventory.getItem(rawSlot);
                if (current == null || ItemBuilder.isGuiButton(current)) {
                    event.setCancelled(true);
                    staff.updateInventory();
                    return;
                }
                event.setCancelled(false);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack after = inventory.getItem(rawSlot);
                    if (after == null || after.getType().isAir()) {
                        setEquipment(rawSlot, null, ARMOR_NAMES[rawSlot - 2]);
                    }
                    syncAllToLiveTarget();
                });
                return;
            }
        }

        // 3. Modifiable target slots: Real armor in slots 2-5, Storage in 18-44, Hotbar in 45-53
        if (ItemBuilder.isGuiButton(event.getCurrentItem())) {
            event.setCancelled(true);
            staff.updateInventory();
            return;
        }

        if (!isLive) {
            // Placing items into historical snapshots / previous logs is strictly BLOCKED!
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

            // Taking items from historical snapshots is ENABLED
            if (rawSlot >= 2 && rawSlot <= 5) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack current = inventory.getItem(rawSlot);
                    if (current == null || current.getType().isAir()) {
                        setEquipment(rawSlot, null, ARMOR_NAMES[rawSlot - 2]);
                    }
                });
            }

            this.hasOfflineChanges = true;
            Bukkit.getScheduler().runTask(plugin, () -> syncToOfflineTarget(staff));
            return;
        }

        // Live Mode (isLive == true):
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

        // Placing, taking, and swapping items in storage/hotbar are ENABLED!
        Bukkit.getScheduler().runTask(plugin, this::syncAllToLiveTarget);
    }

    @Override
    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!isLive) {
            for (int slot : event.getRawSlots()) {
                if (slot < 54) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.updateInventory();
                    }
                    return;
                }
            }
            return;
        }

        // Live mode: functional/control slots (0 to 17) are strictly protected
        for (int slot : event.getRawSlots()) {
            if (slot < 18) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.updateInventory();
                }
                return;
            }
        }

        // Drag into storage & hotbar slots (18 to 53) is ENABLED in live mode
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
                MessageUtil.send(staff, "<green>✔ Offline changes for <white>" + targetPlayerName + "</white> saved. They will sync when they rejoin.</green>");
            }
        }
    }
}
