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
    private lateinit var report: MarketReport

    @BeforeEach
    fun setUp() {
        report = MarketReport(SETTINGS, { items }, stubDatabase(), PriceHandler(0.85))
    }

    /***
     * Only getPricesAt is ever called; the rest of Database would be 25 empty overrides.
     */
    private fun stubDatabase(): Database {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "getPricesAt") pricesBefore else null
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

    private companion object {
        val SETTINGS = ReportSettings(
            24, 0.1,
            "<green>UP <item> <change>", "<red>DOWN <item> <change>",
            "<green>^", "<red>v", "<gray>-", "<dark_gray>quiet",
        )
    }
}
