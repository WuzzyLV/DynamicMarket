package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.market.MarketManager
import org.bukkit.command.CommandSender

class SetCommand(private val manager: MarketManager) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        if (args.size < 4) return listOf("Not enough arguments")

        val itemName = args[1]
        if (manager.getPersistedItem(itemName) == null) return listOf("Item not found")

        val boughtAmount = args[2].toLongOrNull()
        val soldAmount = args[3].toLongOrNull()
        if (boughtAmount == null || soldAmount == null) return listOf("Amounts must be numbers")
        if (boughtAmount < 0 || soldAmount < 0) return listOf("Amount must be greater than or equal to 0")

        manager.getWorkingItem(itemName)?.setCounters(boughtAmount, soldAmount)
        return listOf("Set item $itemName to $boughtAmount bought and $soldAmount sold")
    }
}
