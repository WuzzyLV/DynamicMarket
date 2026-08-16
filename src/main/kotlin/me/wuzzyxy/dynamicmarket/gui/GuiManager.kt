package me.wuzzyxy.dynamicmarket.gui

import io.papermc.paper.datacomponent.DataComponentTypes
import me.wuzzyxy.dynamicmarket.DynamicMarket
import me.wuzzyxy.dynamicmarket.configs.MAX_ICON_COUNT
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
    val registry = GuiRegistry()

    private val warned = mutableSetOf<String>()

    fun openMainMenu(player: Player) = MainMenuGui(this).open(player)

    fun openCategory(player: Player, category: String) = CategoryGui(this, category).open(player)

    fun openItem(player: Player, item: MarketItem) = ItemTradeGui(this, item).open(player)

    /*** Menus rebuild on every open, so anything wrong with the config would otherwise be logged once per click. */
    fun warnOnce(key: String, message: String) {
        if (warned.add(key)) plugin.logger.warning(message)
    }

    /***
     * items.yml lets a typo become a "tradeable phantom item" nothing validates against the
     * Bukkit registry (see ARCHITECTURE.md). The GUI can't hand a player an AIR stack, so it
     * quietly drops anything unresolvable from menus instead — logged once per name so a bad
     * entry is discoverable without spamming the console on every menu open.
     *
     * A quantity tier past what the item normally stacks to only draws its real count if the icon
     * says it can stack that high, so the icon gets a max_stack_size to match. That component tops
     * out at 99 and can't coexist with max_damage, so a bigger tier or a damageable icon is left
     * to render however the client wants — which is why every quantity tier's name carries
     * %amount% too. This is display only: MarketTradeService builds the stacks players actually
     * receive straight off ItemResolver, untouched.
     */
    fun resolveIconOrWarn(name: String, amount: Int = 1): ItemStack? {
        val icon = resolver.resolve(name, amount)
        if (icon == null) {
            warnOnce(name, "Item '$name' doesn't resolve to a real item or CraftEngine id — hiding it from the shop GUI")
            return null
        }
        if (amount > icon.maxStackSize && !icon.hasData(DataComponentTypes.MAX_DAMAGE)) {
            icon.setData(DataComponentTypes.MAX_STACK_SIZE, amount.coerceAtMost(MAX_ICON_COUNT))
        }
        return icon
    }
}
