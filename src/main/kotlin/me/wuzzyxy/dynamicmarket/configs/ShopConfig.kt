package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket

/***
 * The /market GUI's own knobs, kept apart from PluginConfig the same way ReportSettings is —
 * so nothing in gui/ needs a live Bukkit config to be testable.
 */
class ShopConfig(plugin: DynamicMarket) {

    private val config = plugin.config

    val QUANTITIES: List<Int> = config.getIntegerList("shop.quantities")
        .takeIf { it.isNotEmpty() }
        ?: listOf(1, 6, 12, 32, 64)

    val MOVERS_SHOWN: Int = config.getInt("shop.movers_shown", 8).coerceIn(1, 8)

    val SOUNDS_ENABLED: Boolean = config.getBoolean("shop.sounds", true)
}
