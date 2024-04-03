package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.configs.ItemConfig;
import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;

public class MarketInitializer {
    ItemConfig config;
    MarketManager manager;

    /***
     * Gets the values from the item config and puts them
     * into the market manager and then initiates push to db
     */
    public MarketInitializer(ItemConfig config, MarketManager manager){
        this.config = config;
        this.manager = manager;

        List<MarketItem> configItems = config.getAllItems();
        List<MarketItem> managerItems = manager.getWorkingItems();

        //check if config item values are different if so update
        for (MarketItem configItem : configItems) {
            for (MarketItem managerItem : managerItems) {
                if (configItem.equals(managerItem)) {
                    if (configItem.getBasePrice() != managerItem.getBasePrice()) {
                        managerItem.setBasePrice(configItem.getBasePrice());
                    }
                    if (configItem.getMinPrice() != managerItem.getMinPrice()) {
                        managerItem.setMinPrice(configItem.getMinPrice());
                    }
                }
            }
        }
        manager.getDatabaseHandler().pushItems();

    }

}
