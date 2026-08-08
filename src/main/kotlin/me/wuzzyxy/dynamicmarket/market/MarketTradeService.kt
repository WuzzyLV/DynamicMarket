package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.economy.VaultEconomyService
import me.wuzzyxy.dynamicmarket.items.ItemResolver
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.items.countMatching
import me.wuzzyxy.dynamicmarket.items.freeCapacityFor
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.items.removeMatching
import org.bukkit.entity.Player

sealed interface TradeResult {
    data class Bought(val item: MarketItem, val amount: Int, val price: Double) : TradeResult
    data class Sold(val item: MarketItem, val amount: Int, val price: Double) : TradeResult
    data class Denied(val reason: String) : TradeResult
}

data class SaleLine(val item: MarketItem, val amount: Int, val price: Double)

data class SweepResult(val lines: List<SaleLine>, val total: Double, val denied: String? = null)

/***
 * Where a GUI click actually becomes money changing hands and items moving. MarketManager's
 * working items still record every trade the same way BuySellCommand does (recordBuy/recordSell),
 * so a GUI sale and an external `/dmarket sell` move the price curve identically — this class
 * only adds the Vault + inventory side that the command-line interface deliberately never had.
 */
class MarketTradeService(
    private val manager: MarketManager,
    private val priceHandler: PriceHandler,
    private val economy: VaultEconomyService,
    private val resolver: ItemResolver,
) {

    fun buy(player: Player, item: MarketItem, amount: Int): TradeResult {
        if (amount <= 0) return TradeResult.Denied("Amount must be greater than 0")
        if (!economy.available) return TradeResult.Denied("The shop's economy isn't available right now")

        val icon = resolver.resolve(item.name, amount)
            ?: return TradeResult.Denied("${prettyItemName(item.name)} isn't a valid item")

        val room = player.inventory.freeCapacityFor(icon)
        if (room < amount) {
            return TradeResult.Denied("You only have room for $room more ${prettyItemName(item.name)}")
        }

        val price = priceHandler.getBuyPrice(item, amount)
        if (!economy.has(player, price)) {
            val short = price - economy.balance(player)
            return TradeResult.Denied("You need ${economy.format(short)} more to buy $amount ${prettyItemName(item.name)}")
        }

        if (!economy.withdraw(player, price)) return TradeResult.Denied("Payment failed, nothing was charged")

        // The room check above should make this unreachable, but never trust it blindly:
        // if Inventory.addItem() ever can't fit everything, the player must never be charged
        // for the part that didn't arrive.
        val undelivered = player.inventory.addItem(icon).values.sumOf { it.amount }
        val delivered = amount - undelivered
        if (delivered <= 0) {
            economy.deposit(player, price)
            return TradeResult.Denied("You don't have room for any of that — nothing was charged")
        }

        // Prices are along a curve, not flat per unit, so a partial refund has to be the real
        // cost of the delivered amount (recomputed off the same starting position, since
        // recordBuy hasn't moved it yet) — not a proportional share of the full-amount price.
        val actualPrice = if (undelivered > 0) priceHandler.getBuyPrice(item, delivered) else price
        if (undelivered > 0) economy.deposit(player, price - actualPrice)

        item.recordBuy(delivered)
        return TradeResult.Bought(item, delivered, actualPrice)
    }

    fun sell(player: Player, item: MarketItem, amount: Int): TradeResult {
        if (amount <= 0) return TradeResult.Denied("Amount must be greater than 0")

        val owned = player.inventory.countMatching(resolver, item.name)
        if (owned <= 0) return TradeResult.Denied("You don't have any ${prettyItemName(item.name)}")

        return sellExact(player, item, minOf(amount, owned))
    }

    fun sellAll(player: Player, item: MarketItem): TradeResult {
        val owned = player.inventory.countMatching(resolver, item.name)
        if (owned <= 0) return TradeResult.Denied("You don't have any ${prettyItemName(item.name)}")

        return sellExact(player, item, owned)
    }

    /*** Sweeps every configured item the player is carrying and sells all of it, in one go. */
    fun sellEverything(player: Player): SweepResult {
        if (!economy.available) {
            return SweepResult(emptyList(), 0.0, "The shop's economy isn't available right now")
        }

        val lines = mutableListOf<SaleLine>()
        for (item in manager.workingItems) {
            val owned = player.inventory.countMatching(resolver, item.name)
            if (owned <= 0) continue

            val result = sellExact(player, item, owned)
            if (result is TradeResult.Sold) lines += SaleLine(result.item, result.amount, result.price)
        }
        return SweepResult(lines, lines.sumOf { it.price })
    }

    /***
     * Takes the items *before* paying, and pays for exactly what was actually removed — never
     * the amount the caller merely asked for. Never trust a pre-count (`countMatching`) as a
     * promise about what `removeMatching` will find a moment later: if the two ever disagree,
     * this order means the payout still exactly matches reality instead of a stale guess. Only
     * paying after a verified removal is what makes it impossible to be credited for items
     * that never actually left the player's inventory.
     */
    private fun sellExact(player: Player, item: MarketItem, amount: Int): TradeResult {
        if (!economy.available) return TradeResult.Denied("The shop's economy isn't available right now")
        if (amount <= 0) return TradeResult.Denied("You don't have any ${prettyItemName(item.name)}")

        val removed = player.inventory.removeMatching(resolver, item.name, amount)
        if (removed <= 0) return TradeResult.Denied("You don't have any ${prettyItemName(item.name)}")

        val price = priceHandler.getSellPrice(item, removed)
        if (!economy.deposit(player, price)) {
            // Payment failed after the items were already taken — hand back exactly what was
            // removed rather than leave the player short for nothing. This can't be abused to
            // create value: it returns precisely the stack it just took, never more.
            val refund = resolver.resolve(item.name, removed)
            if (refund != null) player.inventory.addItem(refund)
            return TradeResult.Denied("Payment failed, your items were returned")
        }

        item.recordSell(removed)
        return TradeResult.Sold(item, removed, price)
    }
}
