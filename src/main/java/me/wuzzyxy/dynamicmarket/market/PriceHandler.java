package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.configs.PluginConfig;
import me.wuzzyxy.dynamicmarket.items.MarketItem;

public class PriceHandler {
    private final PluginConfig pluginConfig;
    public PriceHandler(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public double getBuyPrice(MarketItem item, int amount) {
        double price = ((item.getBasePrice() * item.getPercentage()) * item.getBoughtAmount() -
                ((item.getBasePrice() * item.getPercentage()) * item.getSoldAmount()) + item.getBasePrice()) * amount;
        return Math.max(price, item.getMinPrice());
    }

    public double getSellPrice(MarketItem item, int amount) {
        double price = getBuyPrice(item, amount) * pluginConfig.SELL_MULTIPLIER;
        return Math.max(price, item.getMinPrice() * pluginConfig.SELL_MULTIPLIER);
    }

}
