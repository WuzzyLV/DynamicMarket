package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.market.MarketDatabaseHandler;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class ForcePushCommand implements ArgsCommand{
    MarketDatabaseHandler handler;
    public ForcePushCommand(MarketDatabaseHandler handler) {
        this.handler = handler;
    }
    @Override
    public Optional<String[]> execute(String[] args, CommandSender sender) {
        handler.pushItems();
        return Optional.of(new String[]{"Pushed items to database"});
    }
}
