package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.List;
import java.util.Optional;

public class MarketManager {

    private final DynamicMarket plugin;
    private final Database database;

    private final MarketInitializer initializer;
    private final MarketDatabaseHandler databaseHandler;

    private final List<MarketItem> items;
    public MarketManager(DynamicMarket plugin, Database database) {
        this.plugin = plugin;
        this.database = database;

        items = getAllDBItems();
        if (items == null) {
            plugin.getLogger().severe("Failed to load items from database");
        }

        this.databaseHandler = new MarketDatabaseHandler(this, database, plugin);
        this.initializer = new MarketInitializer(plugin.getItemConfig(), this);

    }

    private List<MarketItem> getAllDBItems() {
        return database.getAllItems();
    }

    public Optional<MarketItem> addItem(String name, double basePrice, double minPrice) {
        if (getItem(name).isPresent()) return Optional.empty();

        MarketItem item = database.addItem(name, basePrice, minPrice);
        items.add(item);
        return Optional.ofNullable(item);
    }

    public Optional<MarketItem> getItem(String name) {
        for (MarketItem item : items) {
            if (item.getName().equals(name)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public Optional<MarketItem> getItem(MarketItem item) {
        return getItem(item.getName());
    }

    public boolean removeItem(String name) {
        Optional<MarketItem> item = getItem(name);
        if (item.isEmpty()) return false;

        items.remove(item.get());
        return database.removeItem(name);
    }

    public boolean removeItem(MarketItem item) {
        return removeItem(item.getName());
    }

    public List<MarketItem> getAllItems() {
        return items;
    }

    public MarketInitializer getInitializer() {
        return initializer;
    }

    public MarketDatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }
}
