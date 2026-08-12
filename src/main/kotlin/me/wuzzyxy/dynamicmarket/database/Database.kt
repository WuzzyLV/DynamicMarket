package me.wuzzyxy.dynamicmarket.database

import me.wuzzyxy.dynamicmarket.items.MarketItem

/***
 * Every read and write answers null (or false/0) when the query blew up — the warning is
 * already in the log by then, and callers decide whether a miss is worth reacting to.
 */
interface Database {
    fun die()

    fun addItem(item: MarketItem): MarketItem?
    fun setItem(item: MarketItem): MarketItem?
    fun removeItem(item: String): Boolean
    fun getItem(item: String): MarketItem?
    fun getAllItems(): List<MarketItem>?
    fun setAllItems(items: List<MarketItem>): List<MarketItem>?
    fun setBasePrice(item: MarketItem, basePrice: Double): MarketItem?
    fun setMinPrice(item: MarketItem, minPrice: Double): MarketItem?

    fun addBoughtAmount(item: MarketItem, amount: Int): MarketItem?
    fun addSoldAmount(item: MarketItem, amount: Int): MarketItem?
    fun setBoughtAmount(item: MarketItem, amount: Long): MarketItem?
    fun setSoldAmount(item: MarketItem, amount: Long): MarketItem?
    fun setAmounts(item: MarketItem, boughtAmount: Long, soldAmount: Long): MarketItem?

    fun snapshotHistory(): Boolean
    fun pruneHistory(retentionDays: Int): Int
    fun getPricesAt(hoursAgo: Int): Map<String, Double>?
    fun getVolumesAt(hoursAgo: Int): Map<String, Pair<Long, Long>>?

    @Deprecated("Redundant because of the item_history trigger")
    fun createHistoryPoint(item: MarketItem): Boolean
}
