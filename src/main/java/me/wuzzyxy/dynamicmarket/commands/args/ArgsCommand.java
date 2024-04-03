package me.wuzzyxy.dynamicmarket.commands.args;

import me.wuzzyxy.dynamicmarket.commands.CommandError;

public interface ArgsCommand {
    CommandError execute(String[] args);
}
