package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.market.MarketDatabaseHandler;
import org.bukkit.command.CommandSender;
import java.util.Optional;

public class HomeCommand implements ArgsCommand{
    DynamicMarket plugin;
    private int pushInterval = 0;
    public HomeCommand(DynamicMarket plugin) {
        this.plugin = plugin;
        pushInterval = plugin.getPluginConfig().PUSH_INTERVAL;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        sender.sendMessage("DynamicMarket " + plugin.getDescription().getVersion());

        long currentTime = System.currentTimeMillis();
        long timeSinceLastPush = System.currentTimeMillis() - MarketDatabaseHandler.lastPushTime;
        long nextPushTime = pushInterval*1000L - timeSinceLastPush;

        sender.sendMessage("Push to database in: " + nextPushTime/1000.0 + " s");
        return Optional.empty();
    }


}
