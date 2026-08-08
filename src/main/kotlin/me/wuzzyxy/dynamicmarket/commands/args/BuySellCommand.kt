package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.market.MarketManager
import org.bukkit.command.CommandSender
import java.text.DecimalFormat

class BuySellCommand(private val manager: MarketManager) : ArgsCommand {

    private val df = DecimalFormat("0.00")

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        if (args.size < 3) return listOf("Not enough arguments")

        val item = manager.getWorkingItem(args[1]) ?: return listOf("Item not found")

        val amount = args[2].toIntOrNull() ?: return listOf("Amount must be a number")
        if (amount <= 0) return listOf("Amount must be greater than 0")

        return when {
            args[0].equals("buy", ignoreCase = true) -> listOf(df.format(buy(item, amount)))
            args[0].equals("sell", ignoreCase = true) -> listOf(df.format(sell(item, amount)))
            else -> listOf("Invalid command")
        }
    }

    /***
     * Answers with the price that was actually charged, bare so a shop can parse it.
     * Reading a placeholder first and reporting the fill afterwards prices the trade
     * against one snapshot and books it against another, and the gap between the two
     * is walkable.
     */
    private fun buy(item: MarketItem, amount: Int): Double {
        val price = manager.priceHandler.getBuyPrice(item, amount)
        item.recordBuy(amount)
        return price
    }

    private fun sell(item: MarketItem, amount: Int): Double {
        val price = manager.priceHandler.getSellPrice(item, amount)
        item.recordSell(amount)
        return price
    }
}
