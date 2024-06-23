package me.wuzzyxy.dynamicmarket.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import me.wuzzyxy.dynamicmarket.market.MarketManager;
import me.wuzzyxy.dynamicmarket.market.PriceHandler;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class SellPriceExpansion extends PlaceholderExpansion {
    private final DynamicMarket plugin;
    private final MarketManager manager;
    private final PriceHandler priceHandler;
    DecimalFormat df;
    public SellPriceExpansion(DynamicMarket plugin, MarketManager manager, PriceHandler priceHandler) {
        this.plugin = plugin;
        this.manager = manager;
        this.priceHandler = priceHandler;

        df = new DecimalFormat("0.00");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "DMSellPrice";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Wuzzy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    @Override
    public boolean persist() {
        return true; //
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split(",");

        if (args.length != 2) return "DMSellPrice_Item,Amount" + args.length + " args provided";

        MarketItem item = manager.getPersistedItem(args[0]).orElse(null);
        if (item == null) return args[0]+ " not found";

        try {
            int amount = Integer.parseInt(args[1]);
            double price = priceHandler.getSellPrice(item, amount);
            return df.format(price);
        } catch (NumberFormatException e) {
            return "Invalid params";
        }
    }


}
