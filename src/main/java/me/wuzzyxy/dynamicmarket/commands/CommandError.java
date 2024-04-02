package me.wuzzyxy.dynamicmarket.commands;

public class CommandError {
    private final String[] errors;
    public CommandError(String[] errors) {
        this.errors = errors;
    }

    public String[] getErrors() {
        return errors;
    }
}
