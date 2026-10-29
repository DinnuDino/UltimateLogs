package com.ultimatelogs.listeners;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionListener implements Listener {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final DatabaseManager db;

    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionIds = new ConcurrentHashMap<>();

    public SessionListener(UltimateLogs plugin, LogsConfig config, DatabaseManager db) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        com.ultimatelogs.utils.ItemBuilder.cleanPlayerInventory(player);
        db.applyPendingOfflineModifications(player);
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();
        joinTimes.put(uuid, now);
        sessionIds.put(uuid, sessionId);

        String ip = null;
        if (config.isTrackIpAddresses()) {
            InetSocketAddress addr = player.getAddress();
            if (addr != null && addr.getAddress() != null) {
                ip = addr.getAddress().getHostAddress();
            }
        }

        String finalIp = ip;
        db.queueWrite(() -> {
            String sql = "INSERT INTO player_sessions (session_id, player_uuid, username, platform, ip_address, joined_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sessionId);
                ps.setString(2, uuid.toString());
                ps.setString(3, player.getName());
                ps.setString(4, "JAVA");
                ps.setString(5, finalIp);
                ps.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(now)));
                ps.executeUpdate();
            } catch (Exception ignored) {}
        });

        db.recordPositionSnapshot(player, "join");
        db.recordInventorySnapshot(player, "join", sessionId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Long joinedAt = joinTimes.remove(uuid);
        String sessionId = sessionIds.remove(uuid);
        long now = System.currentTimeMillis();
        long duration = joinedAt != null ? (now - joinedAt) / 1000L : 0L;

        if (sessionId != null) {
            db.queueWrite(() -> {
                String sql = "UPDATE player_sessions SET quit_at = ?, duration_seconds = ?, quit_reason = ? WHERE session_id = ?";
                try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(now)));
                    ps.setLong(2, duration);
                    ps.setString(3, "DISCONNECT");
                    ps.setString(4, sessionId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            });
        }

        db.recordPositionSnapshot(player, "quit");
        db.recordInventorySnapshot(player, "quit", sessionId);
        db.clearPlayerTracking(uuid);
    }
}
