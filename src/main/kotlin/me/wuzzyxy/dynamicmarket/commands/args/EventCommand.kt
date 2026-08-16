package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.MarketEventManager
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import java.text.SimpleDateFormat
import java.util.Date

/*** /dmarket event <list|trigger [id]|last> */
class EventCommand(private val eventManager: MarketEventManager) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        return when (args.getOrNull(1)?.lowercase()) {
            "list" -> list()
            "trigger" -> trigger(args.getOrNull(2))
            "last" -> last()
            else -> listOf("Usage: /dmarket event <list|trigger [id]|last>")
        }
    }

    private fun list(): List<String> {
        val definitions = eventManager.definitions
        if (definitions.isEmpty()) return listOf("No events configured in events.yml")

        return definitions.map {
            "${ChatColor.GRAY}${it.id} ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}${it.scope.name.lowercase()}" +
                "${ChatColor.DARK_GRAY}, weight ${ChatColor.WHITE}${it.weight}" +
                "${ChatColor.DARK_GRAY}, ${it.multiplierMin}x-${it.multiplierMax}x ${it.direction.name.lowercase()}"
        }
    }

    private fun trigger(definitionId: String?): List<String> {
        val fired = eventManager.triggerNow(definitionId)
            ?: return listOf(
                "Couldn't fire " + (definitionId?.let { "'$it'" } ?: "a random event") +
                    " - unknown id, or nothing eligible right now",
            )

        val target = fired.target ?: "the whole market"
        return listOf(
            "${ChatColor.GREEN}Fired ${ChatColor.WHITE}${fired.definitionId}" +
                "${ChatColor.GREEN} on ${ChatColor.WHITE}${target.let(::prettyItemName)}" +
                "${ChatColor.GREEN} (${fired.direction}, x${"%.2f".format(fired.multiplier)})",
        )
    }

    private fun last(): List<String> {
        val last = eventManager.lastEvent() ?: return listOf("No event has fired since the server started")

        return listOf(
            "${ChatColor.GRAY}${last.definitionId} ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}${last.scope}" +
                "${ChatColor.DARK_GRAY}/${ChatColor.WHITE}${last.target ?: "all"}" +
                "${ChatColor.DARK_GRAY}, ${ChatColor.WHITE}${last.direction}" +
                "${ChatColor.DARK_GRAY}, x${ChatColor.WHITE}${"%.2f".format(last.multiplier)}" +
                "${ChatColor.DARK_GRAY} at ${ChatColor.WHITE}${TIME_FORMAT.format(Date(last.triggeredAt))}",
        )
    }

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm")
    }
}
