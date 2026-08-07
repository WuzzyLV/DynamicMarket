package me.wuzzyxy.dynamicmarket.database;

import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;
import java.util.Map;

public interface Database {
    void die();

    MarketItem addItem(MarketItem item);
    MarketItem setItem(MarketItem item);
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
    Map<String, Double> getPricesAt(int hoursAgo);

    @Deprecated
    boolean createHistoryPoint(MarketItem item);


}
