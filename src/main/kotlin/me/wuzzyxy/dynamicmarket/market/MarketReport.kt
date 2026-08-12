package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.configs.ReportSettings
import me.wuzzyxy.dynamicmarket.database.Database
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/***
 * One ranked mover — exposed publicly (not just as a rendered string) so a caller that wants
 * to pick its own template per line, like the holo board, can format it however it likes.
 */
data class Mover(val item: String, val change: Double)

data class VolumeMover(val item: String, val units: Long, val bought: Long, val sold: Long)

data class CategorySummary(
    val avgChange: Double,
    val upCount: Int,
    val downCount: Int,
    val trackedCount: Int,
) {
    /***
     * Share of *movers* (items that cleared the flat threshold either way) — not of every
     * tracked item. A category that's mostly flat with a couple of fallers and zero risers is
     * bearish, not "mixed"; diluting against every unmoved item made a handful of one-sided
     * movers read as balanced just because most of the category didn't move at all.
     */
    private val moverCount: Int get() = upCount + downCount
    val upPercent: Double get() = if (moverCount == 0) 0.0 else upCount * 100.0 / moverCount
    val downPercent: Double get() = if (moverCount == 0) 0.0 else downCount * 100.0 / moverCount
}

/***
 * What moved and by how much, recomputed on a timer. DeluxeMenus resolves placeholders
 * on every menu refresh for every slot, so none of this can touch the database on the
 * way through — it all serves off the last snapshot.
 */
class MarketReport(
    var settings: ReportSettings,
    private val items: () -> List<MarketItem>,
    private val database: Database,
    private val priceHandler: PriceHandler,
) {

    val windowHours: Int get() = settings.windowHours

    /***
     * Running tally while a refresh pass is in progress. Kept separate from CategorySummary
     * so the public map only ever holds finished, immutable snapshots.
     */
    private class SummaryAccumulator {
        var trackedCount: Int = 0
        var sumChange: Double = 0.0
        var upCount: Int = 0
        var downCount: Int = 0
    }

    private var changeByItem: Map<String, Double> = emptyMap()
    private var risers: Map<String, List<Mover>> = emptyMap()
    private var fallers: Map<String, List<Mover>> = emptyMap()
    private var summaries: Map<String, CategorySummary> = emptyMap()
    private var volumeLeaders: Map<String, List<VolumeMover>> = emptyMap()

    fun refresh() {
        val before = database.getPricesAt(settings.windowHours) ?: return
        val volumesBefore = database.getVolumesAt(settings.windowHours) ?: emptyMap()

        val changes = HashMap<String, Double>()
        val up = HashMap<String, MutableList<Mover>>()
        val down = HashMap<String, MutableList<Mover>>()
        val summaryAcc = HashMap<String, SummaryAccumulator>()
        val volume = HashMap<String, MutableList<VolumeMover>>()

        for (item in items()) {
            val then = before[item.name] ?: continue
            if (then <= 0) continue

            val change = (priceHandler.getUnitPrice(item) - then) / then * 100
            changes[item.name] = change

            val mover = Mover(item.name, change)
            if (change >= settings.minChange) {
                file(up, item.category, mover)
            } else if (change <= -settings.minChange) {
                file(down, item.category, mover)
            }

            accumulate(summaryAcc, item.category, change)

            val (boughtThen, soldThen) = volumesBefore[item.name] ?: (item.boughtAmount to item.soldAmount)
            val units = max(0L, (item.boughtAmount - boughtThen)) + max(0L, (item.soldAmount - soldThen))
            if (units > 0) {
                fileVolume(
                    volume,
                    item.category,
                    VolumeMover(item.name, units, max(0L, item.boughtAmount - boughtThen), max(0L, item.soldAmount - soldThen)),
                )
            }
        }

        up.values.forEach { movers -> movers.sortByDescending(Mover::change) }
        down.values.forEach { movers -> movers.sortBy(Mover::change) }
        volume.values.forEach { movers -> movers.sortByDescending(VolumeMover::units) }

        changeByItem = changes
        risers = up
        fallers = down
        summaries = summaryAcc.mapValues { (_, acc) -> acc.toSummary() }
        volumeLeaders = volume
    }

    private fun file(into: MutableMap<String, MutableList<Mover>>, category: String, mover: Mover) {
        into.getOrPut(category) { mutableListOf() }.add(mover)
        into.getOrPut(ALL) { mutableListOf() }.add(mover)
    }

    private fun fileVolume(into: MutableMap<String, MutableList<VolumeMover>>, category: String, mover: VolumeMover) {
        into.getOrPut(category) { mutableListOf() }.add(mover)
        into.getOrPut(ALL) { mutableListOf() }.add(mover)
    }

    /***
     * Same up/down/flat boundary itemTrend already uses, so a category's up/down counts
     * agree with what %DMTrend_<item>% would show for any item in it.
     */
    private fun accumulate(acc: MutableMap<String, SummaryAccumulator>, category: String, change: Double) {
        addTo(acc, category, change)
        addTo(acc, ALL, change)
    }

    private fun addTo(acc: MutableMap<String, SummaryAccumulator>, key: String, change: Double) {
        val entry = acc.getOrPut(key) { SummaryAccumulator() }
        entry.trackedCount++
        entry.sumChange += change
        when {
            change >= settings.minChange -> entry.upCount++
            change <= -settings.minChange -> entry.downCount++
        }
    }

    private fun SummaryAccumulator.toSummary() = CategorySummary(sumChange / trackedCount, upCount, downCount, trackedCount)

    // %DMMover_<up|down>,<category>,<rank>%
    fun moverLine(params: Array<String>): String {
        val mover = mover(params) ?: return render(settings.empty)

        val template = if (mover.change >= 0) settings.moverUp else settings.moverDown
        return render(
            template,
            Placeholder.unparsed("item", prettyItemName(mover.item)),
            Placeholder.unparsed("change", formatChange(mover.change)),
        )
    }

    // %DMMoverItem_<up|down>,<category>,<rank>%
    fun moverItem(params: Array<String>): String = mover(params)?.item ?: ""

    // %DMMoverChange_<up|down>,<category>,<rank>%
    fun moverChange(params: Array<String>): String = mover(params)?.change?.let(::formatChange) ?: ""

    // %DMChange_<item>%
    fun itemChange(params: Array<String>): String {
        if (params.size != 1) return ""
        return changeByItem[params[0]]?.let(::formatChange) ?: ""
    }

    // %DMTrend_<item>%
    fun itemTrend(params: Array<String>): String {
        if (params.size != 1) return render(settings.empty)

        val change = changeByItem[params[0]]
        val template = when {
            change == null || abs(change) < settings.minChange -> settings.trendFlat
            change > 0 -> settings.trendUp
            else -> settings.trendDown
        }
        return render(template)
    }

    // %DMCategorySummary_<category>%
    fun categorySummaryLine(params: Array<String>): String {
        if (params.size != 1) return render(settings.empty)
        val summary = summaries[params[0]] ?: return render(settings.empty)

        return render(
            settings.categorySummary,
            Placeholder.unparsed("category", params[0]),
            Placeholder.unparsed("avg_change", formatChange(summary.avgChange)),
            Placeholder.unparsed("up_count", summary.upCount.toString()),
            Placeholder.unparsed("down_count", summary.downCount.toString()),
            Placeholder.unparsed("tracked_count", summary.trackedCount.toString()),
        )
    }

    // %DMSentiment_<category>%
    fun sentimentLine(params: Array<String>): String {
        if (params.size != 1) return render(settings.empty)
        val summary = summaries[params[0]] ?: return render(settings.empty)
        if (summary.trackedCount == 0) return render(settings.empty)

        val template = when {
            summary.upPercent >= settings.sentimentThreshold -> settings.sentimentBullish
            summary.downPercent >= settings.sentimentThreshold -> settings.sentimentBearish
            else -> settings.sentimentMixed
        }
        return render(
            template,
            Placeholder.unparsed("category", params[0]),
            Placeholder.unparsed("up_percent", formatChange(summary.upPercent)),
            Placeholder.unparsed("down_percent", formatChange(summary.downPercent)),
            Placeholder.unparsed("up_count", summary.upCount.toString()),
            Placeholder.unparsed("down_count", summary.downCount.toString()),
            Placeholder.unparsed("tracked_count", summary.trackedCount.toString()),
        )
    }

    // %DMVolume_<category>,<rank>%
    fun volumeLeaderLine(params: Array<String>): String {
        val leader = volumeLeader(params) ?: return render(settings.empty)
        return render(
            settings.volumeLeader,
            Placeholder.unparsed("item", prettyItemName(leader.item)),
            Placeholder.unparsed("units", leader.units.toString()),
            Placeholder.unparsed("bought", leader.bought.toString()),
            Placeholder.unparsed("sold", leader.sold.toString()),
        )
    }

    private fun volumeLeader(params: Array<String>): VolumeMover? {
        if (params.size != 2) return null

        val movers = volumeLeaders[params[0]] ?: return null
        val rank = params[1].toIntOrNull() ?: return null
        return movers.getOrNull(rank - 1)
    }

    /***
     * Raw-data twins of the *Line methods above, for a caller that wants to pick its own
     * template per call instead of the one fixed in settings — the holo board, which lets an
     * admin override wording/colour per line in holograms.yml rather than only globally.
     */
    fun moverData(direction: String, category: String, rank: Int): Mover? = mover(arrayOf(direction, category, rank.toString()))

    fun summaryData(category: String): CategorySummary? = summaries[category]

    fun volumeData(category: String, rank: Int): VolumeMover? = volumeLeader(arrayOf(category, rank.toString()))

    /***
     * The Nth biggest move in a category regardless of direction — risers and fallers merged
     * and ranked by |change|. Lets a board ask for "the 4 biggest movers" without reserving
     * fixed up/N and down/N slots that go blank whenever the market leans one way, which is
     * what asking separately for e.g. down rank 2 does when there's only one faller.
     */
    fun topMoverData(category: String, rank: Int): Mover? =
        (risers[category].orEmpty() + fallers[category].orEmpty())
            .sortedByDescending { abs(it.change) }
            .getOrNull(rank - 1)

    /***
     * Items with a price to compare against. Anything below the total means those items
     * have no usable history yet and cannot appear as movers whatever their price did.
     */
    fun trackedCount(): Int = changeByItem.size

    fun categories(): List<String> = items().map(MarketItem::category).distinct().sorted()

    /***
     * The actual movers in a bucket, capped at [limit] — never padded out to it. Lets a caller
     * (the shop GUI's home screen) size its layout off how many there really are instead of
     * reserving space for a fixed count that might not have anything to show.
     */
    fun movers(direction: String, category: String, limit: Int): List<Pair<String, Double>> {
        val bucket = if (direction.equals("down", ignoreCase = true)) fallers else risers
        val movers = bucket[category] ?: return emptyList()
        return movers.take(limit).map { it.item to it.change }
    }

    private fun mover(params: Array<String>): Mover? {
        if (params.size != 3) return null

        val bucket = if (params[0].equals("down", ignoreCase = true)) fallers else risers
        val movers = bucket[params[1]] ?: return null

        val rank = params[2].toIntOrNull() ?: return null
        return movers.getOrNull(rank - 1)
    }

    companion object {
        const val ALL: String = "all"

        private val MINI: MiniMessage = MiniMessage.miniMessage()
        private val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

        private fun render(template: String, vararg resolvers: TagResolver): String =
            LEGACY.serialize(MINI.deserialize(template, *resolvers))

        /*** Public: the holo board formats raw Mover/CategorySummary/VolumeMover data with this too. */
        fun formatChange(change: Double): String = String.format(Locale.ROOT, "%.1f", change)
    }
}
