package me.wuzzyxy.dynamicmarket

import me.wuzzyxy.dynamicmarket.board.HoloBoardManager
import me.wuzzyxy.dynamicmarket.commands.DMarketCommand
import me.wuzzyxy.dynamicmarket.commands.MarketMenuCommand
import me.wuzzyxy.dynamicmarket.configs.EventConfig
import me.wuzzyxy.dynamicmarket.configs.ItemConfig
import me.wuzzyxy.dynamicmarket.configs.MenuConfig
import me.wuzzyxy.dynamicmarket.configs.PluginConfig
import me.wuzzyxy.dynamicmarket.configs.ShopConfig
import me.wuzzyxy.dynamicmarket.database.Database
import me.wuzzyxy.dynamicmarket.database.MySqlDatabase
import me.wuzzyxy.dynamicmarket.economy.VaultEconomyService
import me.wuzzyxy.dynamicmarket.gui.GuiManager
import me.wuzzyxy.dynamicmarket.items.ItemResolver
import me.wuzzyxy.dynamicmarket.market.MarketManager
import me.wuzzyxy.dynamicmarket.market.MarketTradeService
import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.invui.InvUI
import java.io.IOException
import java.sql.SQLException

class DynamicMarket : JavaPlugin() {

    lateinit var pluginConfig: PluginConfig
        private set

    lateinit var itemConfig: ItemConfig
        private set

    lateinit var eventConfig: EventConfig
        private set

    lateinit var shopConfig: ShopConfig
        private set

    lateinit var menuConfig: MenuConfig
        private set

    lateinit var marketManager: MarketManager
        private set

    lateinit var holoBoardManager: HoloBoardManager
        private set

    lateinit var guiManager: GuiManager
        private set

    private var database: Database? = null

    override fun onEnable() {
        // Must run before any Window is built, so it goes before anything else touches InvUI.
        InvUI.getInstance().setPlugin(this)

        saveDefaultConfig()
        // CONFIGS
        pluginConfig = PluginConfig(this)
        itemConfig = ItemConfig(this)
        eventConfig = EventConfig(this)
        shopConfig = ShopConfig(this)
        menuConfig = MenuConfig(this)

        val database = try {
            MySqlDatabase(this)
        } catch (failure: SQLException) {
            logger.severe("Failed to initialize Database!")
            failure.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        } catch (failure: IOException) {
            logger.severe("Failed to load SQL scripts!")
            failure.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }
        this.database = database

        marketManager = MarketManager(this, database)
        holoBoardManager = HoloBoardManager(this, marketManager.report)

        // SHOP — the /market GUI. Works without Vault/CraftEngine (menu still opens; trades
        // that need them are refused with a clear reason at click time, not silently missing).
        val craftEngineAvailable = server.pluginManager.getPlugin("CraftEngine") != null
        val resolver = ItemResolver(craftEngineAvailable)
        val economy = VaultEconomyService()
        val tradeService = MarketTradeService(marketManager, marketManager.priceHandler, economy, resolver)
        guiManager = GuiManager(this, marketManager, tradeService, economy, resolver)

        // COMMANDS
        getCommand("dmarket")?.setExecutor(DMarketCommand(this))
        val marketCommand = MarketMenuCommand(this)
        getCommand("market")?.let {
            it.setExecutor(marketCommand)
            it.tabCompleter = marketCommand
        }
    }

    /***
     * Trades since the last push only exist in memory, so without this a restart throws
     * away up to a whole push interval of them.
     */
    override fun onDisable() {
        if (::holoBoardManager.isInitialized) {
            holoBoardManager.teardownAll()
        }
        if (::marketManager.isInitialized) {
            marketManager.databaseHandler.pushItems()
        }
        database?.die()
    }

    //Statically coded cause only accessed once and less error-prone :)
    @Throws(IOException::class)
    fun getSQLScripts(): List<String> = listOf(
        readResource("sql/items.sql"),
        readResource("sql/item_history.sql"),
        readResource("sql/market_events.sql"),
        readResource("sql/market_event_items.sql"),
    )

    /***
     * Kept apart from the table scripts because the trigger is dropped and recreated on
     * every boot. CREATE TRIGGER IF NOT EXISTS would leave an old body in place forever.
     */
    @Throws(IOException::class)
    fun getTriggerScript(): String = readResource("sql/item_history_trigger.sql")

    @Throws(IOException::class)
    private fun readResource(path: String): String {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "$path is missing from the plugin jar"
        }
        return stream.use { it.readBytes().decodeToString() }
    }

    /***
     * reloadConfig() first, otherwise PluginConfig just re-reads Bukkit's cached copy and
     * nothing anyone edited on disk takes effect. items.yml is read straight off disk.
     */
    fun reload() {
        reloadConfig()
        pluginConfig = PluginConfig(this)
        itemConfig = ItemConfig(this)
        eventConfig = EventConfig(this)
        shopConfig = ShopConfig(this)
        menuConfig = MenuConfig(this)
        marketManager.reload()
        holoBoardManager.reload()
    }
}
