package me.wuzzyxy.dynamicmarket.market

import me.wuzzyxy.dynamicmarket.configs.ItemConfig
import me.wuzzyxy.dynamicmarket.items.MarketItem
import org.bukkit.ChatColor
import java.util.logging.Logger

/***
 * Copies the definitions out of items.yml onto the live items, inserts anything new, then
 * flushes. YAML owns the static fields; the counters and the decayed position stay put.
 * Runs on boot and again on every reload, so it has to be safe to repeat.
 *
 * An item dropped from items.yml keeps trading on its stored definition — removing a market
 * is deliberately not something editing YAML can do.
 */
fun reconcileWithConfig(config: ItemConfig, manager: MarketManager, logger: Logger) {
    for (definition in config.getAllItems()) {
        val working = manager.getWorkingItem(definition.name)
        if (working == null) {
            logger.info("${ChatColor.GREEN}Adding new item: ${definition.name}")
            manager.addItem(definition)
            continue
        }
        working.copyDefinitionFrom(definition)
    }
    manager.databaseHandler.pushItems()
}

private fun MarketItem.copyDefinitionFrom(definition: MarketItem) {
    basePrice = definition.basePrice
    minPrice = definition.minPrice
    k = definition.k
    halfLifeHours = definition.halfLifeHours
    category = definition.category
}
