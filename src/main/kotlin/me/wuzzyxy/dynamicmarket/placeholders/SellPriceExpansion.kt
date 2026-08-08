package me.wuzzyxy.dynamicmarket.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.market.MarketManager
import me.wuzzyxy.dynamicmarket.market.PriceHandler
import org.bukkit.OfflinePlayer
import java.text.DecimalFormat

class SellPriceExpansion(
    private val plugin: DynamicMarket,
    private val manager: MarketManager,
    private val priceHandler: PriceHandler,
) : PlaceholderExpansion() {

    private val df = DecimalFormat("0.00")

    override fun getIdentifier(): String = "DMSellPrice"

    override fun getAuthor(): String = "Wuzzy"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String {
        val args = params.splitParams()

        if (args.size != 2) return "DMSellPrice_Item,Amount${args.size} args provided"

        val item = manager.getWorkingItem(args[0]) ?: return "${args[0]} not found"
        val amount = args[1].toIntOrNull() ?: return "Invalid params"

        return df.format(priceHandler.getSellPrice(item, amount))
    }
}
