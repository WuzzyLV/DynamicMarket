package me.wuzzyxy.dynamicmarket;

public class PluginConfig {
    private DynamicMarket plugin;
    public PluginConfig( DynamicMarket plugin) {
        this.plugin = plugin;

        HOST= plugin.getConfig().getString("mysql.host");
        PORT= plugin.getConfig().getInt("mysql.port");
        DATABASE= plugin.getConfig().getString("mysql.database");
        USERNAME= plugin.getConfig().getString("mysql.username");
        PASSWORD= plugin.getConfig().getString("mysql.password");


    }

    public final String HOST;
    public final int PORT;
    public final String DATABASE;
    public final String USERNAME;
    public final String PASSWORD;



}
