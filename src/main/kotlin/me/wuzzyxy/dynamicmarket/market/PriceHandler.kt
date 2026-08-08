package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.items.MarketItem
import kotlin.math.exp
import kotlin.math.expm1

class PriceHandler(var sellMultiplier: Double) {

    fun getBuyPrice(item: MarketItem, amount: Int): Double {
        val net = item.getNet()
        return curveArea(item, net + amount) - curveArea(item, net)
    }

    fun getSellPrice(item: MarketItem, amount: Int): Double {
        val net = item.getNet()
        return (curveArea(item, net) - curveArea(item, net - amount)) * sellMultiplier
    }

    /***
     * What one more unit costs right now. Signage only — never charge with this, the
     * whole point is that a batch does not get priced at a single point on the curve.
     */
    fun getUnitPrice(item: MarketItem): Double = item.basePrice * exp(item.k * item.getNet())
}

/***
 * Area under the price curve from 0 up to net. A trade costs the difference between
 * two of these, so the price depends only on where the trade starts and ends and not
 * on how it was chopped up. One click for 64 costs exactly what 64 clicks for 1 cost,
 * which is what stops the dump-a-shulker-at-the-pre-crash-price trick.
 */
private fun curveArea(item: MarketItem, net: Double): Double {
    val k = item.k
    if (k == 0.0) return item.basePrice * net
    return item.basePrice / k * expm1(k * net)
}
