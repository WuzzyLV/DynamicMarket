package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.database.Database
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.util.MiniMessageText
import org.bukkit.Bukkit
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/***
 * Random price shocks. An event fires by nudging the targeted item(s)' net position the
 * same way a trade would (see MarketItem.applyShock) — no separate "active effect" state
 * to expire, since the price impact just decays on the item's own half-life like any
 * other net position change.
 *
 * What IS tracked is a small in-memory (and DB-backed, for restart) log of recently fired
 * events, purely so the GUI can ask "is anything still visibly moving prices around here"
 * and show a badge for it. Whether a fired event still counts as "visible" is computed
 * live from (item, delta, triggeredAt) — see remainingPercents — never from a stored price.
 */
class MarketEventManager(
    private val plugin: DynamicMarket,
    private val database: Database,
    private val items: () -> List<MarketItem>,
) {

    var definitions: List<MarketEventDefinition> = plugin.eventConfig.getAllDefinitions()
        private set

    private val recent = mutableListOf<StoredMarketEvent>()

    /*** Survives the pruning [recent] gets, so /dmarket event last can still answer for a shock too faded to badge. */
    private var lastFired: StoredMarketEvent? = null

    private val random = Random()
    private var cooldownUntil: Long = 0L
    private var triggerTask = NO_TASK

    init {
        loadRecent()
        startTask()
    }

    fun reload() {
        definitions = plugin.eventConfig.getAllDefinitions()
        Bukkit.getScheduler().cancelTask(triggerTask)
        triggerTask = NO_TASK
        startTask()
    }

    /*** Restores the recent-events log from the DB so a restart doesn't drop a still-visible badge. */
    private fun loadRecent() {
        val maxHours = plugin.pluginConfig.EVENTS_MAX_DISPLAY_HOURS
        val since = if (maxHours > 0) System.currentTimeMillis() - maxHours * 3_600_000L else 0L
        database.getRecentEvents(since)?.let(recent::addAll)
    }

    private fun startTask() {
        if (!plugin.pluginConfig.EVENTS_ENABLED) return
        val ticks = max(1, plugin.pluginConfig.EVENTS_CHECK_INTERVAL_SECONDS) * 20L
        triggerTask = Bukkit.getServer().scheduler.scheduleSyncRepeatingTask(plugin, ::tick, ticks, ticks)
    }

    private fun tick() {
        if (System.currentTimeMillis() < cooldownUntil) return
        if (random.nextDouble() * 100 >= plugin.pluginConfig.EVENTS_CHANCE_PERCENT) return

        val working = items()
        val definition = weightedPick(definitions.filter { eligiblePool(it, working) != null }) ?: return
        fire(definition, working)
    }

    /*** Bypasses the chance roll and cooldown, but not eligibility — used by /dmarket event trigger. */
    fun triggerNow(definitionId: String?): StoredMarketEvent? {
        val working = items()
        val definition = if (definitionId != null) {
            definitions.firstOrNull { it.id == definitionId } ?: return null
        } else {
            weightedPick(definitions.filter { eligiblePool(it, working) != null }) ?: return null
        }
        return fire(definition, working)
    }

    fun lastEvent(): StoredMarketEvent? = lastFired ?: recent.maxByOrNull(StoredMarketEvent::triggeredAt)

    private fun fire(definition: MarketEventDefinition, working: List<MarketItem>): StoredMarketEvent? {
        val pool = eligiblePool(definition, working) ?: return null
        val pick = pool[random.nextInt(pool.size)]

        val (targetItems, targetLabel) = when (definition.scope) {
            EventScope.GLOBAL -> working to null
            EventScope.CATEGORY -> working.filter { it.category == pick } to pick
            EventScope.ITEM -> working.filter { it.name == pick } to pick
        }

        val magnitude = definition.multiplierMin + random.nextDouble() * (definition.multiplierMax - definition.multiplierMin)
        // direction: both shares one range for both signs (author it >1; bust is its reciprocal).
        // direction: boom/bust is already pinned, so its range IS the final multiplier — a bust
        // definition is meant to be written as e.g. 0.5-0.7 directly, not inverted again here.
        val direction = when (definition.direction) {
            EventDirection.BOTH -> if (random.nextBoolean()) EventDirection.BOOM else EventDirection.BUST
            else -> definition.direction
        }
        val multiplier = if (definition.direction == EventDirection.BOTH && direction == EventDirection.BUST) {
            1.0 / magnitude
        } else {
            magnitude
        }

        val deltas = LinkedHashMap<String, Double>()
        for (item in targetItems) {
            if (item.k == 0.0) continue
            val delta = ln(multiplier) / item.k
            item.applyShock(delta)
            deltas[item.name] = delta
        }
        if (deltas.isEmpty()) return null

        val triggeredAt = System.currentTimeMillis()
        val eventId = database.recordEvent(
            definition.id, definition.scope.name.lowercase(), targetLabel,
            direction.name.lowercase(), multiplier, triggeredAt, deltas,
        ) ?: -triggeredAt // persistence failed — still shown for the rest of this run, just won't survive a restart

        val stored = StoredMarketEvent(
            eventId, definition.id, definition.scope.name.lowercase(), targetLabel,
            direction.name.lowercase(), multiplier, triggeredAt, deltas,
        )
        // Before the append, never after — a shock too small to badge would otherwise be swept
        // straight back out. Here as well as in the lookups so a server nobody is browsing
        // doesn't accumulate faded events for its whole uptime.
        settleRecent()
        recent += stored
        lastFired = stored
        cooldownUntil = triggeredAt + plugin.pluginConfig.EVENTS_COOLDOWN_MINUTES * 60_000L
        broadcast(definition, targetLabel, definition.scope, (multiplier - 1) * 100)
        return stored
    }

    /*** The pool a definition can roll a target from right now, or null if it has nothing eligible. */
    private fun eligiblePool(definition: MarketEventDefinition, working: List<MarketItem>): List<String>? =
        when (definition.scope) {
            EventScope.GLOBAL -> if (working.isNotEmpty()) GLOBAL_POOL else null
            EventScope.CATEGORY -> definition.targets.filter { cat -> working.any { it.category == cat } }.ifEmpty { null }
            EventScope.ITEM -> definition.targets.filter { name -> working.any { it.name == name } }.ifEmpty { null }
        }

    private fun weightedPick(candidates: List<MarketEventDefinition>): MarketEventDefinition? {
        val totalWeight = candidates.sumOf(MarketEventDefinition::weight)
        if (totalWeight <= 0) return null
        var roll = random.nextInt(totalWeight)
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0) return candidate
        }
        return candidates.lastOrNull()
    }

    private fun broadcast(definition: MarketEventDefinition, targetLabel: String?, scope: EventScope, originalPercent: Double) {
        if (!plugin.pluginConfig.EVENTS_BROADCAST || definition.message.isBlank()) return
        val tokens = labelTokens(scope, targetLabel, originalPercent)
        Bukkit.broadcastMessage(MiniMessageText.render(definition.message.fill(tokens)))
    }

    /***
     * The still-visible badge lines for whatever most recently fired event touches this
     * category — an item-scope event whose item lives here, a category-scope event
     * targeting it directly, or a global one. Empty when nothing qualifies.
     */
    fun activeEventLines(category: String): List<String> {
        val byName = settleRecent()
        return recent.asSequence()
            .sortedByDescending(StoredMarketEvent::triggeredAt)
            .firstOrNull { touchesCategory(it, category, byName) && isStillVisible(it, byName) }
            ?.let { renderBadge(it, byName) }
            ?: emptyList()
    }

    /*** Narrowed to events whose target set includes this specific item — for the category-screen tile badge. */
    fun activeEventLines(item: MarketItem): List<String> {
        val byName = settleRecent()
        return recent.asSequence()
            .sortedByDescending(StoredMarketEvent::triggeredAt)
            .firstOrNull { item.name in it.itemDeltas && isStillVisible(it, byName) }
            ?.let { renderBadge(it, byName) }
            ?: emptyList()
    }

    /***
     * Drops the events that can never show a badge again, and hands back the name lookup every
     * caller below needs. Both are here because this runs per item tile per redraw: keeping dead
     * events meant the scan walked further the longer the server had been up, and rebuilding the
     * lookup inside remainingPercents meant a map of every market item per event per tile.
     *
     * Dropping is safe because visibility only ever decreases — the display window is a fixed
     * age and a shock's share of net decays monotonically — so nothing invisible now comes back.
     * A reload that lengthens max_display_hours or an item's half-life won't resurrect one,
     * which costs a badge nobody was looking at rather than anything to do with a price.
     */
    private fun settleRecent(): Map<String, MarketItem> {
        val byName = items().associateBy(MarketItem::name)
        recent.removeAll { !isStillVisible(it, byName) }
        return byName
    }

    private fun touchesCategory(
        stored: StoredMarketEvent,
        category: String,
        byName: Map<String, MarketItem>,
    ): Boolean = when (stored.scope) {
        "global" -> true
        "category" -> stored.target == category
        "item" -> stored.itemDeltas.keys.any { byName[it]?.category == category }
        else -> false
    }

    /***
     * `delta * 0.5^(elapsed / halfLifeHours)` is exactly how much of the shock is left —
     * decay is linear, so the shock's own contribution to net fades independently of
     * whatever real trading happened on the item since, at the same rate. Turned back into
     * a percentage the same way PriceHandler.getUnitPrice would read it.
     */
    private fun remainingPercents(stored: StoredMarketEvent, byName: Map<String, MarketItem>): List<Double> {
        val elapsedHours = (System.currentTimeMillis() - stored.triggeredAt) / 3_600_000.0
        return stored.itemDeltas.mapNotNull { (name, delta) ->
            val item = byName[name] ?: return@mapNotNull null
            val remainingDelta = if (item.halfLifeHours > 0) delta * 0.5.pow(elapsedHours / item.halfLifeHours) else delta
            (exp(item.k * remainingDelta) - 1) * 100
        }
    }

    private fun isStillVisible(stored: StoredMarketEvent, byName: Map<String, MarketItem>): Boolean {
        val maxHours = plugin.pluginConfig.EVENTS_MAX_DISPLAY_HOURS
        if (maxHours > 0 && System.currentTimeMillis() - stored.triggeredAt > maxHours * 3_600_000L) return false

        val percents = remainingPercents(stored, byName)
        if (percents.isEmpty()) return false
        return percents.maxOf(::abs) >= plugin.pluginConfig.EVENTS_REVERT_THRESHOLD
    }

    private fun renderBadge(stored: StoredMarketEvent, byName: Map<String, MarketItem>): List<String> {
        val definition = definitions.firstOrNull { it.id == stored.definitionId } ?: return emptyList()
        if (definition.badge.isEmpty()) return emptyList()

        val percent = remainingPercents(stored, byName).maxByOrNull(::abs) ?: return emptyList()
        val scope = EventScope.entries.firstOrNull { it.name.equals(stored.scope, ignoreCase = true) } ?: return emptyList()
        val tokens = labelTokens(scope, stored.target, percent)
        return definition.badge.map { it.fill(tokens) }
    }

    private fun labelTokens(scope: EventScope, targetLabel: String?, percent: Double): Map<String, String> {
        val itemLabel = if (scope == EventScope.ITEM) targetLabel?.let(::prettyItemName).orEmpty() else ""
        val categoryLabel = when (scope) {
            EventScope.CATEGORY -> targetLabel?.replaceFirstChar(Char::uppercaseChar).orEmpty()
            EventScope.ITEM -> items().firstOrNull { it.name == targetLabel }?.category
                ?.replaceFirstChar(Char::uppercaseChar).orEmpty()
            EventScope.GLOBAL -> ""
        }
        return mapOf(
            "item" to itemLabel,
            "category" to categoryLabel,
            "sign" to if (percent >= 0) "+" else "",
            "change" to MarketReport.formatChange(abs(percent)),
        )
    }

    private fun String.fill(tokens: Map<String, String>): String =
        tokens.entries.fold(this) { acc, (key, value) -> acc.replace("%$key%", value) }

    private companion object {
        const val NO_TASK = -1
        val GLOBAL_POOL = listOf("")
    }
}
