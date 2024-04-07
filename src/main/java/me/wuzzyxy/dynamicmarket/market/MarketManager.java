package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.items.MarketItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MarketManager {

    private final DynamicMarket plugin;
    private final Database database;

    private final MarketInitializer initializer;
    private final MarketDatabaseHandler databaseHandler;
    private final PriceHandler priceHandler;

    private final List<MarketItem> workingItems;
    private final List<MarketItem> persistedItems;
    public MarketManager(DynamicMarket plugin, Database database) {
        this.plugin = plugin;
        this.database = database;

        workingItems = new ArrayList<MarketItem>();
        persistedItems = getAllDBItems();
        if (persistedItems == null) {
            plugin.getLogger().severe("Failed to load items from database");
        }else {
            for (MarketItem item : persistedItems) {
                if (item == null) {
                    plugin.getLogger().severe("Failed to load items from database");
                    continue;
                }
                workingItems.add(item.clone());
            }
        }

        this.databaseHandler = new MarketDatabaseHandler(this, database, plugin);
        this.initializer = new MarketInitializer(plugin.getItemConfig(), this);
        this.priceHandler = new PriceHandler(plugin.getPluginConfig());

    }

    private List<MarketItem> getAllDBItems() {
        return database.getAllItems();
    }

    /***
     * Returns the working item object
     */
    public Optional<MarketItem> addItem(String name, double basePrice, double minPrice, double percentage) {
        if (getPersistedItem(name).isPresent()) return Optional.empty();

        MarketItem item = database.addItem(name, basePrice, minPrice, percentage);
        workingItems.add(item);
        persistedItems.add(item.clone());
        return Optional.of(item);
    }

    public Optional<MarketItem> getPersistedItem(String name) {
        for (MarketItem item : persistedItems) {
            if (item.getName().equals(name)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public Optional<MarketItem> getWorkingItem(String itemName) {
        for (MarketItem item : workingItems) {
            if (item.getName().equals(itemName)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public Optional<MarketItem> getPersistedItem(MarketItem item) {
        return getPersistedItem(item.getName());
    }

    public boolean removeItem(String name) {
        Optional<MarketItem> item = getPersistedItem(name);
        if (item.isEmpty()) return false;

        persistedItems.remove(item.get());
        workingItems.remove(item.get());
        return database.removeItem(name);
    }

    public boolean removeItem(MarketItem item) {
        return removeItem(item.getName());
    }

    public List<MarketItem> getPersistedItems() {
        return persistedItems;
    }
    public List<MarketItem> getWorkingItems() {
        return workingItems;
    }

    public MarketInitializer getInitializer() {
        return initializer;
    }

    public MarketDatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }

    public PriceHandler getPriceHandler() {
        return priceHandler;
    }
}
