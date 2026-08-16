package me.wuzzyxy.dynamicmarket.configs

import me.wuzzyxy.dynamicmarket.DynamicMarket

/***
 * Vanilla's max_stack_size component is capped at 99, so that's the largest count an icon can be
 * *made* to show. Bigger tiers still trade fine — the number on the icon is just whatever the
 * client decides to draw for an over-max count, so the amount belongs in the name/lore too.
 */
const val MAX_ICON_COUNT = 99

/***
 * The /market GUI's own knobs, kept apart from PluginConfig the same way ReportSettings is —
 * so nothing in gui/ needs a live Bukkit config to be testable.
 */
class ShopConfig(plugin: DynamicMarket) {

    private val config = plugin.config

    /***
     * Quantity tiers, in the order they fill the item screen's 'q' slots. How *many* get shown is
     * the layout's business, not this list's — see menus.yml.
     */
    val QUANTITIES: List<Int> = run {
        val configured = config.getIntegerList("shop.quantities")
        val tiers = configured.filter { it > 0 }
        if (tiers.size < configured.size) {
            plugin.logger.warning("shop.quantities has ${configured.size - tiers.size} entry/entries at zero or below — dropping them")
        }
        val oversized = tiers.filter { it > MAX_ICON_COUNT }
        if (oversized.isNotEmpty()) {
            plugin.logger.info(
                "shop.quantities tier(s) ${oversized.joinToString()} are above $MAX_ICON_COUNT, which is as high as an " +
                    "item's stack count can be set — they trade fine, but keep %amount% in the tier's name so the " +
                    "size is readable either way"
            )
        }
        tiers.ifEmpty { listOf(1, 6, 12, 32, 64) }
    }

    val SOUNDS_ENABLED: Boolean = config.getBoolean("shop.sounds", true)
}
