package com.ultimatelogs.database.models;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public record InventorySnapshotRecord(
        String snapshotId,
        String playerUuid,
        String username,
        String worldName,
        double x,
        double y,
        double z,
        String gamemode,
        double health,
        int hunger,
        float saturation,
        int expLevel,
        int totalExp,
        int selectedSlot,
        String snapshotReason,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offhand,
        ItemStack[] enderChest,
        ItemStack cursor,
        String potionEffects,
        String correlationId,
        int durationSeconds,
        Instant startTimestamp,
        Instant endTimestamp
) {
    public int getItemCount() {
        int count = 0;
        if (inventory != null) {
            for (ItemStack item : inventory) {
                if (item != null && !item.getType().isAir()) count++;
            }
        }
        if (armor != null) {
            for (ItemStack item : armor) {
                if (item != null && !item.getType().isAir()) count++;
            }
        }
        if (offhand != null && !offhand.getType().isAir()) count++;
        return count;
    }
}
