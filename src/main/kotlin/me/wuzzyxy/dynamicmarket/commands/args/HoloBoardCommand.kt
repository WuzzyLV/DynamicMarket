package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.board.HoloBoardManager
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/***
 * /dmarket holo <place|remove|move|list>. Its own verbs are parsed from args[1] onward the
 * way set/debug already do — HoloBoardManager does the real work, this just adapts a
 * CommandSender's location/facing into its Location/yaw-based API.
 */
class HoloBoardCommand(plugin: DynamicMarket) : ArgsCommand {

    private val manager: HoloBoardManager = plugin.holoBoardManager

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> =
        when (args.getOrNull(1)?.lowercase()) {
            "place" -> place(args, sender)
            "remove" -> remove(args)
            "move" -> move(args, sender)
            "list" -> list()
            else -> listOf(
                "/dmarket holo place <name> <screen>",
                "/dmarket holo remove <name>",
                "/dmarket holo move <name>",
                "/dmarket holo list",
            )
        }

    private fun place(args: Array<out String>, sender: CommandSender): List<String> {
        if (sender !is Player) return listOf("Only a player can place a board")
        if (args.size < 4) return listOf("Usage: /dmarket holo place <name> <screen>")

        val name = args[2]
        val screen = args[3]
        val error = manager.placeBoard(name, screen, sender.location, sender.location.yaw)
        return listOf(error ?: "Placed board '$name' using screen '$screen'")
    }

    private fun remove(args: Array<out String>): List<String> {
        if (args.size < 3) return listOf("Usage: /dmarket holo remove <name>")

        val name = args[2]
        return if (manager.removeBoard(name)) listOf("Removed board '$name'") else listOf("No board named '$name'")
    }

    private fun move(args: Array<out String>, sender: CommandSender): List<String> {
        if (sender !is Player) return listOf("Only a player can move a board")
        if (args.size < 3) return listOf("Usage: /dmarket holo move <name>")

        val name = args[2]
        val moved = manager.moveBoard(name, sender.location, sender.location.yaw)
        return if (moved) listOf("Moved board '$name' to your location") else listOf("No board named '$name'")
    }

    private fun list(): List<String> {
        val boards = manager.listBoards()
        if (boards.isEmpty()) {
            return listOf("No boards placed. Screens available: ${manager.screenNames.joinToString()}")
        }
        return boards.map { (name, screen) -> "$name -> $screen" }
    }
}
