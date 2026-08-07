package me.wuzzyxy.dynamicmarket.configs;

/***
 * Just the report knobs, so MarketReport does not need a whole PluginConfig — which can
 * only be built from a live Bukkit config and would drag the server into every test.
 */
public record ReportSettings(
        int windowHours,
        double minChange,
        String moverUp,
        String moverDown,
        String trendUp,
        String trendDown,
        String trendFlat,
        String empty
) {}
