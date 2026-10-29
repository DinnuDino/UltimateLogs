package com.ultimatelogs.gui;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.database.DatabaseManager;
import com.ultimatelogs.database.models.ActionLogRecord;
import com.ultimatelogs.database.models.ItemEventRecord;
import com.ultimatelogs.database.models.PositionSnapshotRecord;
import com.ultimatelogs.database.models.SessionRecord;
import com.ultimatelogs.utils.ItemBuilder;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import com.ultimatelogs.utils.TimeParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private final UltimateLogs plugin;
    private final TimeParser timeParser;
    private final Map<UUID, ChatPrompt> activePrompts = new ConcurrentHashMap<>();

    private record ChatPrompt(UUID staffUuid, PromptType type, GuiHolder.Subsystem subsystem, String targetPlayerName, long createdMillis) {}
    private enum PromptType { USERNAME, TIME }

    public GuiManager(UltimateLogs plugin) {
        this.plugin = plugin;
        this.timeParser = new TimeParser(plugin.getLogsConfig().getZoneId());
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openStage1(Player player) {
        new CategorySelectGui(plugin).open(player);
    }

    public void openStage2(Player player, GuiHolder.Subsystem subsystem, int page) {
        new PlayerSelectorGui(plugin, subsystem, page).open(player);
    }

    public void openStage3(Player player, GuiHolder.Subsystem subsystem, String targetPlayerName) {
        new TimeSelectGui(plugin, subsystem, targetPlayerName).open(player);
    }

    public void openSnapshotHistory(Player player, GuiHolder.Subsystem subsystem, String targetPlayerName, int page, Runnable backAction) {
        new SnapshotHistoryGui(plugin, subsystem, targetPlayerName, page, backAction).open(player);
    }

    public void openPositionHistory(Player player, String targetPlayerName, int page, Runnable backAction) {
        new PositionHistoryGui(plugin, targetPlayerName, page, backAction).open(player);
    }

    public void openDeathHistory(Player player, String targetPlayerName, int page, Runnable backAction) {
        new DeathHistoryGui(plugin, targetPlayerName, page, backAction).open(player);
    }

    public void openInventoryInspection(Player staff, String targetPlayerName, boolean preferLive, Runnable backAction) {
        Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
        if (preferLive && onlineTarget != null && onlineTarget.isOnline()) {
            InventoryInspectionGui gui = new InventoryInspectionGui(
                    plugin, onlineTarget.getName(), onlineTarget.getUniqueId(), "live", Instant.now(), Instant.now(), 1,
                    onlineTarget.getInventory().getStorageContents(),
                    onlineTarget.getInventory().getArmorContents(),
                    onlineTarget.getInventory().getItemInOffHand(),
                    onlineTarget.getLevel(), onlineTarget.getTotalExperience(),
                    onlineTarget.getHealth(), onlineTarget.getFoodLevel(), onlineTarget.getGameMode().name(),
                    true, onlineTarget, backAction
            );
            staff.openInventory(gui.getInventory());
            MessageUtil.send(staff, "<green>Opened live active inventory for <white>" + onlineTarget.getName() + "</white>.</green>");
            return;
        }

        UUID targetUuid = null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetPlayerName);
        if (op != null) {
            targetUuid = op.getUniqueId();
        }

        // 1. Check if there are pending offline modifications for this player
        DatabaseManager.PendingOfflineModification pending = plugin.getDatabaseManager().getPendingOfflineModification(targetPlayerName);
        if (pending != null && pending.getInventoryData() != null) {
            ItemStack[] storage = plugin.getInventorySerializer().deserializeItemArray(pending.getInventoryData());
            ItemStack[] armor = pending.getArmorData() != null ? plugin.getInventorySerializer().deserializeItemArray(pending.getArmorData()) : new ItemStack[4];
            ItemStack offhand = pending.getOffhandData() != null ? plugin.getInventorySerializer().deserializeItem(pending.getOffhandData()) : null;

            UUID finalTargetUuid = targetUuid != null ? targetUuid : (pending.getPlayerUuid() != null ? UUID.fromString(pending.getPlayerUuid()) : UUID.randomUUID());
            InventoryInspectionGui gui = new InventoryInspectionGui(
                    plugin, targetPlayerName, finalTargetUuid, "pending-offline", pending.getModifiedAt(), Instant.now(), 1,
                    storage, armor, offhand, 0, 0, 20.0, 20, "SURVIVAL",
                    false, null, backAction
            );
            staff.openInventory(gui.getInventory());
            MessageUtil.send(staff, "<green>Opened offline inventory for <white>" + targetPlayerName + "</white> (with pending modifications).</green>");
            return;
        }

        // 2. Query latest snapshot from database
        MessageUtil.send(staff, "<gray>Querying most recent inventory snapshot for <white>" + targetPlayerName + "</white>...</gray>");
        UUID finalTargetUuid1 = targetUuid;
        plugin.getDatabaseManager().queryLatestInventorySnapshot(targetPlayerName).thenAccept(snap -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (snap != null) {
                    TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();
                    String intervalStr = formatter.formatIntervalWithDuration(snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds());
                    MessageUtil.send(staff, "<green>Found snapshot: <yellow>" + snap.snapshotId() + "</yellow> (" + intervalStr + ").</green>");

                    InventoryInspectionGui gui = new InventoryInspectionGui(
                            plugin, targetPlayerName, finalTargetUuid1, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                            snap.inventory(), snap.armor(), snap.offhand(), snap.expLevel(), snap.totalExp(), snap.health(), snap.hunger(), snap.gamemode(),
                            false, null, backAction
                    );
                    staff.openInventory(gui.getInventory());
                } else if (op != null && (op.hasPlayedBefore() || op.getName() != null)) {
                    MessageUtil.send(staff, "<yellow>Player <white>" + targetPlayerName + "</white> is offline and has no previous snapshots. Opening offline container.</yellow>");
                    InventoryInspectionGui gui = new InventoryInspectionGui(
                            plugin, targetPlayerName, finalTargetUuid1, "offline-initial", Instant.now(), Instant.now(), 1,
                            new ItemStack[36], new ItemStack[4], null, 0, 0, 20.0, 20, "SURVIVAL",
                            false, null, backAction
                    );
                    staff.openInventory(gui.getInventory());
                } else {
                    MessageUtil.send(staff, "<red>Player <white>" + targetPlayerName + "</white> has never joined this server.</red>");
                }
            });
        });
    }

    public void openEnderChestInspection(Player staff, String targetPlayerName, boolean preferLive, Runnable backAction) {
        Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
        if (preferLive && onlineTarget != null && onlineTarget.isOnline()) {
            EnderChestInspectionGui gui = new EnderChestInspectionGui(
                    plugin, onlineTarget.getName(), onlineTarget.getUniqueId(), "live", Instant.now(), Instant.now(), 1,
                    onlineTarget.getEnderChest().getContents(), true, onlineTarget, backAction
            );
            staff.openInventory(gui.getInventory());
            MessageUtil.send(staff, "<green>Opened live Ender Chest for <white>" + onlineTarget.getName() + "</white>.</green>");
            return;
        }

        UUID targetUuid = null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetPlayerName);
        if (op != null) {
            targetUuid = op.getUniqueId();
        }

        // 1. Check if there are pending offline modifications for this player's Ender Chest
        DatabaseManager.PendingOfflineModification pending = plugin.getDatabaseManager().getPendingOfflineModification(targetPlayerName);
        if (pending != null && pending.getEnderChestData() != null) {
            ItemStack[] echest = plugin.getInventorySerializer().deserializeItemArray(pending.getEnderChestData());
            UUID finalTargetUuid = targetUuid != null ? targetUuid : (pending.getPlayerUuid() != null ? UUID.fromString(pending.getPlayerUuid()) : UUID.randomUUID());
            EnderChestInspectionGui gui = new EnderChestInspectionGui(
                    plugin, targetPlayerName, finalTargetUuid, "pending-offline-ec", pending.getModifiedAt(), Instant.now(), 1,
                    echest, false, null, backAction
            );
            staff.openInventory(gui.getInventory());
            MessageUtil.send(staff, "<green>Opened offline Ender Chest for <white>" + targetPlayerName + "</white> (with pending modifications).</green>");
            return;
        }

        MessageUtil.send(staff, "<gray>Querying most recent Ender Chest snapshot for <white>" + targetPlayerName + "</white>...</gray>");
        UUID finalTargetUuid1 = targetUuid;
        plugin.getDatabaseManager().queryLatestInventorySnapshot(targetPlayerName).thenAccept(snap -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (snap != null) {
                    TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();
                    String intervalStr = formatter.formatIntervalWithDuration(snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds());
                    MessageUtil.send(staff, "<green>Found Ender Chest snapshot: <light_purple>" + snap.snapshotId() + "</light_purple> (" + intervalStr + ").</green>");

                    EnderChestInspectionGui gui = new EnderChestInspectionGui(
                            plugin, targetPlayerName, finalTargetUuid1, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                            snap.enderChest(), false, null, backAction
                    );
                    staff.openInventory(gui.getInventory());
                } else if (op != null && (op.hasPlayedBefore() || op.getName() != null)) {
                    MessageUtil.send(staff, "<yellow>Player <white>" + targetPlayerName + "</white> is offline and has no previous Ender Chest records. Opening offline container.</yellow>");
                    EnderChestInspectionGui gui = new EnderChestInspectionGui(
                            plugin, targetPlayerName, finalTargetUuid1, "offline-initial-ec", Instant.now(), Instant.now(), 1,
                            new ItemStack[27], false, null, backAction
                    );
                    staff.openInventory(gui.getInventory());
                } else {
                    MessageUtil.send(staff, "<red>Player <white>" + targetPlayerName + "</white> has never joined this server.</red>");
                }
            });
        });
    }

    public void executeCategoryAction(Player staff, GuiHolder.Subsystem subsystem, String targetPlayerName, Instant instant, String displayTime, Runnable backAction) {
        boolean isLatest = "Latest".equalsIgnoreCase(displayTime) || "live".equalsIgnoreCase(displayTime);

        switch (subsystem) {
            case INVENTORY -> {
                if (isLatest) {
                    openInventoryInspection(staff, targetPlayerName, true, backAction);
                } else {
                    MessageUtil.send(staff, "<gray>Querying snapshot for <white>" + targetPlayerName + "</white> before <yellow>" + displayTime + "</yellow>...</gray>");
                    plugin.getDatabaseManager().queryInventorySnapshotBefore(targetPlayerName, instant).thenAccept(snap -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (snap != null) {
                                InventoryInspectionGui gui = new InventoryInspectionGui(
                                        plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                        snap.inventory(), snap.armor(), snap.offhand(), snap.expLevel(), snap.totalExp(), snap.health(), snap.hunger(), snap.gamemode(),
                                        false, null, backAction
                                );
                                staff.openInventory(gui.getInventory());
                            } else {
                                MessageUtil.send(staff, "<red>No snapshot found before " + displayTime + " for " + targetPlayerName + ".</red>");
                            }
                        });
                    });
                }
            }
            case ENDER_CHEST -> {
                if (isLatest) {
                    openEnderChestInspection(staff, targetPlayerName, true, backAction);
                } else {
                    MessageUtil.send(staff, "<gray>Querying Ender Chest for <white>" + targetPlayerName + "</white> before <yellow>" + displayTime + "</yellow>...</gray>");
                    plugin.getDatabaseManager().queryInventorySnapshotBefore(targetPlayerName, instant).thenAccept(snap -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (snap != null) {
                                EnderChestInspectionGui gui = new EnderChestInspectionGui(
                                        plugin, targetPlayerName, snap.snapshotId(), snap.startTimestamp(), snap.endTimestamp(), snap.durationSeconds(),
                                        snap.enderChest(), false, null, backAction
                                );
                                staff.openInventory(gui.getInventory());
                            } else {
                                MessageUtil.send(staff, "<red>No Ender Chest snapshot found before " + displayTime + " for " + targetPlayerName + ".</red>");
                            }
                        });
                    });
                }
            }
            case POSITION -> openPositionHistory(staff, targetPlayerName, 0, backAction);
            case DEATHS -> openDeathHistory(staff, targetPlayerName, 0, backAction);
            case ITEM_TRANSACTIONS -> {
                MessageUtil.send(staff, "<gray>Querying item events for <white>" + targetPlayerName + "</white>...</gray>");
                plugin.getDatabaseManager().queryItemEvents(targetPlayerName, isLatest ? null : instant, 28, 0).thenAccept(list -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        List<LogsListGui.LogDisplayEntry> entries = new ArrayList<>();
                        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();
                        for (ItemEventRecord rec : list) {
                            Material mat = Material.matchMaterial(rec.material());
                            if (mat == null || mat.isAir()) mat = Material.DIAMOND;
                            List<String> details = List.of(
                                    "<gray>Event: <yellow>" + rec.eventType() + "</yellow></gray>",
                                    "<gray>Amount: <white>" + rec.amount() + "x " + rec.material() + "</white></gray>",
                                    "<gray>Location: <white>" + rec.worldName() + " [" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</white></gray>",
                                    "<gray>Date: <white>" + formatter.formatDate(rec.timestamp()) + "</white> | Time: <yellow>" + formatter.formatTime(rec.timestamp()) + "</yellow></gray>"
                            );
                            entries.add(new LogsListGui.LogDisplayEntry(mat, "<yellow>" + rec.eventType().toUpperCase() + "</yellow> <white>" + rec.amount() + "x " + rec.material() + "</white>", details));
                        }
                        new LogsListGui(plugin, subsystem, targetPlayerName, displayTime, entries, 0, backAction).open(staff);
                    });
                });
            }
            case BLOCK_ACTIONS -> {
                MessageUtil.send(staff, "<gray>Querying block actions for <white>" + targetPlayerName + "</white>...</gray>");
                plugin.getDatabaseManager().queryActionLogs(targetPlayerName, isLatest ? null : instant, 28, 0).thenAccept(list -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        List<LogsListGui.LogDisplayEntry> entries = new ArrayList<>();
                        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();
                        for (ActionLogRecord rec : list) {
                            Material mat = Material.matchMaterial(rec.targetInfo());
                            if (mat == null || mat.isAir()) mat = Material.BRICKS;
                            List<String> details = List.of(
                                    "<gray>Action: <yellow>" + rec.eventType() + "</yellow></gray>",
                                    "<gray>Block: <white>" + rec.targetInfo() + "</white></gray>",
                                    "<gray>Location: <white>" + rec.worldName() + " [" + (int) rec.x() + ", " + (int) rec.y() + ", " + (int) rec.z() + "]</white></gray>",
                                    "<gray>Date: <white>" + formatter.formatDate(rec.timestamp()) + "</white> | Time: <yellow>" + formatter.formatTime(rec.timestamp()) + "</yellow></gray>"
                            );
                            entries.add(new LogsListGui.LogDisplayEntry(mat, "<light_purple>" + rec.eventType() + "</light_purple> <white>" + rec.targetInfo() + "</white>", details));
                        }
                        new LogsListGui(plugin, subsystem, targetPlayerName, displayTime, entries, 0, backAction).open(staff);
                    });
                });
            }
            case SESSIONS -> {
                MessageUtil.send(staff, "<gray>Querying session history for <white>" + targetPlayerName + "</white>...</gray>");
                plugin.getDatabaseManager().querySessionRecords(targetPlayerName, 28, 0).thenAccept(list -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        List<LogsListGui.LogDisplayEntry> entries = new ArrayList<>();
                        TimeIntervalFormatter formatter = plugin.getTimeIntervalFormatter();
                        for (SessionRecord rec : list) {
                            List<String> details = List.of(
                                    "<gray>Joined: <white>" + formatter.formatDate(rec.joinedAt()) + " " + formatter.formatTime(rec.joinedAt()) + "</white></gray>",
                                    "<gray>Quit: <white>" + (rec.quitAt() != null ? formatter.formatTime(rec.quitAt()) : "Active/Crash") + "</white></gray>",
                                    "<gray>Duration: <yellow>" + formatter.formatDuration(rec.durationSeconds()) + "</yellow></gray>",
                                    "<gray>Platform: <aqua>" + rec.platform() + "</aqua></gray>"
                            );
                            entries.add(new LogsListGui.LogDisplayEntry(Material.CLOCK, "<gradient:#38bdf8:#0284c7><bold>SESSION</bold></gradient> <gray>•</gray> <yellow>" + formatter.formatDuration(rec.durationSeconds()) + "</yellow>", details));
                        }
                        new LogsListGui(plugin, subsystem, targetPlayerName, displayTime, entries, 0, backAction).open(staff);
                    });
                });
            }
        }
    }

    public void promptChatUsername(Player player, GuiHolder.Subsystem subsystem) {
        activePrompts.put(player.getUniqueId(), new ChatPrompt(player.getUniqueId(), PromptType.USERNAME, subsystem, null, System.currentTimeMillis()));
        MessageUtil.send(player, "<dark_gray>▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪</dark_gray>");
        MessageUtil.send(player, plugin.getLogsConfig().getPrefix() + "<gray>Type target username in chat (or type <red>cancel</red>):</gray>");
        MessageUtil.send(player, "<dark_gray>▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪</dark_gray>");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
    }

    public void promptChatTime(Player player, GuiHolder.Subsystem subsystem, String targetPlayerName) {
        activePrompts.put(player.getUniqueId(), new ChatPrompt(player.getUniqueId(), PromptType.TIME, subsystem, targetPlayerName, System.currentTimeMillis()));
        MessageUtil.send(player, "<dark_gray>▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪</dark_gray>");
        MessageUtil.send(player, plugin.getLogsConfig().getPrefix() + "<gray>Time filter for <white>" + targetPlayerName + "</white> (<aqua>" + subsystem.getDisplayName() + "</aqua>):</gray>");
        MessageUtil.send(player, "<gray>Type duration or date in chat (or <red>cancel</red>):</gray>");
        MessageUtil.send(player, "  <yellow>• Examples:</yellow> <white>5m, 10m, 1h, 12h, 1d, 15:30:00, 22/10/2026</white>");
        MessageUtil.send(player, "<dark_gray>▪ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ▪</dark_gray>");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatPrompt prompt = activePrompts.get(player.getUniqueId());
        if (prompt == null) return;

        if (System.currentTimeMillis() - prompt.createdMillis() > 90000L) {
            activePrompts.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (text.equalsIgnoreCase("cancel")) {
            activePrompts.remove(player.getUniqueId());
            MessageUtil.send(player, plugin.getLogsConfig().getPrefix() + "<yellow>Cancelled chat prompt.</yellow>");
            return;
        }

        if (prompt.type() == PromptType.USERNAME) {
            activePrompts.remove(player.getUniqueId());
            String sanitized = text.replaceAll("[^a-zA-Z0-9_]", "");
            if (sanitized.isBlank()) {
                MessageUtil.send(player, "<red>Invalid username format.</red>");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (prompt.subsystem() == GuiHolder.Subsystem.DEATHS) {
                    openDeathHistory(player, sanitized, 0, () -> openStage1(player));
                } else {
                    openStage3(player, prompt.subsystem(), sanitized);
                }
            });
            return;
        }

        if (prompt.type() == PromptType.TIME) {
            Instant parsed = timeParser.parse(text);
            if (parsed == null) {
                MessageUtil.send(player, "<red>Invalid time format: <yellow>" + text + "</yellow>! Use 10m, 1h, 15:30:00, or DD/MM/YYYY.</red>");
                return;
            }
            activePrompts.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> executeCategoryAction(player, prompt.subsystem(), prompt.targetPlayerName(), parsed, text, () -> openStage3(player, prompt.subsystem(), prompt.targetPlayerName())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) return;
        InventoryHolder holder = topInv.getHolder();

        if (holder instanceof UltimateLogsGui gui) {
            // Unconditionally cancel double-click item collection across all UltimateLogs GUIs
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }

            if (!gui.isInspectionGui()) {
                // Non-inspection GUIs (pure selection & archives): 100% CANCEL ALL CLICKS
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);

                // Clean any functional button on cursor or clicked in personal inventory
                if (ItemBuilder.isGuiButton(event.getCursor())) {
                    event.getView().setCursor(null);
                }
                if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                    if (ItemBuilder.isGuiButton(event.getCurrentItem())) {
                        event.setCurrentItem(null);
                    }
                }

                // Force player inventory sync on next tick to destroy any client-predicted ghost items
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);

                // Forward to GUI action processor
                gui.handleInventoryClick(event);
                return;
            }

            // Inspection GUIs (InventoryInspectionGui & EnderChestInspectionGui)
            // 1. Any functional GUI button is strictly protected from being taken, shifted, or swapped
            if (ItemBuilder.isGuiButton(event.getCurrentItem()) || ItemBuilder.isGuiButton(event.getCursor())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (ItemBuilder.isGuiButton(event.getCursor())) {
                    event.getView().setCursor(null);
                }
                if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                    if (ItemBuilder.isGuiButton(event.getCurrentItem())) {
                        event.setCurrentItem(null);
                    }
                }
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                gui.handleInventoryClick(event);
                return;
            }

            // 2. Block shift-clicking or moving items from personal inventory into the inspected container in snapshot mode
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    if (!gui.isLive()) {
                        event.setCancelled(true);
                        event.setResult(Event.Result.DENY);
                        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                        return;
                    }
                }
            }

            // 3. Forward to inspection GUI handler for inspected items
            gui.handleInventoryClick(event);
            return;
        }

        // Outside UltimateLogs GUIs: purge any leftover GUI buttons
        if (ItemBuilder.isGuiButton(event.getCursor())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            event.getView().setCursor(null);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            if (ItemBuilder.isGuiButton(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                event.setCurrentItem(null);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) return;
        InventoryHolder holder = topInv.getHolder();

        if (holder instanceof UltimateLogsGui gui) {
            if (!gui.isInspectionGui()) {
                // Non-inspection GUIs: drag is 100% blocked
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
            gui.handleInventoryDrag(event);
            return;
        }

        if (ItemBuilder.isGuiButton(event.getOldCursor())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiInventoryClose(InventoryCloseEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv != null && topInv.getHolder() instanceof UltimateLogsGui gui) {
            gui.handleInventoryClose(event);
        }
        if (event.getPlayer() instanceof Player player) {
            ItemBuilder.cleanPlayerInventory(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activePrompts.remove(event.getPlayer().getUniqueId());
        ItemBuilder.cleanPlayerInventory(event.getPlayer());
    }
}
