package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.configs.ReportSettings;
import me.wuzzyxy.dynamicmarket.database.Database;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketReportTest {

    private static final ReportSettings SETTINGS = new ReportSettings(
            24, 0.1,
            "<green>UP <item> <change>", "<red>DOWN <item> <change>",
            "<green>^", "<red>v", "<gray>-", "<dark_gray>quiet");

    private final List<MarketItem> items = new ArrayList<>();
    private final Map<String, Double> pricesBefore = new HashMap<>();
    private MarketReport report;

    @BeforeEach
    void setUp() {
        report = new MarketReport(SETTINGS, () -> items, stubDatabase(), new PriceHandler(0.85));
    }

    /***
     * Only getPricesAt is ever called; the rest of Database would be 25 empty overrides.
     */
    private Database stubDatabase() {
        InvocationHandler handler = (proxy, method, args) ->
                method.getName().equals("getPricesAt") ? pricesBefore : null;
        return (Database) Proxy.newProxyInstance(
                Database.class.getClassLoader(), new Class[]{Database.class}, handler);
    }

    /***
     * Sets up an item whose price has moved by the given percentage since the window opened.
     */
    private void moved(String name, String category, double percent) {
        MarketItem item = new MarketItem(name, 1.0, 0, 0, 0.1, 0.0001);
        item.setCategory(category);
        item.setHalfLifeHours(0);
        items.add(item);
        pricesBefore.put(name, 1.0 / (1 + percent / 100));
    }

    @Test
    void ranksRisersHighestFirstAndFallersLowestFirst() {
        moved("sandstone", "stone", 12);
        moved("calcite", "stone", 30);
        moved("cobblestone", "terrain", -8);
        moved("stone", "terrain", -25);
        report.refresh();

        assertEquals("calcite", report.moverItem(new String[]{"up", "stone", "1"}));
        assertEquals("sandstone", report.moverItem(new String[]{"up", "stone", "2"}));
        assertEquals("stone", report.moverItem(new String[]{"down", "terrain", "1"}));
        assertEquals("cobblestone", report.moverItem(new String[]{"down", "terrain", "2"}));
    }

    @Test
    void allBucketSpansEveryCategory() {
        moved("sandstone", "stone", 12);
        moved("green_dye", "dyes", 40);
        report.refresh();

        assertEquals("green_dye", report.moverItem(new String[]{"up", "all", "1"}));
        assertEquals("sandstone", report.moverItem(new String[]{"up", "all", "2"}));
    }

    @Test
    void ranksPastTheEndAreEmptyRatherThanExploding() {
        moved("sandstone", "stone", 12);
        report.refresh();

        assertEquals("", report.moverItem(new String[]{"up", "stone", "2"}));
        assertEquals("", report.moverItem(new String[]{"up", "stone", "0"}));
        assertEquals("", report.moverItem(new String[]{"up", "nonsense", "1"}));
        assertEquals("", report.moverItem(new String[]{"up", "stone", "x"}));
        assertEquals("", report.moverItem(new String[]{"up", "stone"}));
    }

    @Test
    void movesBelowTheThresholdCountAsFlat() {
        moved("tuff", "stone", 0.02);
        report.refresh();

        assertEquals("", report.moverItem(new String[]{"up", "stone", "1"}));
        assertTrue(report.itemTrend(new String[]{"tuff"}).contains("-"));
    }

    @Test
    void itemsWithNoHistoryAreSkippedRatherThanReadingAsInfinite() {
        MarketItem fresh = new MarketItem("brand_new", 1.0, 0, 0, 0.1, 0.0001);
        fresh.setCategory("stone");
        items.add(fresh);
        report.refresh();

        assertEquals("", report.itemChange(new String[]{"brand_new"}));
        assertEquals("", report.moverItem(new String[]{"up", "stone", "1"}));
    }

    @Test
    void linesRenderToLegacyColoursForDeluxeMenus() {
        moved("red_sand", "stone", 12);
        report.refresh();

        assertEquals("§aUP Red Sand 12.0", report.moverLine(new String[]{"up", "stone", "1"}));
    }

    /***
     * The filler sits in a lore line next to real movers, so it has to come out styled
     * the same way rather than as raw tag text.
     */
    @Test
    void theEmptyFillerIsStyledLikeEverythingElse() {
        report.refresh();

        assertEquals("§8quiet", report.moverLine(new String[]{"up", "wood", "1"}));
    }

    @Test
    void changeIsSignedOnTheWayDown() {
        moved("cobblestone", "terrain", -8);
        report.refresh();

        assertEquals("-8.0", report.moverChange(new String[]{"down", "terrain", "1"}));
        assertEquals("-8.0", report.itemChange(new String[]{"cobblestone"}));
    }
}
