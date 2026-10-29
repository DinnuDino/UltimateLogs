package com.ultimatelogs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class GuiHolder implements InventoryHolder {

    public enum GuiType {
        CATEGORY_SELECT,
        PLAYER_SELECT,
        TIME_SELECT,
        SNAPSHOT_HISTORY,
        INVENTORY_INSPECTION,
        ENDER_CHEST_INSPECTION,
        POSITION_HISTORY,
        DEATH_HISTORY,
        LOGS_LIST
    }

    public enum Subsystem {
        INVENTORY("Inventory", "🎒"),
        ENDER_CHEST("Ender Chest", "📦"),
        POSITION("Position & Coordinates", "📍"),
        DEATHS("Death History", "💀"),
        ITEM_TRANSACTIONS("Item Events", "💎"),
        BLOCK_ACTIONS("Block Actions", "🧱"),
        SESSIONS("Sessions & Logins", "⏱");

        private final String displayName;
        private final String emoji;

        Subsystem(String displayName, String emoji) {
            this.displayName = displayName;
            this.emoji = emoji;
        }

        public String getDisplayName() { return displayName; }
        public String getEmoji() { return emoji; }
    }

    private final GuiType guiType;
    private final Subsystem subsystem;
    private final String targetPlayerName;
    private final int page;
    private final Runnable backAction;
    private Inventory inventory;

    public GuiHolder(GuiType guiType, Subsystem subsystem, String targetPlayerName, int page, Runnable backAction) {
        this.guiType = guiType;
        this.subsystem = subsystem;
        this.targetPlayerName = targetPlayerName;
        this.page = page;
        this.backAction = backAction;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public GuiType getGuiType() { return guiType; }
    public Subsystem getSubsystem() { return subsystem; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public int getPage() { return page; }
    public Runnable getBackAction() { return backAction; }
}
