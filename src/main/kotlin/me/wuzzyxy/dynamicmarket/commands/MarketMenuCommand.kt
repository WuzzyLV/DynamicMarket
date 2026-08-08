package me.wuzzyxy.dynamicmarket.commands

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/*** /market [category] — the player-facing GUI entry point. /dmarket stays the admin/data command. */
class MarketMenuCommand(private val plugin: DynamicMarket) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can open the market.")
            return true
        }

        val categoryArg = args.firstOrNull()
        if (categoryArg == null) {
            plugin.guiManager.openMainMenu(player)
            return true
        }

        val category = categories().firstOrNull { it.equals(categoryArg, ignoreCase = true) }
        if (category == null) {
            player.sendMessage("Unknown category '$categoryArg'.")
            return true
        }

        plugin.guiManager.openCategory(player, category)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size != 1) return emptyList()
        return categories().filter { it.startsWith(args[0], ignoreCase = true) }
    }

    private fun categories(): List<String> =
        plugin.marketManager.workingItems.map(MarketItem::category).distinct().sorted()
}
