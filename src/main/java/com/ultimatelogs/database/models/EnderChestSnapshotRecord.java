package com.ultimatelogs.database.models;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public record EnderChestSnapshotRecord(
        String snapshotId,
        String playerUuid,
        String username,
        String worldName,
        double x,
        double y,
        double z,
        String snapshotReason,
        ItemStack[] contents,
        int durationSeconds,
        Instant startTimestamp,
        Instant endTimestamp
) {
    public int getItemCount() {
        int count = 0;
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item != null && !item.getType().isAir()) count++;
            }
        }
        return count;
    }
}
