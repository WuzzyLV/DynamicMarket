package me.wuzzyxy.dynamicmarket.commands

import me.wuzzyxy.dynamicmarket.market.MarketManager
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.util.StringUtil

class CommandCompleter(
    private val marketManager: MarketManager,
    private val commands: List<String>,
) : TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> {
        val matches = mutableListOf<String>()
        if (args.size == 1) {
            StringUtil.copyPartialMatches(args[0], commands, matches)
            return matches
        }

        if (args[0] in TAKE_AN_ITEM) {
            // Read live: items.yml can add a market after this completer was built.
            if (args.size == 2) {
                StringUtil.copyPartialMatches(args[1], marketManager.persistedItems.map { it.name }, matches)
            } else if (args.size == 3) {
                matches += "1"
            }
        }

        if (args[0] == "event") {
            if (args.size == 2) {
                StringUtil.copyPartialMatches(args[1], EVENT_ACTIONS, matches)
            } else if (args.size == 3 && args[1].equals("trigger", ignoreCase = true)) {
                // Read live: events.yml can add/remove definitions after this completer was built.
                StringUtil.copyPartialMatches(args[2], marketManager.eventManager.definitions.map { it.id }, matches)
            }
        }
        return matches
    }

    private companion object {
        val TAKE_AN_ITEM = setOf("buy", "sell", "debug", "set")
        val EVENT_ACTIONS = listOf("list", "trigger", "last")
    }
}
