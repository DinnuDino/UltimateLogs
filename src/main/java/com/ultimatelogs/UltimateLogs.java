package com.ultimatelogs;

import com.ultimatelogs.commands.*;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.DatabaseManager;
import com.ultimatelogs.gui.GuiManager;
import com.ultimatelogs.listeners.*;
import com.ultimatelogs.serialization.InventorySerializer;
import com.ultimatelogs.utils.MessageUtil;
import com.ultimatelogs.utils.TimeIntervalFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class UltimateLogs extends JavaPlugin {

    private static UltimateLogs instance;

    private LogsConfig logsConfig;
    private InventorySerializer inventorySerializer;
    private TimeIntervalFormatter timeIntervalFormatter;
    private DatabaseManager databaseManager;
    private GuiManager guiManager;

    private BukkitTask positionTask;
    private BukkitTask inventoryTask;

    public static UltimateLogs getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();
        this.logsConfig = new LogsConfig();
        this.logsConfig.load(getConfig());

        // Initialize utilities
        this.inventorySerializer = new InventorySerializer();
        this.timeIntervalFormatter = new TimeIntervalFormatter(this.logsConfig);

        // Initialize Database
        this.databaseManager = new DatabaseManager(this, this.logsConfig, this.inventorySerializer);
        this.databaseManager.initialize();

        // Initialize GUI Manager
        this.guiManager = new GuiManager(this);

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new DeathListener(this, this.logsConfig, this.databaseManager), this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this, this.logsConfig, this.databaseManager), this);
        Bukkit.getPluginManager().registerEvents(new PositionTrackerListener(this, this.logsConfig, this.databaseManager), this);
        Bukkit.getPluginManager().registerEvents(new SessionListener(this, this.logsConfig, this.databaseManager), this);
        Bukkit.getPluginManager().registerEvents(new WorldActionListener(this, this.logsConfig, this.databaseManager), this);

        // Register Commands
        registerCommands();

        // Start recurring snapshot tasks
        startRecurringTasks();

        getLogger().info("=================================================");
        getLogger().info("UltimateLogs v" + getPluginMeta().getVersion() + " (Paper 26.2) enabled successfully!");
        getLogger().info("Enterprise logging, real-time intervals & death archive ready.");
        getLogger().info("=================================================");
    }

    private void registerCommands() {
        registerCmd("ultimatelogs", new UltimateLogsCommand(this));
        registerCmd("inv", new InvCommand(this));
        registerCmd("ec", new EnderChestCommand(this));
        registerCmd("pos", new PositionCommand(this));
        registerCmd("deaths", new DeathsCommand(this));
    }

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                cmd.setTabCompleter(completer);
            }
        } else {
            getLogger().warning("Could not register command /" + name + " (missing in plugin.yml)");
        }
    }

    private void startRecurringTasks() {
        long posTicks = (long) logsConfig.getPositionSnapshotIntervalSeconds() * 20L;
        this.positionTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                databaseManager.recordPositionSnapshot(player, "interval");
            }
        }, posTicks, posTicks);

        long invTicks = (long) logsConfig.getInventorySnapshotIntervalSeconds() * 20L;
        this.inventoryTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                databaseManager.recordInventorySnapshot(player, "interval", null);
            }
        }, invTicks, invTicks);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.logsConfig.load(getConfig());
        this.timeIntervalFormatter = new TimeIntervalFormatter(this.logsConfig);
    }

    @Override
    public void onDisable() {
        if (positionTask != null) positionTask.cancel();
        if (inventoryTask != null) inventoryTask.cancel();

        if (databaseManager != null) {
            getLogger().info("Flushing queued audit records to database before shutdown...");
            databaseManager.shutdown();
        }

        getLogger().info("UltimateLogs disabled safely.");
    }

    public LogsConfig getLogsConfig() { return logsConfig; }
    public InventorySerializer getInventorySerializer() { return inventorySerializer; }
    public TimeIntervalFormatter getTimeIntervalFormatter() { return timeIntervalFormatter; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public GuiManager getGuiManager() { return guiManager; }
}
