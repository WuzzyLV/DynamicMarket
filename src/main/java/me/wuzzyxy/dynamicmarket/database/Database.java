package me.wuzzyxy.dynamicmarket.database;

import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;

public interface Database {
    void die();

    MarketItem addItem(String item, double basePrice, double minPrice);
    MarketItem addItem(String item, double basePrice, double minPrice, int boughtAmount, int soldAmount);
    MarketItem setItem(String item, double basePrice, double minPrice, int boughtAmount, int soldAmount);
    MarketItem setItemPrices(String item, double basePrice, double minPrice);
    boolean removeItem(String item);
    MarketItem getItem(String item);
    List<MarketItem> getAllItems();
    List<MarketItem> setAllItems(List<MarketItem> items);
    MarketItem setBasePrice(MarketItem item, double basePrice);
    MarketItem getBasePrice(MarketItem item);
    MarketItem setMinPrice(MarketItem item, double minPrice);
    MarketItem getMinPrice(MarketItem item);

    MarketItem getBoughtAmount(MarketItem item);
    MarketItem getSoldAmount(MarketItem item);
    MarketItem addBoughtAmount(MarketItem item, int amount);
    MarketItem addSoldAmount(MarketItem item, int amount);
    MarketItem setBoughtAmount(MarketItem item, int amount);
    MarketItem setSoldAmount(MarketItem item, int amount);
    MarketItem setAmounts(MarketItem item, int boughtAmount, int soldAmount);

    boolean createHistoryPoint(MarketItem item);


}
