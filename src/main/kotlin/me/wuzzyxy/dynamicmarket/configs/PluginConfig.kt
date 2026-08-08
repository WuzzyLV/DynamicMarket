package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket

class PluginConfig(plugin: DynamicMarket) {

    private val config = plugin.config

    val HOST: String? = config.getString("mysql.host")
    val PORT: Int = config.getInt("mysql.port")
    val DATABASE: String? = config.getString("mysql.database")
    val USERNAME: String? = config.getString("mysql.username")
    val PASSWORD: String? = config.getString("mysql.password")
    val CONNECT_TIMEOUT_MS: Int = config.getInt("mysql.connect_timeout_ms", 5000)
    val SOCKET_TIMEOUT_MS: Int = config.getInt("mysql.socket_timeout_ms", 15000)

    val PUSH_INTERVAL: Int = config.getInt("push_interval")
    val SELL_MULTIPLIER: Double = config.getDouble("sell_multiplier")

    val HISTORY_SNAPSHOT_MINUTES: Int = config.getInt("history.snapshot_minutes", 30)
    val HISTORY_RETENTION_DAYS: Int = config.getInt("history.retention_days", 90)

    val REPORT_REFRESH_SECONDS: Int = config.getInt("report.refresh_seconds", 60)
    val REPORT_WINDOW_HOURS: Int = config.getInt("report.window_hours", 24)
    val REPORT_MIN_CHANGE: Double = config.getDouble("report.min_change_percent", 0.1)
    val REPORT_MOVER_UP: String = string("report.mover_up", "<green>▲ <white><item> <green>+<change>%")
    val REPORT_MOVER_DOWN: String = string("report.mover_down", "<red>▼ <white><item> <red><change>%")
    val REPORT_TREND_UP: String = string("report.trend_up", "<green>▲")
    val REPORT_TREND_DOWN: String = string("report.trend_down", "<red>▼")
    val REPORT_TREND_FLAT: String = string("report.trend_flat", "<gray>-")
    val REPORT_EMPTY: String = string("report.empty", "")

    fun reportSettings(): ReportSettings = ReportSettings(
        REPORT_WINDOW_HOURS, REPORT_MIN_CHANGE, REPORT_MOVER_UP, REPORT_MOVER_DOWN,
        REPORT_TREND_UP, REPORT_TREND_DOWN, REPORT_TREND_FLAT, REPORT_EMPTY,
    )

    private fun string(path: String, def: String): String = config.getString(path, def) ?: def
}
