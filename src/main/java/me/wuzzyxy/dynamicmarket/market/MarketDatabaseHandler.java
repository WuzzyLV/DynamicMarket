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
        startSnapshotTask();
    }

    public void pushItems() {
        List<MarketItem> workingItems = manager.getWorkingItems();
        manager.getPersistedItems().clear();
        database.setAllItems(workingItems).forEach(
                item -> manager.getPersistedItems().add(item)
        );

        lastPushTime = System.currentTimeMillis();
    }

    public void starRepeatingTask(){
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::pushItems,
                0, plugin.getPluginConfig().PUSH_INTERVAL * 20L
        );
    }

    private void startSnapshotTask() {
        long ticks = plugin.getPluginConfig().HISTORY_SNAPSHOT_MINUTES * 60L * 20L;
        if (ticks <= 0) return;

        // First one lands shortly after boot rather than a whole interval later, so a new
        // install has something to compare against instead of an empty report all day.
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            database.snapshotHistory();
            database.pruneHistory(plugin.getPluginConfig().HISTORY_RETENTION_DAYS);
        }, 200L, ticks);
    }
}
