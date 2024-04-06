package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.items.MarketItem;
import me.wuzzyxy.dynamicmarket.market.MarketManager;
import me.wuzzyxy.dynamicmarket.market.PriceHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class DebugCommand implements ArgsCommand{
    MarketManager manager;
    PriceHandler priceHandler;
    public DebugCommand(MarketManager manager, PriceHandler priceHandler){
        this.manager = manager;
        this.priceHandler = priceHandler;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        if (args.length < 2) {
            return Optional.of(new String[]{"Not enough arguments"});
        }

        String itemName = args[1];
        Optional<MarketItem> persistedItem = manager.getPersistedItem(itemName);
        Optional<MarketItem> workingItem = manager.getWorkingItem(itemName);

        if (persistedItem.isEmpty() || workingItem.isEmpty()) {
            return Optional.of(new String[]{"Item not found"});
        }
        sender.sendMessage("Item: " + persistedItem.get().getName());
        sender.sendMessage(
                "Working Bought/Sold "
                + ChatColor.GREEN+workingItem.get().getBoughtAmount()
                + ChatColor.WHITE+" / "
                + ChatColor.RED+workingItem.get().getSoldAmount()
        );
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Price: &a" + priceHandler.getPrice(workingItem.get(), 1)));
        sender.sendMessage(
                "Persisted Bought/Sold "
                        + ChatColor.GREEN+persistedItem.get().getBoughtAmount()
                        + ChatColor.WHITE+" / "
                        + ChatColor.RED+persistedItem.get().getSoldAmount()
        );
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Price: &a" + priceHandler.getPrice(persistedItem.get(), 1)));
        return Optional.empty();

    }
}
