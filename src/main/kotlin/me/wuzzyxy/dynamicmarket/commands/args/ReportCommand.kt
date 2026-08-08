package me.wuzzyxy.dynamicmarket.commands.args

import me.wuzzyxy.dynamicmarket.market.MarketManager
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender

class ReportCommand(private val manager: MarketManager) : ArgsCommand {

    override fun execute(args: Array<out String>, sender: CommandSender): List<String> {
        val report = manager.report
        report.refresh()

        val tracked = report.trackedCount()
        val total = manager.workingItems.size
        val lines = mutableListOf(
            "${ChatColor.GRAY}Market, last ${report.windowHours}h " +
                "${ChatColor.DARK_GRAY}($tracked/$total with a baseline)"
        )

        if (tracked == 0) {
            lines += "${ChatColor.RED}No item has any price history yet."
            lines += "${ChatColor.GRAY}Nothing can show as a mover until the snapshot task has run once."
            return lines
        }

        for (category in report.categories()) {
            lines += "${ChatColor.DARK_GRAY}$category"
            lines += "  " + report.moverLine(arrayOf("up", category, "1"))
            lines += "  " + report.moverLine(arrayOf("down", category, "1"))
        }
        return lines
    }
}
