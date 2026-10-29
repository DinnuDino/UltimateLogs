package com.ultimatelogs.database.models;

import java.time.Instant;

public record PositionSnapshotRecord(
        long id,
        String playerUuid,
        String username,
        String worldUuid,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String gamemode,
        double health,
        int foodLevel,
        int expLevel,
        String snapshotReason,
        int durationSeconds,
        Instant startTimestamp,
        Instant endTimestamp
) {}
