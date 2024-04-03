package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.bukkit.Bukkit;

import javax.xml.crypto.Data;
import java.util.List;

public class MarketDatabaseHandler {
    MarketManager manager;
    Database database;
    DynamicMarket plugin;

    //Dirty just for debug
    public static Long lastPushTime;

    public MarketDatabaseHandler(MarketManager manager, Database database, DynamicMarket plugin) {
        this.manager = manager;
        this.database = database;
        this.plugin = plugin;

        starRepeatingTask();
    }

    public void pushItems() {
        List<MarketItem> workingItems = manager.getWorkingItems();
        System.out.println("Working items: " + workingItems.toString());
        manager.getPersistedItems().clear();
        database.setAllItems(workingItems).forEach(
                item -> manager.getPersistedItems().add(item)
        );
        System.out.println("Persisted items: " + manager.getPersistedItems().toString());

        lastPushTime = System.currentTimeMillis();
        plugin.getLogger().info("Pushed items to database");
    }

    public void starRepeatingTask(){
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::pushItems,
                0, plugin.getPluginConfig().PUSH_INTERVAL * 20L
        );
    }
}
