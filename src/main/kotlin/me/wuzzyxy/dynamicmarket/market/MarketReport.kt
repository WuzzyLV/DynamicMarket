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

    private data class Mover(val item: String, val change: Double)

    private var changeByItem: Map<String, Double> = emptyMap()
    private var risers: Map<String, List<Mover>> = emptyMap()
    private var fallers: Map<String, List<Mover>> = emptyMap()

    fun refresh() {
        val before = database.getPricesAt(settings.windowHours) ?: return

        val changes = HashMap<String, Double>()
        val up = HashMap<String, MutableList<Mover>>()
        val down = HashMap<String, MutableList<Mover>>()

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
        }

        up.values.forEach { movers -> movers.sortByDescending(Mover::change) }
        down.values.forEach { movers -> movers.sortBy(Mover::change) }

        changeByItem = changes
        risers = up
        fallers = down
    }

    private fun file(into: MutableMap<String, MutableList<Mover>>, category: String, mover: Mover) {
        into.getOrPut(category) { mutableListOf() }.add(mover)
        into.getOrPut(ALL) { mutableListOf() }.add(mover)
    }

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

        private fun formatChange(change: Double): String = String.format(Locale.ROOT, "%.1f", change)
    }
}
