package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.command.CommandSender

class HomeCommand(private val plugin: DynamicMarket) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String>? {
        sender.sendMessage("DynamicMarket ${plugin.description.version}")

        val lastPush = plugin.marketManager.databaseHandler.lastPushTime
        if (lastPush == null) {
            sender.sendMessage("Push to database in: waiting on the first push")
            return null
        }

        // Read live: a reload can change the interval under us.
        val pushInterval = plugin.pluginConfig.PUSH_INTERVAL
        val nextPushTime = pushInterval * 1000L - (System.currentTimeMillis() - lastPush)
        sender.sendMessage("Push to database in: ${nextPushTime / 1000.0} s")
        return null
    }
}
