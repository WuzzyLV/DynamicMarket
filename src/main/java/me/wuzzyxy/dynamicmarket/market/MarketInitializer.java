package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.configs.ItemConfig;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.logging.Logger;

public class MarketInitializer {
    ItemConfig config;
    MarketManager manager;

    /***
     * Gets the values from the item config and puts them
     * into the market manager and then initiates push to db
     */
    public MarketInitializer(ItemConfig config, MarketManager manager, Logger logger) {
        this.config = config;
        this.manager = manager;

        List<MarketItem> configItems = config.getAllItems();
        List<MarketItem> managerItems = manager.getWorkingItems();

        //check if config item values are different if so update
        boolean found;
        for (MarketItem configItem : configItems) {
            found = false;
            for (MarketItem managerItem : managerItems) {
                if (configItem.equals(managerItem)) {
                    found = true;
                    if (configItem.getBasePrice() != managerItem.getBasePrice()) {
                        managerItem.setBasePrice(configItem.getBasePrice());
                    }
                    if (configItem.getMinPrice() != managerItem.getMinPrice()) {
                        managerItem.setMinPrice(configItem.getMinPrice());
                    }
                    if (configItem.getPercentage() != managerItem.getPercentage()) {
                        managerItem.setPercentage(configItem.getPercentage());
                    }
                }
            }
            if (!found) {
                logger.info(ChatColor.GREEN + "Adding new item: " + configItem.getName());
                manager.addItem(configItem.getName(), configItem.getBasePrice(), configItem.getMinPrice(), configItem.getPercentage());
            }
        }
        manager.getDatabaseHandler().pushItems();

    }

}
