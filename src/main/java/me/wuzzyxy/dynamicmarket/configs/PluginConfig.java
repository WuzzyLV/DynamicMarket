package me.wuzzyxy.dynamicmarket.configs;

import me.wuzzyxy.dynamicmarket.DynamicMarket;

public class PluginConfig {
    public PluginConfig( DynamicMarket plugin) {
        HOST= plugin.getConfig().getString("mysql.host");
        PORT= plugin.getConfig().getInt("mysql.port");
        DATABASE= plugin.getConfig().getString("mysql.database");
        USERNAME= plugin.getConfig().getString("mysql.username");
        PASSWORD= plugin.getConfig().getString("mysql.password");

        PUSH_INTERVAL = plugin.getConfig().getInt("push_interval");
        PERCENTAGE = plugin.getConfig().getDouble("percentage");
        SELL_MULTIPLIER = plugin.getConfig().getDouble("sell_multiplier");

    }

    public final int PUSH_INTERVAL;
    public final double PERCENTAGE;

    public final double SELL_MULTIPLIER;
    public final String HOST;
    public final int PORT;
    public final String DATABASE;
    public final String USERNAME;
    public final String PASSWORD;



}
