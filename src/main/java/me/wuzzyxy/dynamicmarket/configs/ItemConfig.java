package me.wuzzyxy.dynamicmarket.configs;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ItemConfig {
    FileConfiguration config;
    DynamicMarket plugin;
    public ItemConfig(DynamicMarket plugin) {
        this.plugin = plugin;

        config = initFiles();

//        List<MarketItem> items = getAllItems();
//        for (MarketItem item : items) {
//            plugin.getLogger().info("Loaded item: " + item.toString());
//        }
    }

    public YamlConfiguration initFiles() {
        File configFile = new File(plugin.getDataFolder(), "items.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("items.yml", false);
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    public List<MarketItem> getAllItems() {
        List<MarketItem> items = new ArrayList<>();
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            double basePrice = config.getDouble("items." + key + ".base_price");
            double minPrice = config.getDouble("items." + key + ".min_price");
            double percentage = config.getDouble("items." + key + ".percentage");
            items.add(new MarketItem(key, basePrice, 0,0, minPrice, percentage));
        }
        return items;
    }
}
