package me.wuzzyxy.dynamicmarket.commands;

import me.wuzzyxy.dynamicmarket.market.MarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BuySellCommand implements CommandExecutor {
    private final MarketManager manager;
    public BuySellCommand(MarketManager manager) {
        this.manager = manager;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }
}
