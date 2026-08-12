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
    val REPORT_CATEGORY_SUMMARY: String = string(
        "report.category_summary",
        "<white><category> <gray>avg <avg_change><white>% <green><up_count>↑ <red><down_count>↓",
    )
    val REPORT_SENTIMENT_BULLISH: String = string(
        "report.sentiment_bullish",
        "<green>Bullish <gray>- <white><up_percent>% of movers rising <gray>(<tracked_count> tracked)",
    )
    val REPORT_SENTIMENT_BEARISH: String = string(
        "report.sentiment_bearish",
        "<red>Bearish <gray>- <white><down_percent>% of movers falling <gray>(<tracked_count> tracked)",
    )
    val REPORT_SENTIMENT_MIXED: String = string(
        "report.sentiment_mixed",
        "<yellow>Mixed <gray>- <white><up_count> up <gray>/ <white><down_count> down",
    )
    val REPORT_SENTIMENT_THRESHOLD: Double = config.getDouble("report.sentiment_threshold_percent", 60.0)
    val REPORT_VOLUME_LEADER: String = string(
        "report.volume_leader",
        "<white><item> <gray>- <white><units> <gray>traded",
    )

    fun reportSettings(): ReportSettings = ReportSettings(
        REPORT_WINDOW_HOURS, REPORT_MIN_CHANGE, REPORT_MOVER_UP, REPORT_MOVER_DOWN,
        REPORT_TREND_UP, REPORT_TREND_DOWN, REPORT_TREND_FLAT, REPORT_EMPTY,
        REPORT_CATEGORY_SUMMARY, REPORT_SENTIMENT_BULLISH, REPORT_SENTIMENT_BEARISH, REPORT_SENTIMENT_MIXED,
        REPORT_SENTIMENT_THRESHOLD, REPORT_VOLUME_LEADER,
    )

    private fun string(path: String, def: String): String = config.getString(path, def) ?: def
}
