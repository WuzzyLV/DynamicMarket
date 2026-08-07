package me.wuzzyxy.dynamicmarket.configs;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        try {
            for (String key : Objects.requireNonNull(config.getConfigurationSection("items")).getKeys(false)) {
                String path = "items." + key + ".";
                MarketItem item = new MarketItem(
                        key,
                        config.getDouble(path + "base_price"),
                        0, 0,
                        config.getDouble(path + "min_price"),
                        impactK(path)
                );
                item.setHalfLifeHours(config.getDouble(path + "half_life_hours", MarketItem.DEFAULT_HALF_LIFE_HOURS));
                item.setCategory(config.getString(path + "category", "misc"));
                items.add(item);
            }
        }catch (NullPointerException e) {
            plugin.getLogger().severe("Failed to load items from config");
        }
        return items;
    }

    /***
     * units_to_double is the version a person can reason about — how many net units it
     * takes to double the price. Configs written before the rename only have percentage,
     * which is the same number to first order, so they carry over without editing.
     */
    private double impactK(String path) {
        double unitsToDouble = config.getDouble(path + "units_to_double", 0);
        if (unitsToDouble > 0) return Math.log(2) / unitsToDouble;
        return config.getDouble(path + "percentage");
    }
}
