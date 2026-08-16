package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.market.MarketManager
import me.wuzzyxy.dynamicmarket.market.PriceHandler
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

class DebugCommand(
    private val manager: MarketManager,
    private val priceHandler: PriceHandler,
) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String>? {
        if (args.size < 2) return listOf("Not enough arguments")

        val itemName = args[1]
        val persisted = manager.getPersistedItem(itemName)
        val working = manager.getWorkingItem(itemName)

        if (persisted == null || working == null) return listOf("Item not found")

        sender.sendMessage("Item: ${persisted.name}")
        describe(sender, "Working", working)
        describe(sender, "Persisted", persisted)
        return null
    }

    private fun describe(sender: CommandSender, label: String, item: MarketItem) {
        sender.sendMessage(
            "$label Bought/Sold " +
                "${ChatColor.GREEN}${item.boughtAmount}" +
                "${ChatColor.WHITE} / " +
                "${ChatColor.RED}${item.soldAmount}"
        )
        sender.sendMessage(
            ChatColor.translateAlternateColorCodes(
                '&',
                "&7Net: &a" + String.format("%.1f", item.peekNet()) +
                    "&7  Unit: &a" + String.format("%.4f", priceHandler.getUnitPrice(item)) +
                    "&7  Stack: &a" + String.format("%.2f", priceHandler.getBuyPrice(item, 64))
            )
        )
        sender.sendMessage("k: ${item.k}  half-life: ${item.halfLifeHours}h")
    }
}
