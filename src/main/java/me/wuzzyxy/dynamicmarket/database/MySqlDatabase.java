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
        return connection = dataSource.getConnection();
    }

    private void initializeDatabase() throws SQLException, IOException {
        Statement statement = getConnection().createStatement();

        ArrayList<String> scripts = plugin.getSQLScripts();
        for (String script : scripts) {
            statement.addBatch(script);
        }

        statement.executeBatch();
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
    public MarketItem addItem(String item, double basePrice, double minPrice, double percentage) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "INSERT INTO items (item_name, base_price, min_price, percentage) VALUES (?, ?, ?, ?);"
            );
            statement.setString(1, item);
            statement.setDouble(2, basePrice);
            statement.setDouble(3, minPrice);
            statement.setDouble(4, percentage);
            statement.execute();
            statement.close();
            return new MarketItem(item, basePrice, 0, 0, minPrice, percentage);
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem addItem(String item, double basePrice, double minPrice, int boughtAmount, int soldAmount, double percentage) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "INSERT INTO items (item_name, base_price, min_price, bought_amount, sold_amount, percentage)" +
                            " VALUES (?, ?, ?, ?, ?, ?);"
            );
            statement.setString(1, item);
            statement.setDouble(2, basePrice);
            statement.setDouble(3, minPrice);
            statement.setInt(4, boughtAmount);
            statement.setInt(5, soldAmount);
            statement.setDouble(6, percentage);
            statement.execute();
            statement.close();
            return new MarketItem(item, basePrice, boughtAmount, soldAmount, minPrice, percentage);
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setItem(String item, double basePrice, double minPrice, int boughtAmount, int soldAmount, double percentage) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET base_price = ?, min_price = ?, bought_amount = ?, sold_amount = ?, percentage = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, basePrice);
            statement.setDouble(2, minPrice);
            statement.setInt(3, boughtAmount);
            statement.setInt(4, soldAmount);
            statement.setDouble(5, percentage);
            statement.setString(6, item);
            statement.execute();
            statement.close();
            return new MarketItem(item, basePrice, boughtAmount, soldAmount, minPrice, percentage);
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setItemStatics(String item, double basePrice, double minPrice, double percentage) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET base_price = ?, min_price = ?, percentage = ? WHERE item_name = ?;"
            );
            statement.setDouble(1, basePrice);
            statement.setDouble(2, minPrice);
            statement.setDouble(3, percentage);
            statement.setString(4, item);
            statement.execute();
            statement.close();
            return new MarketItem(item, basePrice, 0, 0, minPrice, percentage);
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
                    resultSet.getInt("bought_amount"),
                    resultSet.getInt("sold_amount"),
                    resultSet.getDouble("min_price"),
                    resultSet.getDouble("percentage")
            );
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
                items.add(new MarketItem(
                        resultSet.getString("item_name"),
                        resultSet.getDouble("base_price"),
                        resultSet.getInt("bought_amount"),
                        resultSet.getInt("sold_amount"),
                        resultSet.getDouble("min_price"),
                        resultSet.getDouble("percentage")
                ));
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
                addItem(item.getName(), item.getBasePrice(), item.getMinPrice(), item.getBoughtAmount(), item.getSoldAmount(), item.getPercentage());
                continue;
            }
            if (dbItem.getBasePrice() != item.getBasePrice() || dbItem.getMinPrice() != item.getMinPrice() || dbItem.getPercentage() != item.getPercentage()){
                setItem(item.getName(), item.getBasePrice(), item.getMinPrice(), item.getBoughtAmount(), item.getSoldAmount(), item.getPercentage());
                continue;
            }
            setAmounts(item, item.getBoughtAmount(), item.getSoldAmount());
        }
        return getAllItems();
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
            return new MarketItem(item.getName(), basePrice, item.getBoughtAmount(), item.getSoldAmount(), item.getMinPrice(), item.getPercentage());
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
            return new MarketItem(item.getName(), basePrice, item.getBoughtAmount(), item.getSoldAmount(), item.getMinPrice(), item.getPercentage());
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
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount(), minPrice, item.getPercentage());
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
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount(), min_price, item.getPercentage());
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
            int boughtAmount = statement.getResultSet().getInt("bought_amount");
            if (boughtAmount == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), boughtAmount, item.getSoldAmount(), item.getMinPrice(), item.getPercentage());
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
            int soldAmount = statement.getResultSet().getInt("sold_amount");
            if (soldAmount == 0) return null;
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), soldAmount, item.getMinPrice(), item.getPercentage());
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
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount() + amount, item.getSoldAmount(), item.getMinPrice(), item.getPercentage());
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
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), item.getSoldAmount() + amount, item.getMinPrice(), item.getPercentage());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setBoughtAmount(MarketItem item, int amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET bought_amount = ? WHERE item_name = ?;"
            );
            statement.setInt(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), amount, item.getSoldAmount(), item.getMinPrice(), item.getPercentage());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setSoldAmount(MarketItem item, int amount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET sold_amount = ? WHERE item_name = ?;"
            );
            statement.setInt(1, amount);
            statement.setString(2, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), item.getBoughtAmount(), amount, item.getMinPrice(), item.getPercentage());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
        }
    }

    @Override
    public MarketItem setAmounts(MarketItem item, int boughtAmount, int soldAmount) {
        try {
            PreparedStatement statement = getConnection().prepareStatement(
                    "UPDATE items SET bought_amount = ?, sold_amount = ? WHERE item_name = ?;"
            );
            statement.setInt(1, boughtAmount);
            statement.setInt(2, soldAmount);
            statement.setString(3, item.getName());
            statement.execute();
            statement.close();
            return new MarketItem(item.getName(), item.getBasePrice(), boughtAmount, soldAmount, item.getMinPrice(), item.getPercentage());
        } catch (SQLException throwables) {
            logger.warning(throwables.getMessage());
            return null;
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
            statement.setInt(2, item.getBoughtAmount());
            statement.setInt(3, item.getSoldAmount());
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
