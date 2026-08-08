package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.market.MarketDatabaseHandler
import org.bukkit.command.CommandSender

class ForcePushCommand(private val handler: MarketDatabaseHandler) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        handler.pushItems()
        return listOf("Pushed items to database")
    }
}
