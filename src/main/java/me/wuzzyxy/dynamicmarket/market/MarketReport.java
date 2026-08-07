package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.configs.ReportSettings;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/***
 * What moved and by how much, recomputed on a timer. DeluxeMenus resolves placeholders
 * on every menu refresh for every slot, so none of this can touch the database on the
 * way through — it all serves off the last snapshot.
 */
public class MarketReport {
    public static final String ALL = "all";

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Supplier<List<MarketItem>> items;
    private final Database database;
    private final PriceHandler priceHandler;
    private final ReportSettings settings;

    private Map<String, Double> changeByItem = Map.of();
    private Map<String, List<Mover>> risers = Map.of();
    private Map<String, List<Mover>> fallers = Map.of();

    private record Mover(String item, double change) {}

    public MarketReport(ReportSettings settings, Supplier<List<MarketItem>> items, Database database, PriceHandler priceHandler) {
        this.settings = settings;
        this.items = items;
        this.database = database;
        this.priceHandler = priceHandler;
    }

    public void refresh() {
        Map<String, Double> before = database.getPricesAt(settings.windowHours());
        if (before == null) return;

        Map<String, Double> changes = new HashMap<>();
        Map<String, List<Mover>> up = new HashMap<>();
        Map<String, List<Mover>> down = new HashMap<>();

        for (MarketItem item : items.get()) {
            Double then = before.get(item.getName());
            if (then == null || then <= 0) continue;

            double change = (priceHandler.getUnitPrice(item) - then) / then * 100;
            changes.put(item.getName(), change);

            Mover mover = new Mover(item.getName(), change);
            if (change >= settings.minChange()) {
                file(up, item.getCategory(), mover);
            } else if (change <= -settings.minChange()) {
                file(down, item.getCategory(), mover);
            }
        }

        up.values().forEach(movers -> movers.sort(Comparator.comparingDouble(Mover::change).reversed()));
        down.values().forEach(movers -> movers.sort(Comparator.comparingDouble(Mover::change)));

        changeByItem = changes;
        risers = up;
        fallers = down;
    }

    private static void file(Map<String, List<Mover>> into, String category, Mover mover) {
        into.computeIfAbsent(category, key -> new ArrayList<>()).add(mover);
        into.computeIfAbsent(ALL, key -> new ArrayList<>()).add(mover);
    }

    // %DMMover_<up|down>,<category>,<rank>%
    public String moverLine(String[] params) {
        Mover mover = mover(params);
        if (mover == null) return settings.empty();

        String template = mover.change() >= 0 ? settings.moverUp() : settings.moverDown();
        return LEGACY.serialize(MINI.deserialize(template,
                Placeholder.unparsed("item", pretty(mover.item())),
                Placeholder.unparsed("change", formatChange(mover.change()))));
    }

    // %DMMoverItem_<up|down>,<category>,<rank>%
    public String moverItem(String[] params) {
        Mover mover = mover(params);
        return mover == null ? "" : mover.item();
    }

    // %DMMoverChange_<up|down>,<category>,<rank>%
    public String moverChange(String[] params) {
        Mover mover = mover(params);
        return mover == null ? "" : formatChange(mover.change());
    }

    // %DMChange_<item>%
    public String itemChange(String[] params) {
        if (params.length != 1) return "";
        Double change = changeByItem.get(params[0]);
        return change == null ? "" : formatChange(change);
    }

    // %DMTrend_<item>%
    public String itemTrend(String[] params) {
        if (params.length != 1) return settings.empty();

        Double change = changeByItem.get(params[0]);
        String template;
        if (change == null || Math.abs(change) < settings.minChange()) {
            template = settings.trendFlat();
        } else {
            template = change > 0 ? settings.trendUp() : settings.trendDown();
        }
        return LEGACY.serialize(MINI.deserialize(template));
    }

    private Mover mover(String[] params) {
        if (params.length != 3) return null;

        List<Mover> movers = ("down".equalsIgnoreCase(params[0]) ? fallers : risers).get(params[1]);
        if (movers == null) return null;

        int rank;
        try {
            rank = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        return rank >= 1 && rank <= movers.size() ? movers.get(rank - 1) : null;
    }

    private static String formatChange(double change) {
        return String.format(Locale.ROOT, "%.1f", change);
    }

    /***
     * cobbled_deepslate reads badly in a lore line.
     */
    private static String pretty(String itemName) {
        StringBuilder pretty = new StringBuilder(itemName.length());
        for (String word : itemName.split("_")) {
            if (word.isEmpty()) continue;
            if (pretty.length() > 0) pretty.append(' ');
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return pretty.toString();
    }
}
