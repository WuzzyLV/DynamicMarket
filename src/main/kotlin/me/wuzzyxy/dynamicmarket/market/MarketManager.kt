package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.database.Database
import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.bukkit.Bukkit
import kotlin.math.max

class MarketManager(private val plugin: DynamicMarket, private val database: Database) {

    val workingItems: MutableList<MarketItem> = mutableListOf()
    val persistedItems: MutableList<MarketItem> = mutableListOf()

    val databaseHandler: MarketDatabaseHandler
    val priceHandler: PriceHandler
    val report: MarketReport
    val eventManager: MarketEventManager

    private var reportTask = NO_TASK

    init {
        val stored = database.getAllItems()
        if (stored == null) {
            plugin.logger.severe("Failed to load items from database")
        } else {
            persistedItems.addAll(stored)
            stored.mapTo(workingItems) { it.clone() }
        }

        databaseHandler = MarketDatabaseHandler(this, database, plugin)
        priceHandler = PriceHandler(plugin.pluginConfig.SELL_MULTIPLIER)
        report = MarketReport(plugin.pluginConfig.reportSettings(), { workingItems }, database, priceHandler)

        reconcileWithConfig(plugin.itemConfig, this, plugin.logger)
        startReportTask()

        // Constructed last: it reads plugin.eventConfig and the just-reconciled workingItems.
        eventManager = MarketEventManager(plugin, database, { workingItems })
    }

    /***
     * Pushes first so nothing in flight is lost, then rewrites the knobs in place rather
     * than rebuilding anything. The registered placeholder expansions hold references to
     * this manager, its price handler and its report — replace those objects and the
     * placeholders keep serving the old ones for the rest of the server's life.
     */
    fun reload() {
        databaseHandler.pushItems()

        priceHandler.sellMultiplier = plugin.pluginConfig.SELL_MULTIPLIER
        report.settings = plugin.pluginConfig.reportSettings()

        reconcileWithConfig(plugin.itemConfig, this, plugin.logger)

        databaseHandler.restartTasks()
        startReportTask()
        eventManager.reload()
    }

    private fun startReportTask() {
        Bukkit.getScheduler().cancelTask(reportTask)
        val ticks = max(1, plugin.pluginConfig.REPORT_REFRESH_SECONDS) * 20L
        reportTask = Bukkit.getServer().scheduler.scheduleSyncRepeatingTask(plugin, report::refresh, 220L, ticks)
    }

    /***
     * Returns the working item object
     */
    fun addItem(definition: MarketItem): MarketItem? {
        if (getPersistedItem(definition.name) != null) return null

        val item = database.addItem(definition) ?: return null
        workingItems.add(item)
        persistedItems.add(item.clone())
        return item
    }

    fun getPersistedItem(name: String): MarketItem? = persistedItems.firstOrNull { it.name == name }

    fun getPersistedItem(item: MarketItem): MarketItem? = getPersistedItem(item.name)

    fun getWorkingItem(itemName: String): MarketItem? = workingItems.firstOrNull { it.name == itemName }

    fun removeItem(name: String): Boolean {
        val item = getPersistedItem(name) ?: return false

        persistedItems.remove(item)
        workingItems.remove(item)
        return database.removeItem(name)
    }

    fun removeItem(item: MarketItem): Boolean = removeItem(item.name)

    private companion object {
        const val NO_TASK = -1
    }
}
