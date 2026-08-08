package me.wuzzyxy.dynamicmarket.items

import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/***
 * A market item name is either a plain lowercase Material name ("oak_log") or a namespaced
 * CraftEngine id ("mypack:ruby") — the colon is the only signal between the two, so a typo'd
 * CraftEngine id never silently falls back to being read as a (nonexistent) vanilla material.
 *
 * [craftEngineAvailable] is resolved once at startup (CraftEngine is a softdepend); every call
 * here is safe to make regardless, it just always misses when the plugin isn't installed.
 */
class ItemResolver(private val craftEngineAvailable: Boolean) {

    fun isCraftEngineId(name: String): Boolean = name.contains(':')

    /***
     * The icon/gift item for a market item, or null when it doesn't resolve to anything real —
     * callers must skip a phantom item rather than hand a player an AIR stack.
     */
    fun resolve(name: String, amount: Int = 1): ItemStack? {
        val stack = if (isCraftEngineId(name)) {
            if (!craftEngineAvailable) return null
            CraftEngineItems.byId(name)?.buildBukkitItem() ?: return null
        } else {
            val material = Material.matchMaterial(name) ?: return null
            ItemStack(material)
        }
        // Deliberately not clamped to maxStackSize: Inventory.addItem() splits an oversized
        // amount across slots correctly on its own, and a GUI icon showing "128" for a
        // configured quantity tier bigger than one stack is honest, not a rendering bug.
        stack.amount = amount.coerceAtLeast(1)
        return stack
    }

    /***
     * Whether a stack a player is holding counts as the given market item for buy/sell purposes.
     * CraftEngine items match by their registered id (so a renamed/enchanted copy still counts —
     * the id, not the display, is what a shop cares about); vanilla items match by Material only,
     * for the same reason.
     */
    fun matches(stack: ItemStack, marketItemName: String): Boolean {
        if (stack.type.isAir) return false
        return if (isCraftEngineId(marketItemName)) {
            craftEngineAvailable && CraftEngineItems.getCustomItemId(stack)?.asString() == marketItemName
        } else {
            stack.type == Material.matchMaterial(marketItemName)
        }
    }
}
