package me.wuzzyxy.dynamicmarket.market;

import me.wuzzyxy.dynamicmarket.items.MarketItem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceHandlerTest {

    private final PriceHandler prices = new PriceHandler(0.85);

    private MarketItem cobblestone() {
        return new MarketItem("cobblestone", 0.6, 0, 0, 0.04, 0.0001);
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
                    cobble.setBoughtAmount(cobble.getBoughtAmount() + amount);
                    held += amount;
                } else if (held >= amount) {
                    cash += prices.getSellPrice(cobble, amount);
                    cobble.setSoldAmount(cobble.getSoldAmount() + amount);
                    held -= amount;
                }
            }
            if (held > 0) {
                cash += prices.getSellPrice(cobble, held);
                cobble.setSoldAmount(cobble.getSoldAmount() + held);
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
            cobble.setBoughtAmount(cobble.getBoughtAmount() + 64);
        }

        assertEquals(atOnce, stackByStack, 1e-9);
    }

    @Test
    void sellingBackWhatYouJustBoughtLosesExactlyTheSpread() {
        MarketItem cobble = cobblestone();
        double paid = prices.getBuyPrice(cobble, 10_000);
        cobble.setBoughtAmount(10_000);

        assertEquals(0.85, prices.getSellPrice(cobble, 10_000) / paid, 1e-9);
    }

    @Test
    void itemsWithNoImpactStayAtBasePrice() {
        MarketItem fixed = new MarketItem("bedrock", 12.0, 0, 0, 12.0, 0);
        fixed.setBoughtAmount(50_000);

        assertEquals(120.0, prices.getBuyPrice(fixed, 10), 1e-9);
    }
}
