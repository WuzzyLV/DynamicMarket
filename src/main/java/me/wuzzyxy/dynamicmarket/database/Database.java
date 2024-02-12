package me.wuzzyxy.dynamicmarket.database;

import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;

public interface Database {
    void die();

    MarketItem addItem(String item, double basePrice, double minPrice);
    boolean removeItem(String item);
    MarketItem getItem(String item);
    List<MarketItem> getAllItems();
    MarketItem setBasePrice(MarketItem item, double basePrice);
    MarketItem getBasePrice(MarketItem item);

    MarketItem getBoughtAmount(MarketItem item);
    MarketItem getSoldAmount(MarketItem item);
    MarketItem addBoughtAmount(MarketItem item, int amount);
    MarketItem addSoldAmount(MarketItem item, int amount);
    MarketItem setBoughtAmount(MarketItem item, int amount);
    MarketItem setSoldAmount(MarketItem item, int amount);

    boolean createHistoryPoint(MarketItem item);


}
