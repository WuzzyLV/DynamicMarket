package me.wuzzyxy.dynamicmarket.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.wuzzyxy.dynamicmarket.DynamicMarket;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/***
 * The report placeholders differ only in which method they call, so they share one class
 * instead of a file each. Buy/SellPriceExpansion predate this and are left alone.
 */
public class MarketExpansion extends PlaceholderExpansion {
    private final DynamicMarket plugin;
    private final String identifier;
    private final Function<String[], String> resolver;

    public MarketExpansion(DynamicMarket plugin, String identifier, Function<String[], String> resolver) {
        this.plugin = plugin;
        this.identifier = identifier;
        this.resolver = resolver;
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Wuzzy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return resolver.apply(params.split(","));
    }
}
