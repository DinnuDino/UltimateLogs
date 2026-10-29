package com.ultimatelogs.database.models;

import java.time.Instant;

public record SessionRecord(
        long id,
        String sessionId,
        String playerUuid,
        String username,
        String platform,
        String ipAddress,
        Instant joinedAt,
        Instant quitAt,
        long durationSeconds,
        String quitReason
) {}
