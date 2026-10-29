package com.ultimatelogs.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Universal interface for all UltimateLogs GUIs.
 * Centralizes lifecycle, event handling, and security enforcement
 * through GuiManager, eliminating per-instance event registration vulnerabilities.
 */
public interface UltimateLogsGui extends InventoryHolder {

    /**
     * Dispatches an inventory click event to the GUI implementation.
     * For non-inspection GUIs, the event is guaranteed to be already cancelled
     * by GuiManager before this method is called.
     *
     * @param event The click event
     */
    void handleInventoryClick(InventoryClickEvent event);

    /**
     * Handles inventory drag events.
     *
     * @param event The drag event
     */
    default void handleInventoryDrag(InventoryDragEvent event) {}

    /**
     * Handles inventory close events.
     *
     * @param event The close event
     */
    default void handleInventoryClose(InventoryCloseEvent event) {}

    /**
     * Whether this GUI is an active inspection GUI (Inventory or Ender Chest)
     * where legitimate player items can be taken by authorized staff.
     *
     * @return true for inspection GUIs, false for selector/menu/archive GUIs
     */
    boolean isInspectionGui();

    /**
     * Whether this GUI is currently inspecting an active, online player in real time.
     * In live mode, authorized staff can place items and take items with live sync.
     * In historical snapshot / previous logs mode, placing items is strictly blocked.
     *
     * @return true if live inspection, false if snapshot / previous logs
     */
    default boolean isLive() {
        return false;
    }
}

