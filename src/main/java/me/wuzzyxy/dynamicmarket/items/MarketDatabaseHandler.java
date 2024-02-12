package me.wuzzyxy.dynamicmarket.items;

import me.wuzzyxy.dynamicmarket.database.Database;

public class MarketDatabaseHandler {
    MarketManager manager;
    Database database;

    public MarketDatabaseHandler(MarketManager manager, Database database) {
        this.manager = manager;
        this.database = database;
    }
    // Make the update loop here, and call the database to update the items every said period
}
