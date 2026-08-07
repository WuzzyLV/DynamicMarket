package me.wuzzyxy.dynamicmarket.database;

import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;

public interface Database {
    void die();

    MarketItem addItem(String item, double basePrice, double minPrice, double impactK, double halfLifeHours);
    MarketItem addItem(String item, double basePrice, double minPrice, long boughtAmount, long soldAmount, double impactK, double halfLifeHours);
    MarketItem setItem(String item, double basePrice, double minPrice, long boughtAmount, long soldAmount, double impactK, double halfLifeHours);
    MarketItem setItemStatics(String item, double basePrice, double minPrice, double impactK);
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
    MarketItem setBoughtAmount(MarketItem item, long amount);
    MarketItem setSoldAmount(MarketItem item, long amount);
    MarketItem setAmounts(MarketItem item, long boughtAmount, long soldAmount);

    boolean snapshotHistory();
    int pruneHistory(int retentionDays);

    @Deprecated
    boolean createHistoryPoint(MarketItem item);


}
