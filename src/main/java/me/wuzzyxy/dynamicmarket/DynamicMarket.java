package me.wuzzyxy.dynamicmarket;

import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.database.MySqlDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;

public final class DynamicMarket extends JavaPlugin {

    private PluginConfig config;
    private Database database;


    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        // Plugin startup logic
        config = new PluginConfig(this);

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




    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    //Statically coded cause only accessed once and less error prone :)
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
    public void reloadPluginConfig() {
        config = new PluginConfig(this);
    }
}
