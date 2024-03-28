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

    public MarketDatabaseHandler(MarketManager manager, Database database, DynamicMarket plugin) {
        this.manager = manager;
        this.database = database;
        this.plugin = plugin;

        starRepeatingTask();
    }

    public void pushItems() {
        List<MarketItem> items = manager.getAllItems();

        database.setAllItems(items);

        plugin.getLogger().info("Pushed items to database");
    }

    public void starRepeatingTask(){
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::pushItems,
                0, plugin.getPluginConfig().PUSH_INTERVAL * 20L
        );
    }
}
