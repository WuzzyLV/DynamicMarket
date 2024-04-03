package me.wuzzyxy.dynamicmarket.commands;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.commands.args.ArgsCommand;
import me.wuzzyxy.dynamicmarket.commands.args.BuySellCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;

public class DMarketCommand implements CommandExecutor {

    private final DynamicMarket plugin;
    private final HashMap<String, ArgsCommand> subCommands = new HashMap<>();

    public DMarketCommand(DynamicMarket plugin) {
        this.plugin = plugin;

        // Register subcommands
        subCommands.put("buy", new BuySellCommand(plugin.getMarketManager()));
        subCommands.put("sell", new BuySellCommand(plugin.getMarketManager()));
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("DynamicMarket " + plugin.getDescription().getVersion());
            return true;
        }
        ArgsCommand subCommand = subCommands.get(args[0]);
        if (subCommand == null) {
            sender.sendMessage("Unknown subcommand");
            return false;
        }
        subCommand.execute(args);
        return true;
    }
}
