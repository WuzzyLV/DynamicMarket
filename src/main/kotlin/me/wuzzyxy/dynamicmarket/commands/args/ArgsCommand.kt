package me.wuzzyxy.dynamicmarket.commands.args

import org.bukkit.command.CommandSender

interface ArgsCommand {
    /***
     * Lines to send back, or null when the command has already messaged the sender itself.
     */
    fun execute(args: Array<out String>, sender: CommandSender): List<String>?
}
