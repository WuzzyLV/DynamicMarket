package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class BuySellCommand implements ArgsCommand{

    MarketManager manager;
    public BuySellCommand(MarketManager manager) {
        this.manager = manager;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        if (args.length < 3) {
            return Optional.of(new String[]{"Not enough arguments"});
        }

        String itemName = args[1];
        if (manager.getPersistedItem(itemName).isEmpty()) {
            return Optional.of(new String[]{"Item not found"});
        }
        int amount = Integer.parseInt(args[2]);
        if (amount <= 0) {
            return Optional.of(new String[]{"Amount must be greater than 0"});
        }

        if (args[0].equalsIgnoreCase("buy")) {
            return buy(itemName, amount);
        } else if (args[0].equalsIgnoreCase("sell")) {
            return sell(itemName, amount);
        } else {
            return Optional.of(new String[]{"Invalid command"});
        }
    }

    private Optional<String[]> buy(String itemName, int amount) {
        manager.getWorkingItem(itemName).ifPresent(item -> {
            item.setBoughtAmount(item.getBoughtAmount() + amount);
        });
        return Optional.empty();
    }

    private Optional<String[]> sell(String itemName, int amount) {
        manager.getWorkingItem(itemName).ifPresent(item -> {
            item.setSoldAmount(item.getSoldAmount() + amount);
        });
        return Optional.empty();
    }


}
