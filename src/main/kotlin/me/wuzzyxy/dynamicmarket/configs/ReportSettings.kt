package me.wuzzyxy.dynamicmarket.configs

/***
 * Just the report knobs, so MarketReport does not need a whole PluginConfig — which can
 * only be built from a live Bukkit config and would drag the server into every test.
 */
data class ReportSettings(
    val windowHours: Int,
    val minChange: Double,
    val moverUp: String,
    val moverDown: String,
    val trendUp: String,
    val trendDown: String,
    val trendFlat: String,
    val empty: String,
)
