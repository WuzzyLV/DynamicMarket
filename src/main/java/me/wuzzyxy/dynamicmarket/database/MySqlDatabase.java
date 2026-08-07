package me.wuzzyxy.dynamicmarket.database;

import com.mysql.cj.jdbc.MysqlDataSource;
import me.wuzzyxy.dynamicmarket.DynamicMarket;
import me.wuzzyxy.dynamicmarket.items.MarketItem;
import me.wuzzyxy.dynamicmarket.configs.PluginConfig;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("ALL")
public class MySqlDatabase implements Database{

    private final DynamicMarket plugin;
    private final PluginConfig config;
    private final Logger logger;
    private Connection connection;

    public MySqlDatabase(DynamicMarket plugin) throws SQLException, IOException {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        this.logger = plugin.getLogger();


        getConnection();
        initializeDatabase();

    }

    private Connection getConnection() throws SQLException{
        if (connection != null) {
            return connection;
        }
        connection = setConnection();
        return connection;
    }

    private Connection setConnection() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setServerName(config.HOST);
        dataSource.setPort(config.PORT);
        dataSource.setDatabaseName(config.DATABASE);
        dataSource.setUser(config.USERNAME);
        dataSource.setPassword(config.PASSWORD);
        // Connector/J defaults both of these to 0, which means "wait forever". We connect from
        // onEnable on the main thread, so a host that swallows packets instead of refusing them
        // wedges the entire server boot rather than failing the plugin.
        dataSource.setConnectTimeout(config.CONNECT_TIMEOUT_MS);
        dataSource.setSocketTimeout(config.SOCKET_TIMEOUT_MS);

        logger.info("Connecting to " + config.HOST + ":" + config.PORT + "/" + config.DATABASE
                + " as " + config.USERNAME + ", giving up after " + config.CONNECT_TIMEOUT_MS + "ms");

        long start = System.currentTimeMillis();
        try {
            connection = dataSource.getConnection();
        } catch (SQLException throwables) {
            logger.severe("Gave up after " + (System.currentTimeMillis() - start) + "ms"
                    + " (SQLState " + throwables.getSQLState() + ", vendor code " + throwables.getErrorCode() + "): "
                    + throwables.getMessage());
            logger.severe(diagnose(throwables));
            throw throwables;
        }
        logger.info("Connected in " + (System.currentTimeMillis() - start) + "ms");
        return connection;
    }

    /***
     * The driver folds every network problem into one CommunicationsException, so the stack trace
     * alone never says whether the host is wrong, the port is closed, or the credentials are.
     */
    private String diagnose(SQLException throwables) {
        String sqlState = throwables.getSQLState() == null ? "" : throwables.getSQLState();
        switch (sqlState) {
            case "28000":
                return "Wrong username or password for '" + config.USERNAME + "'.";
            case "42000":
                return "Connected, but database '" + config.DATABASE + "' does not exist or the user cannot see it.";
            case "08S01":
                break;
            default:
                return "Check mysql.* in config.yml.";
        }
        if (hasCause(throwables, java.net.ConnectException.class)) {
            return "Refused: something answered at " + config.HOST + ":" + config.PORT + " but nothing is listening."
                    + " Is mysqld running, and is the port right?";
        }
        if (hasCause(throwables, java.net.SocketTimeoutException.class)) {
            return "Timed out with no reply from " + config.HOST + ":" + config.PORT + "."
                    + " Packets are being dropped - firewall, wrong host, or a container/VPN that cannot route there."
                    + " Note 'localhost' means this server's own box, not the machine you run mysql on.";
        }
        if (hasCause(throwables, java.net.UnknownHostException.class)) {
            return "Hostname '" + config.HOST + "' does not resolve.";
        }
        return "Network-level failure reaching " + config.HOST + ":" + config.PORT + ".";
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private void initializeDatabase() throws SQLException, IOException {
        Statement statement = getConnection().createStatement();

        ArrayList<String> scripts = plugin.getSQLScripts();
        for (String script : scripts) {
            statement.addBatch(script);
        }

        statement.executeBatch();
        migrate();

        statement.execute("DROP TRIGGER IF EXISTS update_item_history");
        statement.execute(plugin.getTriggerScript());
    }

    /***
     * items.sql is CREATE TABLE IF NOT EXISTS, so an install that already has the table
     * never sees a column added to it. MySQL has no ADD COLUMN IF NOT EXISTS either,
     * hence checking the catalog by hand.
     */
    private void migrate() throws SQLException {
        Statement statement = getConnection().createStatement();
        if (!hasColumn("items", "net_position")) {
            statement.execute("ALTER TABLE items ADD COLUMN net_position DECIMAL(20, 6) NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE items ADD COLUMN last_decay BIGINT NOT NULL DEFAULT 0");
            statement.execute("UPDATE items SET net_position = bought_amount - sold_amount, last_decay = "
                    + System.currentTimeMillis());
            logger.info("Seeded net positions from the existing bought/sold tallies");
        }
        if (hasColumn("items", "percentage")) {
            statement.execute("ALTER TABLE items CHANGE percentage impact_k DECIMAL(20, 18) NOT NULL");
            logger.info("Renamed items.percentage to items.impact_k");
        }
        if (!hasColumn("items", "half_life_hours")) {
            statement.execute("ALTER TABLE items ADD COLUMN half_life_hours DECIMAL(10, 2) NOT NULL DEFAULT 48");
        }
        // no-ops once they are already wide
        statement.execute("ALTER TABLE items MODIFY bought_amount BIGINT DEFAULT 0");
        statement.execute("ALTER TABLE items MODIFY sold_amount BIGINT DEFAULT 0");

        // history recorded tallies only, which stopped determining the price when the
        // curve went exponential and net started decaying. Charts need the price itself.
        if (!hasColumn("item_history", "unit_price")) {
            statement.execute("ALTER TABLE item_history ADD COLUMN net_position DECIMAL(20, 6) NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE item_history ADD COLUMN unit_price DECIMAL(20, 6) NOT NULL DEFAULT 0");
            logger.info("Added price columns to item_history; rows written before now have none");
        }
        statement.execute("ALTER TABLE item_history MODIFY bought_amount BIGINT");
        statement.execute("ALTER TABLE item_history MODIFY sold_amount BIGINT");
        if (!hasIndex("item_history", "idx_item_time")) {
            statement.execute("ALTER TABLE item_history ADD INDEX idx_item_time (item_id, change_date)");
        }
        if (!hasIndex("item_history", "idx_time")) {
            statement.execute("ALTER TABLE item_history ADD INDEX idx_time (change_date)");
        }
        statement.close();
    }

    private boolean hasIndex(String table, String index) throws SQLException {
        ResultSet indexes = getConnection().getMetaData().getIndexInfo(config.DATABASE, null, table, false, false);
        while (indexes.next()) {
            if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                indexes.close();
                return true;
            }
        }
        indexes.close();
        return false;
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        ResultSet columns = getConnection().getMetaData().getColumns(config.DATABASE, null, table, column);
        boolean present = columns.next();
        columns.close();
        return present;
    }


    @Override
    public void die() {
        try {
            connection.close();
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
        }
    }

    @Override
    public MarketItem addItem(String item, double basePrice, double minPrice, double impactK, double halfLifeHours) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "INSERT INTO items (item_name, base_price, min_price, impact_k, half_life_hours) VALUES (?, ?, ?, ?, ?);"
            );
            statement.setString(1, item);
            statement.setDouble(2, basePrice);
            statement.setDouble(3, minPrice);
            statement.setDouble(4, impactK);
            statement.setDouble(5, halfLifeHours);
            statement.execute();
            statement.close();

            MarketItem added = new MarketItem(item, basePrice, 0, 0, minPrice, impactK);
            added.setHalfLifeHours(halfLifeHours);
            return added;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem addItem(String item, double basePrice, double minPrice, long boughtAmount, long soldAmount, double impactK, double halfLifeHours) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "INSERT INTO items (item_name, base_price, min_price, bought_amount, sold_amount, impact_k, half_life_hours, net_position, last_decay)" +
                            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);"
            );
            statement.setString(1, item);
            statement.setDouble(2, basePrice);
            statement.setDouble(3, minPrice);
            statement.setLong(4, boughtAmount);
            statement.setLong(5, soldAmount);
            statement.setDouble(6, impactK);
            statement.setDouble(7, halfLifeHours);
            statement.setDouble(8, boughtAmount - soldAmount);
            statement.setLong(9, System.currentTimeMillis());
            statement.execute();
            statement.close();

            MarketItem added = new MarketItem(item, basePrice, boughtAmount, soldAmount, minPrice, impactK);
            added.setHalfLifeHours(halfLifeHours);
            return added;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setItem(String item, double basePrice, double minPrice, long boughtAmount, long soldAmount, double impactK, double halfLifeHours) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET base_price = ?, min_price = ?, bought_amount = ?, sold_amount = ?, impact_k = ?, half_life_hours = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, basePrice);
            statement.setDouble(2, minPrice);
            statement.setLong(3, boughtAmount);
            statement.setLong(4, soldAmount);
            statement.setDouble(5, impactK);
            statement.setDouble(6, halfLifeHours);
            statement.setString(7, item);
            statement.execute();
            statement.close();

            MarketItem written = new MarketItem(item, basePrice, boughtAmount, soldAmount, minPrice, impactK);
            written.setHalfLifeHours(halfLifeHours);
            return written;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setItemStatics(String item, double basePrice, double minPrice, double impactK) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET base_price = ?, min_price = ?, impact_k = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, basePrice);
            statement.setDouble(2, minPrice);
            statement.setDouble(3, impactK);
            statement.setString(4, item);
            statement.execute();
            statement.close();
            return new MarketItem(item, basePrice, 0, 0, minPrice, impactK);
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public boolean removeItem(String item) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "DELETE FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item);
            statement.execute();
            statement.close();
            return true;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return false;
        }
    }

    @Override
    public MarketItem getItem(String item_name) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT * FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item_name);
            statement.execute();
            ResultSet resultSet = statement.getResultSet();
            if (!resultSet.next()) return null;

            MarketItem item = new MarketItem(
                    item_name,
                    resultSet.getDouble("base_price"),
                    resultSet.getLong("bought_amount"),
                    resultSet.getLong("sold_amount"),
                    resultSet.getDouble("min_price"),
                    resultSet.getDouble("impact_k")
            );
            item.setHalfLifeHours(resultSet.getDouble("half_life_hours"));
            item.restoreNet(resultSet.getDouble("net_position"), resultSet.getLong("last_decay"));
            statement.close();
            return item;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }
    @Override
    public List<MarketItem> getAllItems() {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT * FROM items;"
            );
            statement.execute();
            ResultSet resultSet = statement.getResultSet();
            ArrayList<MarketItem> items = new ArrayList<>();
            while (resultSet.next()) {
                MarketItem item = new MarketItem(
                        resultSet.getString("item_name"),
                        resultSet.getDouble("base_price"),
                        resultSet.getLong("bought_amount"),
                        resultSet.getLong("sold_amount"),
                        resultSet.getDouble("min_price"),
                        resultSet.getDouble("impact_k")
                );
                item.setHalfLifeHours(resultSet.getDouble("half_life_hours"));
                item.restoreNet(resultSet.getDouble("net_position"), resultSet.getLong("last_decay"));
                items.add(item);
            }
            statement.close();
            return items;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public List<MarketItem> setAllItems(List<MarketItem> items) {
        for (MarketItem item : items){
            MarketItem dbItem = getItem(item.getName());

            if (dbItem==null){
                addItem(item.getName(), item.getBasePrice(), item.getMinPrice(), item.getBoughtAmount(), item.getSoldAmount(), item.getK(), item.getHalfLifeHours());
                continue;
            }
            if (staticsDrifted(dbItem, item)){
                setItem(item.getName(), item.getBasePrice(), item.getMinPrice(), item.getBoughtAmount(), item.getSoldAmount(), item.getK(), item.getHalfLifeHours());
            }
            // setItem has no net to write, so the counters always go through here
            setAmounts(item, item.getBoughtAmount(), item.getSoldAmount());
        }
        return getAllItems();
    }

    private static boolean staticsDrifted(MarketItem dbItem, MarketItem item) {
        return dbItem.getBasePrice() != item.getBasePrice()
                || dbItem.getMinPrice() != item.getMinPrice()
                || dbItem.getK() != item.getK()
                || dbItem.getHalfLifeHours() != item.getHalfLifeHours();
    }

    @Override
    public MarketItem setBasePrice(MarketItem item, double basePrice){
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET base_price = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, basePrice);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), basePrice, item.getBoughtAmount(), item.getSoldAmount(), item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem getBasePrice(MarketItem item) {
        try{
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT base_price FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item.getName());
            statement.execute();
            double basePrice = statement.getResultSet().getDouble("base_price");
            if (basePrice == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), basePrice, item.getBoughtAmount(), item.getSoldAmount(), item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setMinPrice(MarketItem item, double minPrice){
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET min_price = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, minPrice);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount(), minPrice, item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem getMinPrice(MarketItem item) {
        try{
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT min_price FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item.getName());
            statement.execute();
            double min_price = statement.getResultSet().getDouble("min_price");
            if (min_price == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount(), min_price, item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem getBoughtAmount(MarketItem item) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT bought_amount FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item.getName());
            statement.execute();
            long boughtAmount = statement.getResultSet().getLong("bought_amount");
            if (boughtAmount == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), boughtAmount, item.getSoldAmount(), item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem getSoldAmount(MarketItem item) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "SELECT sold_amount FROM items WHERE item_name = ?;"
            );
            statement.setString(1, item.getName());
            statement.execute();
            long soldAmount = statement.getResultSet().getLong("sold_amount");
            if (soldAmount == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), soldAmount, item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem addBoughtAmount(MarketItem item, int amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET bought_amount = bought_amount + ? WHERE item_name = ?;"
            );
            statement.setInt(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount() + amount, item.getSoldAmount(), item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem addSoldAmount(MarketItem item, int amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET sold_amount = sold_amount + ? WHERE item_name = ?;"
            );
            statement.setInt(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount() + amount, item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setBoughtAmount(MarketItem item, long amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET bought_amount = ? WHERE item_name = ?;"
            );
            statement.setLong(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), amount, item.getSoldAmount(), item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setSoldAmount(MarketItem item, long amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET sold_amount = ? WHERE item_name = ?;"
            );
            statement.setLong(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), amount, item.getMinPrice(), item.getK());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setAmounts(MarketItem item, long boughtAmount, long soldAmount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET bought_amount = ?, sold_amount = ?, net_position = ?, last_decay = ? WHERE item_name = ?;"
            );
            double net = item.getNet();
            statement.setLong(1, boughtAmount);
            statement.setLong(2, soldAmount);
            statement.setDouble(3, net);
            statement.setLong(4, item.getLastDecay());
            statement.setString(5, item.getName());
            statement.execute();
            statement.close();

            MarketItem written = new MarketItem(item.getName(), item.getBasePrice(), boughtAmount, soldAmount, item.getMinPrice(), item.getK());
            written.restoreNet(net, item.getLastDecay());
            return written;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    /***
     * A price point per item on a timer, whether or not anyone traded. The trigger only
     * fires on trades, so a quiet item would have no points at all — even though its
     * price has been moving the whole time as net decays back toward base.
     */
    @Override
    public boolean snapshotHistory() {
        try {
            Statement statement = getConnection().createStatement();
            statement.execute(
                    "INSERT INTO item_history (item_id, bought_amount, sold_amount, net_position, unit_price) " +
                    "SELECT item_id, bought_amount, sold_amount, net_position, base_price * EXP(impact_k * net_position) " +
                    "FROM items"
            );
            statement.close();
            return true;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return false;
        }
    }

    @Override
    public int pruneHistory(int retentionDays) {
        if (retentionDays <= 0) return 0;
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "DELETE FROM item_history WHERE change_date < DATE_SUB(NOW(), INTERVAL ? DAY);"
            );
            statement.setInt(1, retentionDays);
            int removed = statement.executeUpdate();
            statement.close();
            return removed;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return 0;
        }
    }

    @Deprecated
    /***
     * Redundant because of the trigger
     */
    @Override
    public boolean createHistoryPoint(MarketItem item) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "INSERT INTO item_history (item_id, bought_amount, sold_amount) VALUES (?, ?, ?);"
            );
            statement.setInt(1, getItemID(item.getName()));
            statement.setLong(2, item.getBoughtAmount());
            statement.setLong(3, item.getSoldAmount());
            statement.execute();
            statement.close();
            return true;
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return false;
        }
    }

    private int getItemID(String item) throws SQLException {
        PreparedStatement statement = getConnection().prepareStatement(
                "SELECT item_id FROM items WHERE item_name = ?;"
        );
        statement.setString(1, item);
        statement.execute();
        if (!statement.getResultSet().next()) throw new SQLException("Item not found!");
        int id = statement.getResultSet().getInt("item_id");
        statement.close();
        return id;
    }
}
