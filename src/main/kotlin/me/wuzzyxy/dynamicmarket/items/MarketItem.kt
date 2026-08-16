package me.wuzzyxy.dynamicmarket.items

import kotlin.math.pow

class MarketItem(
    val name: String,
    var basePrice: Double,
    boughtAmount: Long,
    soldAmount: Long,
    var minPrice: Double,
    impactK: Double,
) : Cloneable {

    /***
     * Log-price move per net unit. ln2 / units_to_double.
     */
    var k: Double = impactK

    var halfLifeHours: Double = DEFAULT_HALF_LIFE_HOURS

    var category: String = "misc"
        set(value) {
            field = value.ifBlank { "misc" }
        }

    /***
     * Lifetime tallies. Analytics and the history trigger only — the price stopped
     * being a function of these when net started decaying.
     */
    var boughtAmount: Long = boughtAmount
        private set

    var soldAmount: Long = soldAmount
        private set

    private var netPosition: Double = (boughtAmount - soldAmount).toDouble()

    var lastDecay: Long = System.currentTimeMillis()
        private set

    /***
     * Net units the market is long, pulled back toward zero as it ages. The tallies on
     * their own are a one way ratchet: price becomes a function of every trade that ever
     * happened, so an item that got dumped once sits on the floor for the rest of the
     * server's life. Settling on read means no scheduler and nothing to catch up after
     * a restart.
     */
    fun getNet(): Double {
        val now = System.currentTimeMillis()
        netPosition = decayedAt(now)
        lastDecay = now
        return netPosition
    }

    /***
     * The same number without writing it back, for callers that are only quoting a price.
     * [getNet] settling on read made every GUI render and every placeholder a writer, which
     * is harmless on one thread and a lost update on two — a decay write landing on top of
     * a recordBuy silently swallows the trade. Anything that only wants to *read* the
     * position wants this; only trades, shocks and the DB push should be moving it.
     */
    fun peekNet(): Double = decayedAt(System.currentTimeMillis())

    private fun decayedAt(now: Long): Double =
        if (halfLifeHours > 0 && now > lastDecay) {
            netPosition * 0.5.pow((now - lastDecay) / (halfLifeHours * 3_600_000.0))
        } else {
            netPosition
        }

    fun recordBuy(amount: Int) {
        getNet()
        netPosition += amount
        boughtAmount += amount
    }

    fun recordSell(amount: Int) {
        getNet()
        netPosition -= amount
        soldAmount += amount
    }

    /***
     * A synthetic market event, not a trade — moves net the same way a trade would (so it
     * prices and decays identically) but leaves boughtAmount/soldAmount alone, since those
     * feed the DB history trigger and volume analytics and nothing was actually bought or sold.
     */
    fun applyShock(delta: Double) {
        getNet()
        netPosition += delta
    }

    /***
     * Puts back what was on disk. A row written before the decay columns existed has
     * last_decay 0, and decaying from 1970 would wipe the position.
     */
    fun restoreNet(net: Double, lastDecay: Long) {
        this.netPosition = net
        this.lastDecay = if (lastDecay > 0) lastDecay else System.currentTimeMillis()
    }

    /***
     * Admin override. Moves the price too, otherwise /dmarket set only rewrites the
     * analytics and leaves the market where it was.
     */
    fun setCounters(boughtAmount: Long, soldAmount: Long) {
        this.boughtAmount = boughtAmount
        this.soldAmount = soldAmount
        this.netPosition = (boughtAmount - soldAmount).toDouble()
        this.lastDecay = System.currentTimeMillis()
    }

    override fun toString(): String =
        "MarketItem{name='$name', basePrice=$basePrice, impactK=$k, minPrice=$minPrice," +
            " boughtAmount=$boughtAmount, soldAmount=$soldAmount, net=$netPosition}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is MarketItem && name == other.name)

    override fun hashCode(): Int = name.hashCode()

    public override fun clone(): MarketItem = super.clone() as MarketItem

    companion object {
        const val DEFAULT_HALF_LIFE_HOURS: Double = 48.0
    }
}
