package me.wuzzyxy.dynamicmarket.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.OfflinePlayer

/***
 * The report placeholders differ only in which method they call, so they share one class
 * instead of a file each. Buy/SellPriceExpansion predate this and are left alone.
 */
class MarketExpansion(
    private val plugin: DynamicMarket,
    private val identifier: String,
    private val resolver: (Array<String>) -> String,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = identifier

    override fun getAuthor(): String = "Wuzzy"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String = resolver(params.splitParams())
}
