package me.wuzzyxy.dynamicmarket.board

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.configs.HologramConfig
import me.wuzzyxy.dynamicmarket.configs.ScreenFrame
import me.wuzzyxy.dynamicmarket.configs.ScreenLine
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.MarketReport
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent
import java.util.UUID
import kotlin.math.max

/***
 * Owns every placed holo board: loads screens (holograms.yml) and placements (boards.yml),
 * spawns/tears down their entities, and runs the two repeating tasks that keep them live.
 * There is no reconciliation loop beyond that — content is pushed on its own timer, frames
 * cycle on theirs, same "push after state changes, plus a few repeating tasks for what's
 * genuinely time-driven" shape as MarketReport/MarketDatabaseHandler.
 */
class HoloBoardManager(private val plugin: DynamicMarket, private val report: MarketReport) : Listener {

    private val keys = BoardKeys(plugin)
    private val store = BoardStore(plugin)
    private var hologramConfig = HologramConfig(plugin)

    /***
     * Fresh per construction/reload. No board UUIDs are persisted (boards.yml only stores
     * where a board goes, not what it last spawned as), so nothing is ever reused across a
     * restart or reload — every board is respawned fresh, and this token is what lets a
     * dormant chunk's leftover entities be told apart from the set that just spawned.
     */
    private var sessionToken = UUID.randomUUID()

    private val boards = LinkedHashMap<String, HoloBoard>()
    private val pinnedChunks = HashMap<String, Chunk>()

    private var contentTask = NO_TASK
    private var frameTask = NO_TASK

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        spawnAll()
    }

    val screenNames: List<String> get() = hologramConfig.screens.keys.sorted()

    fun listBoards(): List<Pair<String, String>> = boards.values.map { it.name to it.screenName }

    fun placeBoard(name: String, screenName: String, location: Location, yaw: Float): String? {
        if (boards.containsKey(name)) return "A board named '$name' already exists"
        val screen = hologramConfig.screens[screenName] ?: return "Unknown screen '$screenName'"
        if (location.world == null) return "That location has no world"

        reconcile(location, name)
        val board = HoloBoard(name, screenName, screen, location.clone(), snapYaw(yaw))
        board.spawn(keys, sessionToken)
        pinChunk(board)
        render(board)
        boards[name] = board
        persist()
        return null
    }

    fun removeBoard(name: String): Boolean {
        val board = boards.remove(name) ?: return false
        board.destroy()
        pinnedChunks.remove(name)?.removePluginChunkTicket(plugin)
        persist()
        return true
    }

    fun moveBoard(name: String, location: Location, yaw: Float): Boolean {
        val board = boards[name] ?: return false
        if (location.world == null) return false

        pinnedChunks.remove(name)?.removePluginChunkTicket(plugin)
        board.moveTo(location.clone(), snapYaw(yaw))
        pinChunk(board)
        render(board)
        persist()
        return true
    }

    /***
     * Full teardown + config/session reload, mirroring the reference plugin's
     * teardown -> reloadConfig -> respawn sequence: nothing here is safe to mutate in place
     * because screens (and therefore slot counts/frame content) can change shape entirely.
     */
    fun reload() {
        teardown()
        hologramConfig = HologramConfig(plugin)
        sessionToken = UUID.randomUUID()
        spawnAll()
    }

    fun teardownAll() {
        teardown()
    }

    private fun teardown() {
        stopTasks()
        boards.values.forEach { it.destroy() }
        boards.clear()
        pinnedChunks.values.forEach { it.removePluginChunkTicket(plugin) }
        pinnedChunks.clear()
    }

    private fun spawnAll() {
        for (placed in store.load()) {
            val screen = hologramConfig.screens[placed.screen]
            if (screen == null) {
                plugin.logger.warning("boards.yml: board '${placed.name}' references unknown screen '${placed.screen}', skipping it")
                continue
            }
            val world = Bukkit.getWorld(placed.world)
            if (world == null) {
                plugin.logger.warning("boards.yml: board '${placed.name}' is on unloaded/unknown world '${placed.world}', skipping it")
                continue
            }

            val location = Location(world, placed.x, placed.y, placed.z)
            reconcile(location, placed.name)

            val board = HoloBoard(placed.name, placed.screen, screen, location, placed.yaw)
            board.spawn(keys, sessionToken)
            pinChunk(board)
            render(board)
            boards[placed.name] = board
        }
        startTasks()
    }

    /***
     * No board UUIDs survive a restart, so anything already sitting where this board is about
     * to spawn is leftover from a previous run — same rationale as the reference plugin's
     * `reconcileOnLoad`. Radius is generous enough to catch every slot of any screen shipped
     * or reasonably authored; it only ever removes entities tagged with this exact board name.
     */
    private fun reconcile(location: Location, name: String) {
        val world = location.world ?: return
        world.getNearbyEntities(location, RECONCILE_RADIUS, RECONCILE_RADIUS, RECONCILE_RADIUS)
            .filter { it.boardName(keys) == name }
            .forEach { it.remove() }
    }

    /***
     * Hard Entity references die permanently on chunk unload (nothing re-acquires them), so a
     * board's chunk is pinned for as long as it exists — same reasoning as the reference
     * plugin's chunk tickets, just one chunk since a board is a single free-standing wall.
     */
    private fun pinChunk(board: HoloBoard) {
        val chunk = board.chunk
        chunk.addPluginChunkTicket(plugin)
        pinnedChunks[board.name] = chunk
    }

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        for (entity in event.entities) {
            val token = entity.boardSessionToken(keys) ?: continue
            if (token != sessionToken.toString()) entity.remove()
        }
    }

    private fun startTasks() {
        stopTasks()
        val contentTicks = max(1, plugin.pluginConfig.REPORT_REFRESH_SECONDS) * 20L
        contentTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, ::renderAll, contentTicks, contentTicks)
        frameTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, ::advanceFrames, FRAME_TICK_RATE, FRAME_TICK_RATE)
    }

    private fun stopTasks() {
        Bukkit.getScheduler().cancelTask(contentTask)
        Bukkit.getScheduler().cancelTask(frameTask)
    }

    private fun renderAll() {
        boards.values.forEach(::render)
    }

    private fun advanceFrames() {
        for (board in boards.values) {
            if (board.tick(FRAME_TICK_RATE.toInt())) {
                board.advanceFrame(::frameHasData, ::frameComponent)
            }
        }
    }

    private fun render(board: HoloBoard) {
        board.renderCurrent(::frameHasData, ::frameComponent)
    }

    /***
     * A frame with no sourced lines at all (pure static decor) always counts as having data —
     * it doesn't depend on anything. Otherwise it needs at least one sourced line with real
     * data; a frame where every sourced line is empty (e.g. a "most traded" page when nothing
     * has traded yet) is skipped rather than shown with nothing but its static title and a
     * row of filler lines.
     */
    private fun frameHasData(frame: ScreenFrame): Boolean {
        val sourced = frame.lines.filterIsInstance<ScreenLine.Sourced>()
        return sourced.isEmpty() || sourced.any(::lineHasData)
    }

    private fun lineHasData(line: ScreenLine.Sourced): Boolean = when (line.source) {
        "mover" -> report.moverData(line.direction, line.category, line.rank) != null
        "top-mover" -> report.topMoverData(line.category, line.rank) != null
        "category-summary" -> report.summaryData(line.category) != null
        "sentiment" -> (report.summaryData(line.category)?.trackedCount ?: 0) > 0
        "volume-leader" -> report.volumeData(line.category, line.rank) != null
        else -> false
    }

    /***
     * Builds the WHOLE frame as one newline-joined MiniMessage string and deserializes it in a
     * single pass, rather than parsing each line into its own Component and stitching them
     * together with Component.newline() — composing many small parsed Components that way is
     * what was producing doubled line breaks on the entity. One combined parse means the
     * newlines the client sees are exactly the literal '\n' characters put in here, no more.
     * Lines with no data are dropped rather than joined in as an empty row — see textFor.
     */
    private fun frameComponent(frame: ScreenFrame): Component {
        val lines = frame.lines.mapNotNull { line -> textFor(line) }
        return MINI.deserialize(lines.joinToString("\n"))
    }

    /***
     * Sourced lines pull raw data straight off MarketReport's last refresh() snapshot (never
     * touches the database) and substitute it into the line's own template if it set one,
     * falling back to the matching report.* default in config.yml otherwise — so a line is
     * fully configurable in holograms.yml without having to touch the global default every
     * other board still uses. Static lines are admin-authored MiniMessage as-is, including an
     * intentionally blank `""` used as a spacer. A sourced line with nothing to show returns
     * null so frameComponent drops the row entirely (not settings.empty — that's for contexts
     * with a fixed number of slots to fill, like the GUI; a holo board just shows fewer rows).
     * Substituted values are escaped so an item/category name can never itself be read as a tag.
     */
    private fun textFor(line: ScreenLine): String? = when (line) {
        is ScreenLine.Static -> line.text
        is ScreenLine.Sourced -> when (line.source) {
            "mover" -> moverText(line)
            "top-mover" -> topMoverText(line)
            "category-summary" -> categorySummaryText(line)
            "sentiment" -> sentimentText(line)
            "volume-leader" -> volumeLeaderText(line)
            else -> null
        }
    }

    private fun moverText(line: ScreenLine.Sourced): String? {
        val settings = report.settings
        val mover = report.moverData(line.direction, line.category, line.rank) ?: return null

        val template = line.template ?: if (mover.change >= 0) settings.moverUp else settings.moverDown
        return template
            .replace("<item>", escape(prettyItemName(mover.item)))
            .replace("<change>", MarketReport.formatChange(mover.change))
    }

    /***
     * Same rendering as moverText, but the mover's sign isn't known until now (it's ranked by
     * magnitude, not a fixed direction), so this picks between two templates — line.up/down if
     * set, else the same settings.moverUp/moverDown a direction-locked `mover` line would use.
     */
    private fun topMoverText(line: ScreenLine.Sourced): String? {
        val settings = report.settings
        val mover = report.topMoverData(line.category, line.rank) ?: return null

        val template = if (mover.change >= 0) (line.up ?: settings.moverUp) else (line.down ?: settings.moverDown)
        return template
            .replace("<item>", escape(prettyItemName(mover.item)))
            .replace("<change>", MarketReport.formatChange(mover.change))
    }

    private fun categorySummaryText(line: ScreenLine.Sourced): String? {
        val settings = report.settings
        val summary = report.summaryData(line.category) ?: return null

        return (line.template ?: settings.categorySummary)
            .replace("<category>", escape(line.category))
            .replace("<avg_change>", MarketReport.formatChange(summary.avgChange))
            .replace("<up_count>", summary.upCount.toString())
            .replace("<down_count>", summary.downCount.toString())
            .replace("<tracked_count>", summary.trackedCount.toString())
    }

    private fun sentimentText(line: ScreenLine.Sourced): String? {
        val settings = report.settings
        val summary = report.summaryData(line.category)?.takeIf { it.trackedCount > 0 } ?: return null

        val template = when {
            summary.upPercent >= settings.sentimentThreshold -> line.bullish ?: settings.sentimentBullish
            summary.downPercent >= settings.sentimentThreshold -> line.bearish ?: settings.sentimentBearish
            else -> line.mixed ?: settings.sentimentMixed
        }
        return template
            .replace("<category>", escape(line.category))
            .replace("<up_percent>", MarketReport.formatChange(summary.upPercent))
            .replace("<down_percent>", MarketReport.formatChange(summary.downPercent))
            .replace("<up_count>", summary.upCount.toString())
            .replace("<down_count>", summary.downCount.toString())
            .replace("<tracked_count>", summary.trackedCount.toString())
    }

    private fun volumeLeaderText(line: ScreenLine.Sourced): String? {
        val settings = report.settings
        val leader = report.volumeData(line.category, line.rank) ?: return null

        return (line.template ?: settings.volumeLeader)
            .replace("<item>", escape(prettyItemName(leader.item)))
            .replace("<units>", leader.units.toString())
            .replace("<bought>", leader.bought.toString())
            .replace("<sold>", leader.sold.toString())
    }

    private fun escape(value: String): String = value.replace("<", "\\<")

    private fun persist() {
        val placements = boards.values.map {
            PlacedBoard(it.name, it.screenName, it.location.world!!.name, it.location.x, it.location.y, it.location.z, it.yaw)
        }
        store.save(placements)
    }

    private companion object {
        const val NO_TASK = -1
        const val RECONCILE_RADIUS = 8.0
        const val FRAME_TICK_RATE = 20L

        val MINI: MiniMessage = MiniMessage.miniMessage()
    }
}
