package me.wuzzyxy.dynamicmarket.commands;

import me.wuzzyxy.dynamicmarket.items.MarketItem;
import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class CommandCompleter implements TabCompleter {

    MarketManager marketManager;
    List<String> items;
    List<String> commands = List.of("help", "reload", "buy", "sell");
    public CommandCompleter(MarketManager marketManager) {
        this.marketManager = marketManager;
        items = marketManager.getAllItems().stream().map(MarketItem::getName).toList();
    }
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        List<String> results = new ArrayList<>();
        if (strings.length == 1) {
            StringUtil.copyPartialMatches(strings[0], commands, results);
            return results;
        }
        if (strings[0].equals("buy") || strings[0].equals("sell")) {
            if (strings.length == 2)
                StringUtil.copyPartialMatches(strings[1], items, results);
            else if (strings.length == 3)
                results.add("1");

            return results;
        }
        return results;
    }
}
