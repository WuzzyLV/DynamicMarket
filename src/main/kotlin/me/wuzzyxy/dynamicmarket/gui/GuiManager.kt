package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.economy.VaultEconomyService
import me.wuzzyxy.dynamicmarket.items.ItemResolver
import me.wuzzyxy.dynamicmarket.items.MarketItem
import me.wuzzyxy.dynamicmarket.market.MarketManager
import me.wuzzyxy.dynamicmarket.market.MarketTradeService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/***
 * Shared wiring for the three GUI screens. Reads `plugin.shopConfig` live rather than storing
 * a snapshot, since /dmarket reload replaces that object — see DynamicMarket.reload().
 */
class GuiManager(
    val plugin: DynamicMarket,
    val manager: MarketManager,
    val trades: MarketTradeService,
    val economy: VaultEconomyService,
    val resolver: ItemResolver,
) {
    val shopConfig get() = plugin.shopConfig
    val menuConfig get() = plugin.menuConfig

    private val warnedUnresolvable = mutableSetOf<String>()

    fun openMainMenu(player: Player) = MainMenuGui(this).open(player)

    fun openCategory(player: Player, category: String) = CategoryGui(this, category).open(player)

    fun openItem(player: Player, item: MarketItem) = ItemTradeGui(this, item).open(player)

    /***
     * items.yml lets a typo become a "tradeable phantom item" nothing validates against the
     * Bukkit registry (see ARCHITECTURE.md). The GUI can't hand a player an AIR stack, so it
     * quietly drops anything unresolvable from menus instead — logged once per name so a bad
     * entry is discoverable without spamming the console on every menu open.
     */
    fun resolveIconOrWarn(name: String, amount: Int = 1): ItemStack? {
        val icon = resolver.resolve(name, amount)
        if (icon == null && warnedUnresolvable.add(name)) {
            plugin.logger.warning("Item '$name' doesn't resolve to a real item or CraftEngine id — hiding it from the shop GUI")
        }
        return icon
    }
}
