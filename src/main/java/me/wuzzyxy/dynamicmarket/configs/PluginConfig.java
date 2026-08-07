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

        REPORT_REFRESH_SECONDS = plugin.getConfig().getInt("report.refresh_seconds", 60);
        REPORT_WINDOW_HOURS = plugin.getConfig().getInt("report.window_hours", 24);
        REPORT_MIN_CHANGE = plugin.getConfig().getDouble("report.min_change_percent", 0.1);
        REPORT_MOVER_UP = plugin.getConfig().getString("report.mover_up", "<green>▲ <white><item> <green>+<change>%");
        REPORT_MOVER_DOWN = plugin.getConfig().getString("report.mover_down", "<red>▼ <white><item> <red><change>%");
        REPORT_TREND_UP = plugin.getConfig().getString("report.trend_up", "<green>▲");
        REPORT_TREND_DOWN = plugin.getConfig().getString("report.trend_down", "<red>▼");
        REPORT_TREND_FLAT = plugin.getConfig().getString("report.trend_flat", "<gray>-");
        REPORT_EMPTY = plugin.getConfig().getString("report.empty", "");
    }

    public final int PUSH_INTERVAL;
    public final double SELL_MULTIPLIER;
    public final int HISTORY_SNAPSHOT_MINUTES;
    public final int HISTORY_RETENTION_DAYS;
    public final int REPORT_REFRESH_SECONDS;
    public final int REPORT_WINDOW_HOURS;
    public final double REPORT_MIN_CHANGE;
    public final String REPORT_MOVER_UP;
    public final String REPORT_MOVER_DOWN;
    public final String REPORT_TREND_UP;
    public final String REPORT_TREND_DOWN;
    public final String REPORT_TREND_FLAT;
    public final String REPORT_EMPTY;

    public ReportSettings reportSettings() {
        return new ReportSettings(REPORT_WINDOW_HOURS, REPORT_MIN_CHANGE, REPORT_MOVER_UP, REPORT_MOVER_DOWN,
                REPORT_TREND_UP, REPORT_TREND_DOWN, REPORT_TREND_FLAT, REPORT_EMPTY);
    }
    public final String HOST;
    public final int PORT;
    public final String DATABASE;
    public final String USERNAME;
    public final String PASSWORD;
    public final int CONNECT_TIMEOUT_MS;
    public final int SOCKET_TIMEOUT_MS;



}
