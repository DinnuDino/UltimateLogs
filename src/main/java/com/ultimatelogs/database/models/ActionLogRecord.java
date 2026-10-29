package com.ultimatelogs.database.models;

import java.time.Instant;

public record ActionLogRecord(
        long id,
        String eventType,
        String playerUuid,
        String username,
        String worldName,
        double x,
        double y,
        double z,
        String targetInfo,
        String details,
        Instant timestamp
) {}
