package com.ultimatelogs.database.models;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public record DeathRecord(
        String deathId,
        String playerUuid,
        String username,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String deathMessage,
        String deathCause,
        String killerName,
        String killerType,
        int expLevel,
        int totalExp,
        int expDropped,
        String inventorySnapshotId,
        ItemStack[] inventoryItems,
        ItemStack[] armorItems,
        ItemStack offhandItem,
        ItemStack[] enderChestItems,
        int itemCount,
        int dropCount,
        Instant timestamp
) {}
