package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.ln

class PriceHandlerTest {

    private val prices = PriceHandler(0.85)

    /***
     * Decay off, so the curve tests are about the curve and nothing else.
     */
    private fun cobblestone() =
        MarketItem("cobblestone", 0.6, 0, 0, 0.04, 0.0001).apply { halfLifeHours = 0.0 }

    private fun decayingCobblestone() =
        MarketItem("cobblestone", 0.6, 0, 0, 0.04, 0.0001).apply { halfLifeHours = HALF_LIFE }

    /***
     * The property everything else rests on. Any run of trades that ends flat has to
     * leave the player poorer, whatever order or sizes they picked. The old formula
     * priced a whole batch at the pre-trade price and failed this outright.
     */
    @Test
    fun unwindingToFlatNeverProfits() {
        for (seed in 0L until 10_000L) {
            val rng = Random(seed)
            val cobble = cobblestone()
            var cash = 0.0
            var held = 0

            repeat(40) {
                val amount = 1 + rng.nextInt(2000)
                if (rng.nextBoolean()) {
                    cash -= prices.getBuyPrice(cobble, amount)
                    cobble.recordBuy(amount)
                    held += amount
                } else if (held >= amount) {
                    cash += prices.getSellPrice(cobble, amount)
                    cobble.recordSell(amount)
                    held -= amount
                }
            }
            if (held > 0) {
                cash += prices.getSellPrice(cobble, held)
                cobble.recordSell(held)
            }

            assertTrue(cash < 1e-6, "made $cash out of nothing, seed $seed")
        }
    }

    @Test
    fun aShulkerCostsTheSameBoughtInStacks() {
        val atOnce = prices.getBuyPrice(cobblestone(), 1728)

        val cobble = cobblestone()
        var stackByStack = 0.0
        repeat(27) {
            stackByStack += prices.getBuyPrice(cobble, 64)
            cobble.recordBuy(64)
        }

        assertEquals(atOnce, stackByStack, 1e-9)
    }

    @Test
    fun sellingBackWhatYouJustBoughtLosesExactlyTheSpread() {
        val cobble = cobblestone()
        val paid = prices.getBuyPrice(cobble, 10_000)
        cobble.recordBuy(10_000)

        assertEquals(0.85, prices.getSellPrice(cobble, 10_000) / paid, 1e-9)
    }

    /***
     * /dmarket reload writes the new multiplier onto the live handler rather than building
     * a new one, because the registered placeholders hold a reference to this instance.
     */
    @Test
    fun theSpreadFollowsAReloadedMultiplier() {
        val cobble = cobblestone()
        val paid = prices.getBuyPrice(cobble, 10_000)
        cobble.recordBuy(10_000)

        prices.sellMultiplier = 0.5

        assertEquals(0.5, prices.getSellPrice(cobble, 10_000) / paid, 1e-9)
    }

    @Test
    fun itemsWithNoImpactStayAtBasePrice() {
        val bedrock = MarketItem("bedrock", 12.0, 0, 0, 12.0, 0.0).apply { halfLifeHours = 0.0 }
        bedrock.recordBuy(50_000)

        assertEquals(120.0, prices.getBuyPrice(bedrock, 10), 1e-9)
    }

    /***
     * The config knob has to mean literally what it is named, otherwise nobody can tune
     * items.yml without a calculator.
     */
    @Test
    fun unitsToDoubleDoublesThePrice() {
        val cobble = MarketItem("cobblestone", 0.6, 0, 0, 0.04, ln(2.0) / 20_000)
            .apply { halfLifeHours = 0.0 }
        val before = prices.getUnitPrice(cobble)

        cobble.recordBuy(20_000)

        assertEquals(2 * before, prices.getUnitPrice(cobble), 1e-9)
    }

    @Test
    fun netHalvesOverOneHalfLife() {
        val cobble = decayingCobblestone()
        cobble.restoreNet(10_000.0, System.currentTimeMillis() - HALF_LIFE_MILLIS)

        assertEquals(5_000.0, cobble.getNet(), 1.0)
    }

    /***
     * Decay heals a crash, which is the point, but it must not hand the person who
     * caused the crash a cheap way back in. Recovery moves the price against them.
     */
    @Test
    fun sittingOutTheRecoveryDoesNotPayForCrashingIt() {
        val cobble = decayingCobblestone()
        val revenue = prices.getSellPrice(cobble, 20_000)
        cobble.recordSell(20_000)

        cobble.restoreNet(cobble.getNet(), System.currentTimeMillis() - HALF_LIFE_MILLIS)

        assertTrue(prices.getBuyPrice(cobble, 20_000) > revenue)
    }

    @Test
    fun ridingTheDecayAfterBuyingDoesNotPayEither() {
        val cobble = decayingCobblestone()
        val paid = prices.getBuyPrice(cobble, 20_000)
        cobble.recordBuy(20_000)

        cobble.restoreNet(cobble.getNet(), System.currentTimeMillis() - HALF_LIFE_MILLIS)

        assertTrue(prices.getSellPrice(cobble, 20_000) < paid)
    }

    /***
     * A row written before the decay columns existed comes back with last_decay 0.
     * Decaying from 1970 would silently wipe the position.
     */
    @Test
    fun rowsFromBeforeTheMigrationKeepTheirPosition() {
        val cobble = decayingCobblestone()
        cobble.restoreNet(8_000.0, 0)

        assertEquals(8_000.0, cobble.getNet(), 1e-6)
    }

    private companion object {
        const val HALF_LIFE = 48.0
        const val HALF_LIFE_MILLIS = 48L * 3_600_000L
    }
}
