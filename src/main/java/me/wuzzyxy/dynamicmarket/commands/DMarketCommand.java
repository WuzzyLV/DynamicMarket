package me.wuzzyxy.dynamicmarket.commands;

import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.commands.args.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.HashMap;

public class DMarketCommand implements CommandExecutor {

    private final DynamicMarket plugin;
    private final HashMap<String, ArgsCommand> subCommands = new HashMap<>();

    public DMarketCommand(DynamicMarket plugin) {
        this.plugin = plugin;

        // Register subcommands
        subCommands.put("", new HomeCommand(plugin));
        subCommands.put("buy", new BuySellCommand(plugin.getMarketManager()));
        subCommands.put("sell", new BuySellCommand(plugin.getMarketManager()));
        subCommands.put("set", new SetCommand(plugin.getMarketManager()));
        subCommands.put("debug", new DebugCommand(plugin.getMarketManager()));
        subCommands.put("push", new ForcePushCommand(plugin.getMarketManager().getDatabaseHandler()));

        plugin.getCommand("dmarket").setTabCompleter(new CommandCompleter(
                plugin.getMarketManager(),
                subCommands.keySet().stream().toList()
        ));
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subCommandName = args.length > 0 ? args[0] : "";
        ArgsCommand subCommand = subCommands.get(subCommandName);
        if (subCommand == null) {
            sender.sendMessage("Unknown subcommand");
            return false;
        }

        subCommand.execute(args, sender).ifPresent(errors -> {
            for (String error : errors) {
                sender.sendMessage(error);
            }
        });
        return true;
    }
}
