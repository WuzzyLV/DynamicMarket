package me.wuzzyxy.dynamicmarket;

import me.wuzzyxy.dynamicmarket.commands.CommandCompleter;
import me.wuzzyxy.dynamicmarket.commands.DMarketCommand;
import me.wuzzyxy.dynamicmarket.configs.ItemConfig;
import me.wuzzyxy.dynamicmarket.configs.PluginConfig;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.database.MySqlDatabase;
import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;

public final class DynamicMarket extends JavaPlugin {

    private PluginConfig config;
    private ItemConfig itemConfig;
    private Database database;
    private MarketManager marketManager;


    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        // CONFIGS
        config = new PluginConfig(this);
        itemConfig = new ItemConfig(this);

        try {
            database = new MySqlDatabase(this);
        } catch (SQLException e) {
            this.getLogger().severe("Failed to initialize Database!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }catch (IOException e) {
            this.getLogger().severe("Failed to load SQL scripts!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }

        marketManager = new MarketManager(this, database);

        // COMMANDS
        this.getCommand("dmarket").setExecutor(new DMarketCommand(this));
        this.getCommand("dmarket").setTabCompleter(new CommandCompleter(marketManager));


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    //Statically coded cause only accessed once and less error-prone :)
    public ArrayList<String> getSQLScripts() throws IOException {
        ArrayList<String> scripts = new ArrayList<>();
        InputStream stream;
        stream = getClass().getClassLoader().getResourceAsStream("sql/items.sql");
        scripts.add(new String(stream.readAllBytes()));

        stream = getClass().getClassLoader().getResourceAsStream("sql/item_history.sql");
        scripts.add(new String(stream.readAllBytes()));

        return scripts;
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public ItemConfig getItemConfig() {
        return itemConfig;
    }

    public MarketManager getMarketManager() { return marketManager; }
    public void reloadPluginConfig() {
        config = new PluginConfig(this);
    }
}
