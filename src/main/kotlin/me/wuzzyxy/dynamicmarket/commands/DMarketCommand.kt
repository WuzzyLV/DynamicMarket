package me.wuzzyxy.dynamicmarket.commands

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.commands.args.ArgsCommand
import me.wuzzyxy.dynamicmarket.commands.args.BuySellCommand
import me.wuzzyxy.dynamicmarket.commands.args.DebugCommand
import me.wuzzyxy.dynamicmarket.commands.args.ForcePushCommand
import me.wuzzyxy.dynamicmarket.commands.args.HoloBoardCommand
import me.wuzzyxy.dynamicmarket.commands.args.HomeCommand
import me.wuzzyxy.dynamicmarket.commands.args.ReloadCommand
import me.wuzzyxy.dynamicmarket.commands.args.ReportCommand
import me.wuzzyxy.dynamicmarket.commands.args.SetCommand
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DMarketCommand(plugin: DynamicMarket) : CommandExecutor {

    private val subCommands: Map<String, ArgsCommand> = mapOf(
        "" to HomeCommand(plugin),
        "buy" to BuySellCommand(plugin.marketManager),
        "sell" to BuySellCommand(plugin.marketManager),
        "set" to SetCommand(plugin.marketManager),
        "debug" to DebugCommand(plugin.marketManager, plugin.marketManager.priceHandler),
        "push" to ForcePushCommand(plugin.marketManager.databaseHandler),
        "report" to ReportCommand(plugin.marketManager),
        "holo" to HoloBoardCommand(plugin),
        "reload" to ReloadCommand(plugin),
    )

    init {
        plugin.getCommand("dmarket")?.tabCompleter =
            CommandCompleter(plugin.marketManager, subCommands.keys.toList())
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val subCommand = subCommands[args.firstOrNull() ?: ""]
        if (subCommand == null) {
            sender.sendMessage("Unknown subcommand")
            return false
        }

        subCommand.execute(args, sender)?.forEach { sender.sendMessage(it) }
        return true
    }
}
