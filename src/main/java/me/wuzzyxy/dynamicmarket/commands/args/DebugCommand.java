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
        describe(sender, "Working", workingItem.get());
        describe(sender, "Persisted", persistedItem.get());
        return Optional.empty();
    }

    private void describe(CommandSender sender, String label, MarketItem item) {
        sender.sendMessage(
                label + " Bought/Sold "
                + ChatColor.GREEN+item.getBoughtAmount()
                + ChatColor.WHITE+" / "
                + ChatColor.RED+item.getSoldAmount()
        );
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Net: &a" + String.format("%.1f", item.getNet())
                + "&7  Unit: &a" + String.format("%.4f", priceHandler.getUnitPrice(item))
                + "&7  Stack: &a" + String.format("%.2f", priceHandler.getBuyPrice(item, 64))));
        sender.sendMessage("k: " + item.getK() + "  half-life: " + item.getHalfLifeHours() + "h");

    }
}
