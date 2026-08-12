package me.wuzzyxy.dynamicmarket.database

import com.mysql.cj.jdbc.MysqlDataSource
import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.items.MarketItem
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

class MySqlDatabase @Throws(SQLException::class, IOException::class) constructor(
    private val plugin: DynamicMarket,
) : Database {

    private val config = plugin.pluginConfig
    private val logger = plugin.logger
    private var connection: Connection? = null

    init {
        connection()
        initializeDatabase()
    }

    private fun connection(): Connection = connection ?: openConnection().also { connection = it }

    private fun openConnection(): Connection {
        val dataSource = MysqlDataSource()
        dataSource.setServerName(config.HOST)
        dataSource.setPort(config.PORT)
        dataSource.setDatabaseName(config.DATABASE)
        dataSource.setUser(config.USERNAME)
        dataSource.setPassword(config.PASSWORD)
        // Connector/J defaults both of these to 0, which means "wait forever". We connect from
        // onEnable on the main thread, so a host that swallows packets instead of refusing them
        // wedges the entire server boot rather than failing the plugin.
        dataSource.setConnectTimeout(config.CONNECT_TIMEOUT_MS)
        dataSource.setSocketTimeout(config.SOCKET_TIMEOUT_MS)

        logger.info(
            "Connecting to ${config.HOST}:${config.PORT}/${config.DATABASE}" +
                " as ${config.USERNAME}, giving up after ${config.CONNECT_TIMEOUT_MS}ms"
        )

        val start = System.currentTimeMillis()
        try {
            return dataSource.connection
        } catch (failure: SQLException) {
            logger.severe(
                "Gave up after ${System.currentTimeMillis() - start}ms" +
                    " (SQLState ${failure.sqlState}, vendor code ${failure.errorCode}): ${failure.message}"
            )
            logger.severe(diagnose(failure))
            throw failure
        }
    }

    /***
     * The driver folds every network problem into one CommunicationsException, so the stack trace
     * alone never says whether the host is wrong, the port is closed, or the credentials are.
     */
    private fun diagnose(failure: SQLException): String {
        when (failure.sqlState ?: "") {
            "28000" -> return "Wrong username or password for '${config.USERNAME}'."
            "42000" -> return "Connected, but database '${config.DATABASE}' does not exist or the user cannot see it."
            "08S01" -> Unit
            else -> return "Check mysql.* in config.yml."
        }
        if (failure.hasCause<ConnectException>()) {
            return "Refused: something answered at ${config.HOST}:${config.PORT} but nothing is listening." +
                " Is mysqld running, and is the port right?"
        }
        if (failure.hasCause<SocketTimeoutException>()) {
            return "Timed out with no reply from ${config.HOST}:${config.PORT}." +
                " Packets are being dropped - firewall, wrong host, or a container/VPN that cannot route there." +
                " Note 'localhost' means this server's own box, not the machine you run mysql on."
        }
        if (failure.hasCause<UnknownHostException>()) {
            return "Hostname '${config.HOST}' does not resolve."
        }
        return "Network-level failure reaching ${config.HOST}:${config.PORT}."
    }

    @Throws(SQLException::class, IOException::class)
    private fun initializeDatabase() {
        connection().createStatement().use { statement ->
            for (script in plugin.getSQLScripts()) {
                statement.addBatch(script)
            }
            statement.executeBatch()
            migrate()

            statement.execute("DROP TRIGGER IF EXISTS update_item_history")
            statement.execute(plugin.getTriggerScript())
        }
    }

    /***
     * items.sql is CREATE TABLE IF NOT EXISTS, so an install that already has the table
     * never sees a column added to it. MySQL has no ADD COLUMN IF NOT EXISTS either,
     * hence checking the catalog by hand.
     */
    @Throws(SQLException::class)
    private fun migrate() {
        connection().createStatement().use { statement ->
            if (!hasColumn("items", "net_position")) {
                statement.execute("ALTER TABLE items ADD COLUMN net_position DECIMAL(20, 6) NOT NULL DEFAULT 0")
                statement.execute("ALTER TABLE items ADD COLUMN last_decay BIGINT NOT NULL DEFAULT 0")
                statement.execute(
                    "UPDATE items SET net_position = bought_amount - sold_amount, last_decay = " +
                        System.currentTimeMillis()
                )
                logger.info("Seeded net positions from the existing bought/sold tallies")
            }
            if (hasColumn("items", "percentage")) {
                statement.execute("ALTER TABLE items CHANGE percentage impact_k DECIMAL(20, 18) NOT NULL")
                logger.info("Renamed items.percentage to items.impact_k")
            }
            if (!hasColumn("items", "half_life_hours")) {
                statement.execute("ALTER TABLE items ADD COLUMN half_life_hours DECIMAL(10, 2) NOT NULL DEFAULT 48")
            }
            if (!hasColumn("items", "category")) {
                statement.execute("ALTER TABLE items ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'misc'")
            }
            // no-ops once they are already wide
            statement.execute("ALTER TABLE items MODIFY bought_amount BIGINT DEFAULT 0")
            statement.execute("ALTER TABLE items MODIFY sold_amount BIGINT DEFAULT 0")

            // history recorded tallies only, which stopped determining the price when the
            // curve went exponential and net started decaying. Charts need the price itself.
            if (!hasColumn("item_history", "unit_price")) {
                statement.execute("ALTER TABLE item_history ADD COLUMN net_position DECIMAL(20, 6) NOT NULL DEFAULT 0")
                statement.execute("ALTER TABLE item_history ADD COLUMN unit_price DECIMAL(20, 6) NOT NULL DEFAULT 0")
                logger.info("Added price columns to item_history; rows written before now have none")
            }
            statement.execute("ALTER TABLE item_history MODIFY bought_amount BIGINT")
            statement.execute("ALTER TABLE item_history MODIFY sold_amount BIGINT")
            if (!hasIndex("item_history", "idx_item_time")) {
                statement.execute("ALTER TABLE item_history ADD INDEX idx_item_time (item_id, change_date)")
            }
            if (!hasIndex("item_history", "idx_time")) {
                statement.execute("ALTER TABLE item_history ADD INDEX idx_time (change_date)")
            }
        }
    }

    @Throws(SQLException::class)
    private fun hasIndex(table: String, index: String): Boolean {
        connection().metaData.getIndexInfo(config.DATABASE, null, table, false, false).use { indexes ->
            while (indexes.next()) {
                if (index.equals(indexes.getString("INDEX_NAME"), ignoreCase = true)) return true
            }
        }
        return false
    }

    @Throws(SQLException::class)
    private fun hasColumn(table: String, column: String): Boolean =
        connection().metaData.getColumns(config.DATABASE, null, table, column).use { it.next() }

    override fun die() {
        try {
            connection?.close()
        } catch (failure: SQLException) {
            logger.warning(failure.message)
        }
    }

    override fun addItem(item: MarketItem): MarketItem? = withStatement(
        "INSERT INTO items (item_name, base_price, min_price, impact_k, half_life_hours, category," +
            " bought_amount, sold_amount, net_position, last_decay)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
    ) { statement ->
        val net = item.getNet()
        statement.setString(1, item.name)
        statement.setDouble(2, item.basePrice)
        statement.setDouble(3, item.minPrice)
        statement.setDouble(4, item.k)
        statement.setDouble(5, item.halfLifeHours)
        statement.setString(6, item.category)
        statement.setLong(7, item.boughtAmount)
        statement.setLong(8, item.soldAmount)
        statement.setDouble(9, net)
        statement.setLong(10, item.lastDecay)
        statement.execute()
        item.clone()
    }

    override fun setItem(item: MarketItem): MarketItem? = withStatement(
        "UPDATE items SET base_price = ?, min_price = ?, impact_k = ?, half_life_hours = ?, category = ?," +
            " bought_amount = ?, sold_amount = ?, net_position = ?, last_decay = ? WHERE item_name = ?;"
    ) { statement ->
        val net = item.getNet()
        statement.setDouble(1, item.basePrice)
        statement.setDouble(2, item.minPrice)
        statement.setDouble(3, item.k)
        statement.setDouble(4, item.halfLifeHours)
        statement.setString(5, item.category)
        statement.setLong(6, item.boughtAmount)
        statement.setLong(7, item.soldAmount)
        statement.setDouble(8, net)
        statement.setLong(9, item.lastDecay)
        statement.setString(10, item.name)
        statement.execute()
        item.clone()
    }

    override fun removeItem(item: String): Boolean = withStatement(
        "DELETE FROM items WHERE item_name = ?;"
    ) { statement ->
        statement.setString(1, item)
        statement.execute()
        true
    } ?: false

    override fun getItem(item: String): MarketItem? = withStatement(
        "SELECT * FROM items WHERE item_name = ?;"
    ) { statement ->
        statement.setString(1, item)
        statement.execute()
        val row = statement.resultSet
        if (!row.next()) return@withStatement null
        row.toMarketItem(item)
    }

    override fun getAllItems(): List<MarketItem>? = withStatement("SELECT * FROM items;") { statement ->
        statement.execute()
        val row = statement.resultSet
        val items = ArrayList<MarketItem>()
        while (row.next()) {
            items.add(row.toMarketItem(row.getString("item_name")))
        }
        items
    }

    override fun setAllItems(items: List<MarketItem>): List<MarketItem>? {
        for (item in items) {
            val stored = getItem(item.name)
            if (stored == null) {
                addItem(item)
                continue
            }
            if (staticsDrifted(stored, item)) {
                setItem(item)
                continue
            }
            setAmounts(item, item.boughtAmount, item.soldAmount)
        }
        return getAllItems()
    }

    /***
     * Price to compare today against: the newest row at or before the cutoff, or failing
     * that the oldest row there is. Without the fallback a fresh install has nothing old
     * enough to compare to and the whole report reads empty until the window has elapsed,
     * however much the prices actually moved in the meantime.
     *
     * Rows from before the price columns existed carry 0 and would read as an infinite move.
     */
    override fun getPricesAt(hoursAgo: Int): Map<String, Double>? = withStatement(
        "SELECT i.item_name, COALESCE((" +
            "  SELECT h.unit_price FROM item_history h" +
            "  WHERE h.item_id = i.item_id AND h.unit_price > 0" +
            "    AND h.change_date <= DATE_SUB(NOW(), INTERVAL ? HOUR)" +
            "  ORDER BY h.change_date DESC LIMIT 1" +
            "), (" +
            "  SELECT h.unit_price FROM item_history h" +
            "  WHERE h.item_id = i.item_id AND h.unit_price > 0" +
            "  ORDER BY h.change_date ASC LIMIT 1" +
            ")) AS old_price FROM items i;"
    ) { statement ->
        statement.setInt(1, hoursAgo)
        statement.execute()
        val row = statement.resultSet

        val prices = HashMap<String, Double>()
        while (row.next()) {
            val price = row.getDouble("old_price")
            if (!row.wasNull() && price > 0) {
                prices[row.getString("item_name")] = price
            }
        }
        prices
    }

    /***
     * Same nearest-before-or-oldest fallback as getPricesAt, but for the lifetime tallies
     * instead of price — this is what a caller subtracts from the current tallies to get
     * units traded within the window, since only the running totals are stored anywhere.
     */
    override fun getVolumesAt(hoursAgo: Int): Map<String, Pair<Long, Long>>? = withStatement(
        "SELECT i.item_name," +
            "  COALESCE((" +
            "    SELECT h.bought_amount FROM item_history h" +
            "    WHERE h.item_id = i.item_id AND h.change_date <= DATE_SUB(NOW(), INTERVAL ? HOUR)" +
            "    ORDER BY h.change_date DESC LIMIT 1" +
            "  ), (" +
            "    SELECT h.bought_amount FROM item_history h" +
            "    WHERE h.item_id = i.item_id ORDER BY h.change_date ASC LIMIT 1" +
            "  )) AS old_bought," +
            "  COALESCE((" +
            "    SELECT h.sold_amount FROM item_history h" +
            "    WHERE h.item_id = i.item_id AND h.change_date <= DATE_SUB(NOW(), INTERVAL ? HOUR)" +
            "    ORDER BY h.change_date DESC LIMIT 1" +
            "  ), (" +
            "    SELECT h.sold_amount FROM item_history h" +
            "    WHERE h.item_id = i.item_id ORDER BY h.change_date ASC LIMIT 1" +
            "  )) AS old_sold" +
            " FROM items i;"
    ) { statement ->
        statement.setInt(1, hoursAgo)
        statement.setInt(2, hoursAgo)
        statement.execute()
        val row = statement.resultSet

        val volumes = HashMap<String, Pair<Long, Long>>()
        while (row.next()) {
            val bought = row.getLong("old_bought")
            val boughtNull = row.wasNull()
            val sold = row.getLong("old_sold")
            val soldNull = row.wasNull()
            if (!boughtNull || !soldNull) {
                volumes[row.getString("item_name")] = (if (boughtNull) 0L else bought) to (if (soldNull) 0L else sold)
            }
        }
        volumes
    }

    override fun setBasePrice(item: MarketItem, basePrice: Double): MarketItem? = withStatement(
        "UPDATE items SET base_price = ? WHERE item_name = ?;"
    ) { statement ->
        statement.setDouble(1, basePrice)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, basePrice, item.boughtAmount, item.soldAmount, item.minPrice, item.k)
    }

    override fun setMinPrice(item: MarketItem, minPrice: Double): MarketItem? = withStatement(
        "UPDATE items SET min_price = ? WHERE item_name = ?;"
    ) { statement ->
        statement.setDouble(1, minPrice)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, item.basePrice, item.boughtAmount, item.soldAmount, minPrice, item.k)
    }

    override fun addBoughtAmount(item: MarketItem, amount: Int): MarketItem? = withStatement(
        "UPDATE items SET bought_amount = bought_amount + ? WHERE item_name = ?;"
    ) { statement ->
        statement.setInt(1, amount)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, item.basePrice, item.boughtAmount + amount, item.soldAmount, item.minPrice, item.k)
    }

    override fun addSoldAmount(item: MarketItem, amount: Int): MarketItem? = withStatement(
        "UPDATE items SET sold_amount = sold_amount + ? WHERE item_name = ?;"
    ) { statement ->
        statement.setInt(1, amount)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, item.basePrice, item.boughtAmount, item.soldAmount + amount, item.minPrice, item.k)
    }

    override fun setBoughtAmount(item: MarketItem, amount: Long): MarketItem? = withStatement(
        "UPDATE items SET bought_amount = ? WHERE item_name = ?;"
    ) { statement ->
        statement.setLong(1, amount)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, item.basePrice, amount, item.soldAmount, item.minPrice, item.k)
    }

    override fun setSoldAmount(item: MarketItem, amount: Long): MarketItem? = withStatement(
        "UPDATE items SET sold_amount = ? WHERE item_name = ?;"
    ) { statement ->
        statement.setLong(1, amount)
        statement.setString(2, item.name)
        statement.execute()
        MarketItem(item.name, item.basePrice, item.boughtAmount, amount, item.minPrice, item.k)
    }

    override fun setAmounts(item: MarketItem, boughtAmount: Long, soldAmount: Long): MarketItem? = withStatement(
        "UPDATE items SET bought_amount = ?, sold_amount = ?, net_position = ?, last_decay = ? WHERE item_name = ?;"
    ) { statement ->
        val net = item.getNet()
        statement.setLong(1, boughtAmount)
        statement.setLong(2, soldAmount)
        statement.setDouble(3, net)
        statement.setLong(4, item.lastDecay)
        statement.setString(5, item.name)
        statement.execute()

        MarketItem(item.name, item.basePrice, boughtAmount, soldAmount, item.minPrice, item.k)
            .apply { restoreNet(net, item.lastDecay) }
    }

    /***
     * A price point per item on a timer, whether or not anyone traded. The trigger only
     * fires on trades, so a quiet item would have no points at all — even though its
     * price has been moving the whole time as net decays back toward base.
     */
    override fun snapshotHistory(): Boolean =
        try {
            connection().createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO item_history (item_id, bought_amount, sold_amount, net_position, unit_price) " +
                        "SELECT item_id, bought_amount, sold_amount, net_position, base_price * EXP(impact_k * net_position) " +
                        "FROM items"
                )
            }
            true
        } catch (failure: SQLException) {
            logger.warning(failure.message)
            false
        }

    override fun pruneHistory(retentionDays: Int): Int {
        if (retentionDays <= 0) return 0
        return withStatement(
            "DELETE FROM item_history WHERE change_date < DATE_SUB(NOW(), INTERVAL ? DAY);"
        ) { statement ->
            statement.setInt(1, retentionDays)
            statement.executeUpdate()
        } ?: 0
    }

    @Deprecated("Redundant because of the item_history trigger")
    override fun createHistoryPoint(item: MarketItem): Boolean = withStatement(
        "INSERT INTO item_history (item_id, bought_amount, sold_amount) VALUES (?, ?, ?);"
    ) { statement ->
        statement.setInt(1, itemId(item.name))
        statement.setLong(2, item.boughtAmount)
        statement.setLong(3, item.soldAmount)
        statement.execute()
        true
    } ?: false

    @Throws(SQLException::class)
    private fun itemId(item: String): Int =
        connection().prepareStatement("SELECT item_id FROM items WHERE item_name = ?;").use { statement ->
            statement.setString(1, item)
            statement.execute()
            val row = statement.resultSet
            if (!row.next()) throw SQLException("Item not found!")
            row.getInt("item_id")
        }

    /***
     * Warns and answers null rather than throwing: a push runs on the main thread every
     * few seconds, and one bad query must not take the tick with it.
     */
    private fun <T> withStatement(sql: String, body: (PreparedStatement) -> T?): T? =
        try {
            connection().prepareStatement(sql).use(body)
        } catch (failure: SQLException) {
            logger.warning(failure.message)
            null
        }
}

private fun staticsDrifted(stored: MarketItem, item: MarketItem): Boolean =
    stored.basePrice != item.basePrice ||
        stored.minPrice != item.minPrice ||
        stored.k != item.k ||
        stored.halfLifeHours != item.halfLifeHours ||
        stored.category != item.category

private fun ResultSet.toMarketItem(name: String): MarketItem =
    MarketItem(
        name,
        getDouble("base_price"),
        getLong("bought_amount"),
        getLong("sold_amount"),
        getDouble("min_price"),
        getDouble("impact_k"),
    ).apply {
        halfLifeHours = getDouble("half_life_hours")
        category = getString("category") ?: "misc"
        restoreNet(getDouble("net_position"), getLong("last_decay"))
    }

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this, Throwable::cause).any { it is T }
