package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.configs.PluginConfig;
import me.wuzzyxy.dynamicmarket.items.MarketItem;

public class PriceHandler {
    private PluginConfig pluginConfig;
    private double percentage;
    public PriceHandler(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
        percentage = pluginConfig.PERCENTAGE;
    }

    public double getPrice(MarketItem item, int amount) {
        double price = ((item.getBasePrice() * percentage) * item.getBoughtAmount() -
                ((item.getBasePrice() * percentage) * item.getSoldAmount()) + item.getBasePrice()) * amount;
        return Math.max(price, item.getMinPrice());
    }
    public double getPrice(MarketItem item, int amount, double percentage) {
        double price = ((item.getBasePrice() * percentage) * item.getBoughtAmount() -
                ((item.getBasePrice() * percentage) * item.getSoldAmount()) + item.getBasePrice()) * amount;
        return Math.max(price, item.getMinPrice());
    }
}
