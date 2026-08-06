package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.items.MarketItem;
import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.Optional;

public class BuySellCommand implements ArgsCommand{

    MarketManager manager;
    DecimalFormat df = new DecimalFormat("0.00");
    public BuySellCommand(MarketManager manager) {
        this.manager = manager;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        if (args.length < 3) {
            return Optional.of(new String[]{"Not enough arguments"});
        }

        MarketItem item = manager.getWorkingItem(args[1]).orElse(null);
        if (item == null) {
            return Optional.of(new String[]{"Item not found"});
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return Optional.of(new String[]{"Amount must be a number"});
        }
        if (amount <= 0) {
            return Optional.of(new String[]{"Amount must be greater than 0"});
        }

        if (args[0].equalsIgnoreCase("buy")) {
            return buy(item, amount);
        } else if (args[0].equalsIgnoreCase("sell")) {
            return sell(item, amount);
        } else {
            return Optional.of(new String[]{"Invalid command"});
        }
    }

    /***
     * Answers with the price that was actually charged, bare so a shop can parse it.
     * Reading a placeholder first and reporting the fill afterwards prices the trade
     * against one snapshot and books it against another, and the gap between the two
     * is walkable.
     */
    private Optional<String[]> buy(MarketItem item, int amount) {
        double price = manager.getPriceHandler().getBuyPrice(item, amount);
        item.setBoughtAmount(item.getBoughtAmount() + amount);
        return Optional.of(new String[]{df.format(price)});
    }

    private Optional<String[]> sell(MarketItem item, int amount) {
        double price = manager.getPriceHandler().getSellPrice(item, amount);
        item.setSoldAmount(item.getSoldAmount() + amount);
        return Optional.of(new String[]{df.format(price)});
    }
}
