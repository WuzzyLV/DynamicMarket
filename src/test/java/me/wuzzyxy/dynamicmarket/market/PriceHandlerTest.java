package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceHandlerTest {

    private static final long HALF_LIFE = 48;
    private static final long HALF_LIFE_MILLIS = HALF_LIFE * 3_600_000L;

    private final PriceHandler prices = new PriceHandler(0.85);

    /***
     * Decay off, so the curve tests are about the curve and nothing else.
     */
    private MarketItem cobblestone() {
        MarketItem cobble = new MarketItem("cobblestone", 0.6, 0, 0, 0.04, 0.0001);
        cobble.setHalfLifeHours(0);
        return cobble;
    }

    private MarketItem decayingCobblestone() {
        MarketItem cobble = new MarketItem("cobblestone", 0.6, 0, 0, 0.04, 0.0001);
        cobble.setHalfLifeHours(HALF_LIFE);
        return cobble;
    }

    /***
     * The property everything else rests on. Any run of trades that ends flat has to
     * leave the player poorer, whatever order or sizes they picked. The old formula
     * priced a whole batch at the pre-trade price and failed this outright.
     */
    @Test
    void unwindingToFlatNeverProfits() {
        for (long seed = 0; seed < 10_000; seed++) {
            Random rng = new Random(seed);
            MarketItem cobble = cobblestone();
            double cash = 0;
            int held = 0;

            for (int trade = 0; trade < 40; trade++) {
                int amount = 1 + rng.nextInt(2000);
                if (rng.nextBoolean()) {
                    cash -= prices.getBuyPrice(cobble, amount);
                    cobble.recordBuy(amount);
                    held += amount;
                } else if (held >= amount) {
                    cash += prices.getSellPrice(cobble, amount);
                    cobble.recordSell(amount);
                    held -= amount;
                }
            }
            if (held > 0) {
                cash += prices.getSellPrice(cobble, held);
                cobble.recordSell(held);
            }

            assertTrue(cash < 1e-6, "made " + cash + " out of nothing, seed " + seed);
        }
    }

    @Test
    void aShulkerCostsTheSameBoughtInStacks() {
        double atOnce = prices.getBuyPrice(cobblestone(), 1728);

        MarketItem cobble = cobblestone();
        double stackByStack = 0;
        for (int stack = 0; stack < 27; stack++) {
            stackByStack += prices.getBuyPrice(cobble, 64);
            cobble.recordBuy(64);
        }

        assertEquals(atOnce, stackByStack, 1e-9);
    }

    @Test
    void sellingBackWhatYouJustBoughtLosesExactlyTheSpread() {
        MarketItem cobble = cobblestone();
        double paid = prices.getBuyPrice(cobble, 10_000);
        cobble.recordBuy(10_000);

        assertEquals(0.85, prices.getSellPrice(cobble, 10_000) / paid, 1e-9);
    }

    @Test
    void itemsWithNoImpactStayAtBasePrice() {
        MarketItem bedrock = new MarketItem("bedrock", 12.0, 0, 0, 12.0, 0);
        bedrock.setHalfLifeHours(0);
        bedrock.recordBuy(50_000);

        assertEquals(120.0, prices.getBuyPrice(bedrock, 10), 1e-9);
    }

    /***
     * The config knob has to mean literally what it is named, otherwise nobody can tune
     * items.yml without a calculator.
     */
    @Test
    void unitsToDoubleDoublesThePrice() {
        MarketItem cobble = new MarketItem("cobblestone", 0.6, 0, 0, 0.04, Math.log(2) / 20_000);
        cobble.setHalfLifeHours(0);
        double before = prices.getUnitPrice(cobble);

        cobble.recordBuy(20_000);

        assertEquals(2 * before, prices.getUnitPrice(cobble), 1e-9);
    }

    @Test
    void netHalvesOverOneHalfLife() {
        MarketItem cobble = decayingCobblestone();
        cobble.restoreNet(10_000, System.currentTimeMillis() - HALF_LIFE_MILLIS);

        assertEquals(5_000, cobble.getNet(), 1.0);
    }

    /***
     * Decay heals a crash, which is the point, but it must not hand the person who
     * caused the crash a cheap way back in. Recovery moves the price against them.
     */
    @Test
    void sittingOutTheRecoveryDoesNotPayForCrashingIt() {
        MarketItem cobble = decayingCobblestone();
        double revenue = prices.getSellPrice(cobble, 20_000);
        cobble.recordSell(20_000);

        cobble.restoreNet(cobble.getNet(), System.currentTimeMillis() - HALF_LIFE_MILLIS);

        assertTrue(prices.getBuyPrice(cobble, 20_000) > revenue);
    }

    @Test
    void ridingTheDecayAfterBuyingDoesNotPayEither() {
        MarketItem cobble = decayingCobblestone();
        double paid = prices.getBuyPrice(cobble, 20_000);
        cobble.recordBuy(20_000);

        cobble.restoreNet(cobble.getNet(), System.currentTimeMillis() - HALF_LIFE_MILLIS);

        assertTrue(prices.getSellPrice(cobble, 20_000) < paid);
    }

    /***
     * A row written before the decay columns existed comes back with last_decay 0.
     * Decaying from 1970 would silently wipe the position.
     */
    @Test
    void rowsFromBeforeTheMigrationKeepTheirPosition() {
        MarketItem cobble = decayingCobblestone();
        cobble.restoreNet(8_000, 0);

        assertEquals(8_000, cobble.getNet(), 1e-6);
    }
}
