package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.configs.ReportSettings
import me.wuzzyxy.dynamicmarket.database.Database
import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class MarketReportTest {

    private val items = mutableListOf<MarketItem>()
    private val pricesBefore = mutableMapOf<String, Double>()
    private val volumesBefore = mutableMapOf<String, Pair<Long, Long>>()
    private lateinit var report: MarketReport

    @BeforeEach
    fun setUp() {
        report = MarketReport(SETTINGS, { items }, stubDatabase(), PriceHandler(0.85))
    }

    /***
     * Only getPricesAt/getVolumesAt are ever called; the rest of Database would be 20-odd
     * empty overrides.
     */
    private fun stubDatabase(): Database {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getPricesAt" -> pricesBefore
                "getVolumesAt" -> volumesBefore
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            Database::class.java.classLoader,
            arrayOf(Database::class.java),
            handler,
        ) as Database
    }

    /***
     * Sets up an item whose price has moved by the given percentage since the window opened.
     */
    private fun moved(name: String, category: String, percent: Double) {
        val item = MarketItem(name, 1.0, 0, 0, 0.1, 0.0001)
        item.category = category
        item.halfLifeHours = 0.0
        items.add(item)
        pricesBefore[name] = 1.0 / (1 + percent / 100)
    }

    /***
     * Sets up an item with a flat price (so movers/summary tests aren't disturbed) that bought
     * and sold the given units since the window opened.
     */
    private fun traded(name: String, category: String, bought: Long, sold: Long, boughtThen: Long, soldThen: Long) {
        val item = MarketItem(name, 1.0, bought, sold, 0.1, 0.0001)
        item.category = category
        item.halfLifeHours = 0.0
        items.add(item)
        pricesBefore[name] = 1.0
        volumesBefore[name] = boughtThen to soldThen
    }

    @Test
    fun ranksRisersHighestFirstAndFallersLowestFirst() {
        moved("sandstone", "stone", 12.0)
        moved("calcite", "stone", 30.0)
        moved("cobblestone", "terrain", -8.0)
        moved("stone", "terrain", -25.0)
        report.refresh()

        assertEquals("calcite", report.moverItem(arrayOf("up", "stone", "1")))
        assertEquals("sandstone", report.moverItem(arrayOf("up", "stone", "2")))
        assertEquals("stone", report.moverItem(arrayOf("down", "terrain", "1")))
        assertEquals("cobblestone", report.moverItem(arrayOf("down", "terrain", "2")))
    }

    @Test
    fun allBucketSpansEveryCategory() {
        moved("sandstone", "stone", 12.0)
        moved("green_dye", "dyes", 40.0)
        report.refresh()

        assertEquals("green_dye", report.moverItem(arrayOf("up", "all", "1")))
        assertEquals("sandstone", report.moverItem(arrayOf("up", "all", "2")))
    }

    @Test
    fun ranksPastTheEndAreEmptyRatherThanExploding() {
        moved("sandstone", "stone", 12.0)
        report.refresh()

        assertEquals("", report.moverItem(arrayOf("up", "stone", "2")))
        assertEquals("", report.moverItem(arrayOf("up", "stone", "0")))
        assertEquals("", report.moverItem(arrayOf("up", "nonsense", "1")))
        assertEquals("", report.moverItem(arrayOf("up", "stone", "x")))
        assertEquals("", report.moverItem(arrayOf("up", "stone")))
    }

    @Test
    fun movesBelowTheThresholdCountAsFlat() {
        moved("tuff", "stone", 0.02)
        report.refresh()

        assertEquals("", report.moverItem(arrayOf("up", "stone", "1")))
        assertTrue(report.itemTrend(arrayOf("tuff")).contains("-"))
    }

    @Test
    fun itemsWithNoHistoryAreSkippedRatherThanReadingAsInfinite() {
        val fresh = MarketItem("brand_new", 1.0, 0, 0, 0.1, 0.0001)
        fresh.category = "stone"
        items.add(fresh)
        report.refresh()

        assertEquals("", report.itemChange(arrayOf("brand_new")))
        assertEquals("", report.moverItem(arrayOf("up", "stone", "1")))
    }

    @Test
    fun linesRenderToLegacyColoursForDeluxeMenus() {
        moved("red_sand", "stone", 12.0)
        report.refresh()

        assertEquals("§aUP Red Sand 12.0", report.moverLine(arrayOf("up", "stone", "1")))
    }

    /***
     * The filler sits in a lore line next to real movers, so it has to come out styled
     * the same way rather than as raw tag text.
     */
    @Test
    fun theEmptyFillerIsStyledLikeEverythingElse() {
        report.refresh()

        assertEquals("§8quiet", report.moverLine(arrayOf("up", "wood", "1")))
    }

    /***
     * Same reason the multiplier is mutable: reload rewrites the settings in place, so the
     * next refresh has to pick them up.
     */
    @Test
    fun reloadedSettingsApplyOnTheNextRefresh() {
        moved("sandstone", "stone", 12.0)
        report.refresh()
        assertEquals("§aUP Sandstone 12.0", report.moverLine(arrayOf("up", "stone", "1")))

        report.settings = SETTINGS.copy(moverUp = "<red>NOW <item>")
        report.refresh()

        assertEquals("§cNOW Sandstone", report.moverLine(arrayOf("up", "stone", "1")))
    }

    @Test
    fun changeIsSignedOnTheWayDown() {
        moved("cobblestone", "terrain", -8.0)
        report.refresh()

        assertEquals("-8.0", report.moverChange(arrayOf("down", "terrain", "1")))
        assertEquals("-8.0", report.itemChange(arrayOf("cobblestone")))
    }

    @Test
    fun categorySummaryAveragesTheCategoryAndTracksUpDownCounts() {
        moved("sandstone", "stone", 10.0)
        moved("calcite", "stone", 20.0)
        report.refresh()

        assertEquals("§fstone avg 15.0 2/0/2", report.categorySummaryLine(arrayOf("stone")))
    }

    @Test
    fun categorySummaryIsEmptyForACategoryWithNothingTracked() {
        report.refresh()

        assertEquals("§8quiet", report.categorySummaryLine(arrayOf("nonsense")))
    }

    @Test
    fun sentimentIsBullishOnceUpPercentClearsTheThreshold() {
        moved("sandstone", "stone", 10.0)
        moved("calcite", "stone", 20.0)
        report.refresh()

        assertEquals("§aBULLISH 100.0", report.sentimentLine(arrayOf("stone")))
    }

    @Test
    fun sentimentIsBearishOnceDownPercentClearsTheThreshold() {
        moved("cobblestone", "terrain", -8.0)
        moved("stone", "terrain", -25.0)
        report.refresh()

        assertEquals("§cBEARISH 100.0", report.sentimentLine(arrayOf("terrain")))
    }

    @Test
    fun sentimentIsMixedWhenNeitherSideClearsTheThreshold() {
        moved("sandstone", "stone", 10.0)
        moved("cobblestone", "stone", -8.0)
        report.refresh()

        assertEquals("§eMIXED 1/1", report.sentimentLine(arrayOf("stone")))
    }

    /***
     * Percentages are a share of the movers, not of every tracked item — a couple of fallers
     * next to a pile of flat items is bearish, not diluted down to mixed just because most of
     * the category didn't move at all.
     */
    @Test
    fun sentimentIgnoresFlatItemsWhenComputingPercentages() {
        moved("cobblestone", "stone", -8.0)
        moved("basalt", "stone", -25.0)
        moved("tuff", "stone", 0.02)
        moved("granite", "stone", -0.05)
        report.refresh()

        assertEquals("§cBEARISH 100.0", report.sentimentLine(arrayOf("stone")))
    }

    @Test
    fun volumeLeadersRankByUnitsTradedInTheWindow() {
        traded("wheat", "food", bought = 100, sold = 50, boughtThen = 20, soldThen = 10)
        traded("carrot", "food", bought = 40, sold = 40, boughtThen = 30, soldThen = 30)
        report.refresh()

        assertEquals("§fWheat 120 (80/40)", report.volumeLeaderLine(arrayOf("food", "1")))
        assertEquals("§fCarrot 20 (10/10)", report.volumeLeaderLine(arrayOf("food", "2")))
        assertEquals("§8quiet", report.volumeLeaderLine(arrayOf("food", "3")))
    }

    @Test
    fun volumeLeaderFallsBackToCurrentTalliesWithNoSnapshotToCompareAgainst() {
        traded("wheat", "food", bought = 5, sold = 0, boughtThen = 5, soldThen = 0)
        report.refresh()

        assertEquals("§8quiet", report.volumeLeaderLine(arrayOf("food", "1")))
    }

    private companion object {
        val SETTINGS = ReportSettings(
            windowHours = 24,
            minChange = 0.1,
            moverUp = "<green>UP <item> <change>",
            moverDown = "<red>DOWN <item> <change>",
            trendUp = "<green>^",
            trendDown = "<red>v",
            trendFlat = "<gray>-",
            empty = "<dark_gray>quiet",
            categorySummary = "<white><category> avg <avg_change> <up_count>/<down_count>/<tracked_count>",
            sentimentBullish = "<green>BULLISH <up_percent>",
            sentimentBearish = "<red>BEARISH <down_percent>",
            sentimentMixed = "<yellow>MIXED <up_count>/<down_count>",
            sentimentThreshold = 60.0,
            volumeLeader = "<white><item> <units> (<bought>/<sold>)",
        )
    }
}
