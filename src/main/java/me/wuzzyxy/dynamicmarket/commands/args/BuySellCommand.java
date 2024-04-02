package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.commands.CommandError;
import me.wuzzyxy.dynamicmarket.market.MarketManager;

public class BuySellCommand implements ArgsCommand{

    MarketManager manager;
    public BuySellCommand(MarketManager manager) {
        this.manager = manager;
    }
    @Override
    public CommandError execute(String[] args) {
        if (args.length < 3) {
            return new CommandError(new String[]{"Not enough arguments"});
        }

        String itemName = args[1];
        if (manager.getItem(itemName).isEmpty()) {
            return new CommandError(new String[]{"Item not found"});
        }
        int amount = Integer.parseInt(args[2]);
        if (amount <= 0) {
            return new CommandError(new String[]{"Amount must be greater than 0"});
        }

        if (args[0].equals("buy")) {
            return buy(itemName, amount);
        } else if (args[0].equals("sell")) {
            return sell(itemName, amount);
        } else {
            return new CommandError(new String[]{"Unknown subcommand"});
        }
    }

    private CommandError buy(String itemName, int amount) {
        manager.getItem(itemName).ifPresent(item -> {
            item.setBoughtAmount(item.getBoughtAmount() + amount);

        });
        return null;
    }

    private CommandError sell(String itemName, int amount) {
        manager.getItem(itemName).ifPresent(item -> {
            item.setSoldAmount(item.getSoldAmount() + amount);
        });
        return null;
    }


}
