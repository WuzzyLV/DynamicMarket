package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.items.MarketItem;

public class PriceHandler {
    private final double sellMultiplier;

    public PriceHandler(double sellMultiplier) {
        this.sellMultiplier = sellMultiplier;
    }

    /***
     * Area under the price curve from 0 up to net. A trade costs the difference between
     * two of these, so the price depends only on where the trade starts and ends and not
     * on how it was chopped up. One click for 64 costs exactly what 64 clicks for 1 cost,
     * which is what stops the dump-a-shulker-at-the-pre-crash-price trick.
     */
    private static double curveArea(MarketItem item, double net) {
        double k = item.getK();
        if (k == 0) return item.getBasePrice() * net;
        return item.getBasePrice() / k * Math.expm1(k * net);
    }

    public double getBuyPrice(MarketItem item, int amount) {
        double net = item.getNet();
        return curveArea(item, net + amount) - curveArea(item, net);
    }

    public double getSellPrice(MarketItem item, int amount) {
        double net = item.getNet();
        return (curveArea(item, net) - curveArea(item, net - amount)) * sellMultiplier;
    }

    /***
     * What one more unit costs right now. Signage only — never charge with this, the
     * whole point is that a batch does not get priced at a single point on the curve.
     */
    public double getUnitPrice(MarketItem item) {
        return item.getBasePrice() * Math.exp(item.getK() * item.getNet());
    }
}
