package me.wuzzyxy.dynamicmarket.configs;

import me.wuzzyxy.dynamicmarket.DynamicMarket;

public class PluginConfig {
    public PluginConfig( DynamicMarket plugin) {
        HOST= plugin.getConfig().getString("mysql.host");
        PORT= plugin.getConfig().getInt("mysql.port");
        DATABASE= plugin.getConfig().getString("mysql.database");
        USERNAME= plugin.getConfig().getString("mysql.username");
        PASSWORD= plugin.getConfig().getString("mysql.password");
        CONNECT_TIMEOUT_MS = plugin.getConfig().getInt("mysql.connect_timeout_ms", 5000);
        SOCKET_TIMEOUT_MS = plugin.getConfig().getInt("mysql.socket_timeout_ms", 15000);

        PUSH_INTERVAL = plugin.getConfig().getInt("push_interval");
        SELL_MULTIPLIER = plugin.getConfig().getDouble("sell_multiplier");

        HISTORY_SNAPSHOT_MINUTES = plugin.getConfig().getInt("history.snapshot_minutes", 30);
        HISTORY_RETENTION_DAYS = plugin.getConfig().getInt("history.retention_days", 90);
    }

    public final int PUSH_INTERVAL;
    public final double SELL_MULTIPLIER;
    public final int HISTORY_SNAPSHOT_MINUTES;
    public final int HISTORY_RETENTION_DAYS;
    public final String HOST;
    public final int PORT;
    public final String DATABASE;
    public final String USERNAME;
    public final String PASSWORD;
    public final int CONNECT_TIMEOUT_MS;
    public final int SOCKET_TIMEOUT_MS;



}
