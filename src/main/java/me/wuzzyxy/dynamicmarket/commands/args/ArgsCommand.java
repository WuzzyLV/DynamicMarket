package me.wuzzyxy.dynamicmarket.commands.args;

import org.bukkit.command.CommandSender;

import java.util.Optional;

public interface ArgsCommand {
    Optional<String[]> execute(String[] args, CommandSender sender);
}
