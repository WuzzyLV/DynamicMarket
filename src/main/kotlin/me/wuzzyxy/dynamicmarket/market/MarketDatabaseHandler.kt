package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.database.Database
import org.bukkit.Bukkit

class MarketDatabaseHandler(
    private val manager: MarketManager,
    private val database: Database,
    private val plugin: DynamicMarket,
) {

    /***
     * Null until the first push lands, which is during startup — nothing but /dmarket
     * can read it before then.
     */
    var lastPushTime: Long? = null
        private set

    private var pushTask = NO_TASK
    private var snapshotTask = NO_TASK

    init {
        startTasks()
    }

    /***
     * Both intervals come from config.yml, so a reload has to tear the old timers down —
     * otherwise the new ones just run alongside them.
     */
    fun restartTasks() {
        Bukkit.getScheduler().cancelTask(pushTask)
        Bukkit.getScheduler().cancelTask(snapshotTask)
        startTasks()
    }

    private fun startTasks() {
        pushTask = startPushTask()
        snapshotTask = startSnapshotTask()
    }

    /***
     * A failed write leaves the previous snapshot in place. Half the point of persisting
     * is that placeholders keep quoting the last known prices when MySQL is unreachable.
     */
    fun pushItems() {
        val pushed = database.setAllItems(manager.workingItems) ?: return

        manager.persistedItems.clear()
        manager.persistedItems.addAll(pushed)
        lastPushTime = System.currentTimeMillis()
    }

    private fun startPushTask(): Int =
        Bukkit.getServer().scheduler.scheduleSyncRepeatingTask(
            plugin,
            ::pushItems,
            0L,
            plugin.pluginConfig.PUSH_INTERVAL * 20L,
        )

    private fun startSnapshotTask(): Int {
        val ticks = plugin.pluginConfig.HISTORY_SNAPSHOT_MINUTES * 60L * 20L
        if (ticks <= 0) return NO_TASK

        // First one lands shortly after boot rather than a whole interval later, so a new
        // install has something to compare against instead of an empty report all day.
        return Bukkit.getServer().scheduler.scheduleSyncRepeatingTask(
            plugin,
            {
                database.snapshotHistory()
                database.pruneHistory(plugin.pluginConfig.HISTORY_RETENTION_DAYS)
            },
            200L,
            ticks,
        )
    }

    private companion object {
        const val NO_TASK = -1
    }
}
