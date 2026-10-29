package com.ultimatelogs.database.models;

import java.time.Instant;

public record ItemEventRecord(
        long id,
        String eventType,
        String actorUuid,
        String actorName,
        String affectedPlayerUuid,
        String affectedPlayerName,
        String material,
        int amount,
        String itemDisplayName,
        String itemSerialized,
        String worldName,
        double x,
        double y,
        double z,
        String correlationId,
        String details,
        Instant timestamp
) {}
