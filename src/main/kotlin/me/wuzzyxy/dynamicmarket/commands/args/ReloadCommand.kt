package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.DynamicMarket
import org.bukkit.command.CommandSender

class ReloadCommand(private val plugin: DynamicMarket) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        plugin.reload()
        return listOf("Reloaded config.yml and items.yml")
    }
}
