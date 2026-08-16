package me.wuzzyxy.dynamicmarket.items

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

/***
 * Slots 0-35: the 4x9 main grid plus the hotbar. Armor and offhand are deliberately excluded —
 * nothing this shop trades is wearable or a legitimate offhand item, so counting them would
 * only let a player sell, say, a pumpkin they're wearing as a helmet out from under themselves.
 */
private const val MAIN_INVENTORY_SIZE = 36

/*** How many more of [stack] fit in the player's main inventory. */
fun PlayerInventory.freeCapacityFor(stack: ItemStack): Int {
    val maxStack = stack.maxStackSize
    var free = 0
    for (slot in 0 until MAIN_INVENTORY_SIZE) {
        val existing = getItem(slot)
        free += when {
            existing == null || existing.type.isAir -> maxStack
            // Another plugin's overstacked slot would otherwise report negative room and eat
            // into the total, refusing a purchase that fits perfectly well elsewhere.
            existing.isSimilar(stack) -> (maxStack - existing.amount).coerceAtLeast(0)
            else -> 0
        }
    }
    return free
}

fun PlayerInventory.countMatching(resolver: ItemResolver, marketItemName: String): Int {
    var count = 0
    for (slot in 0 until MAIN_INVENTORY_SIZE) {
        val stack = getItem(slot) ?: continue
        if (resolver.matches(stack, marketItemName)) count += stack.amount
    }
    return count
}

/*** Removes up to [amount], lowest slot index first. Returns how many were actually removed. */
fun PlayerInventory.removeMatching(resolver: ItemResolver, marketItemName: String, amount: Int): Int {
    var remaining = amount
    for (slot in 0 until MAIN_INVENTORY_SIZE) {
        if (remaining <= 0) break

        val stack = getItem(slot) ?: continue
        if (!resolver.matches(stack, marketItemName)) continue

        val take = minOf(remaining, stack.amount)
        if (take == stack.amount) setItem(slot, null) else stack.amount -= take
        remaining -= take
    }
    return amount - remaining
}
