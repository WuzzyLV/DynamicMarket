package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class SetCommand implements ArgsCommand{
    MarketManager manager;
    public SetCommand(MarketManager manager) {
        this.manager = manager;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        if (args.length < 4) {
            return Optional.of(new String[]{"Not enough arguments"});
        }

        String itemName = args[1];
        if (manager.getPersistedItem(itemName).isEmpty()) {
            return Optional.of(new String[]{"Item not found"});
        }
        long boughtAmount;
        long soldAmount;
        try {
            boughtAmount = Long.parseLong(args[2]);
            soldAmount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            return Optional.of(new String[]{"Amounts must be numbers"});
        }
        if (boughtAmount < 0 || soldAmount < 0) {
            return Optional.of(new String[]{"Amount must be greater than or equal to 0"});
        }

        manager.getWorkingItem(itemName).ifPresent(item -> item.setCounters(boughtAmount, soldAmount));
        return Optional.of(new String[]{"Set item " + itemName + " to " + boughtAmount + " bought and " + soldAmount + " sold"});
    }
}
