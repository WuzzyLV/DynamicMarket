package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.market.MarketManager;
import me.wuzzyxy.dynamicmarket.market.MarketReport;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReportCommand implements ArgsCommand{
    MarketManager manager;
    int windowHours;

    public ReportCommand(MarketManager manager, int windowHours) {
        this.manager = manager;
        this.windowHours = windowHours;
    }

    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        MarketReport report = manager.getReport();
        report.refresh();

        List<String> lines = new ArrayList<>();
        int tracked = report.trackedCount();
        int total = manager.getWorkingItems().size();
        lines.add(ChatColor.GRAY + "Market, last " + windowHours + "h "
                + ChatColor.DARK_GRAY + "(" + tracked + "/" + total + " with a baseline)");

        if (tracked == 0) {
            lines.add(ChatColor.RED + "No item has any price history yet.");
            lines.add(ChatColor.GRAY + "Nothing can show as a mover until the snapshot task has run once.");
            return Optional.of(lines.toArray(new String[0]));
        }

        for (String category : report.categories()) {
            lines.add(ChatColor.DARK_GRAY + category);
            lines.add("  " + report.moverLine(new String[]{"up", category, "1"}));
            lines.add("  " + report.moverLine(new String[]{"down", category, "1"}));
        }
        return Optional.of(lines.toArray(new String[0]));
    }
}
