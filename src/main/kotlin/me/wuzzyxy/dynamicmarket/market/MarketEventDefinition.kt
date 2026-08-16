package me.wuzzyxy.dynamicmarket.market

enum class EventScope { ITEM, CATEGORY, GLOBAL }

enum class EventDirection { BOOM, BUST, BOTH }

/***
 * One entry from events.yml. `targets` is the pool of item/category names a
 * scope: item / scope: category definition rolls a target from — unused (and normally
 * left out of the config) for scope: global, which always hits every working item.
 */
data class MarketEventDefinition(
    val id: String,
    val weight: Int,
    val scope: EventScope,
    val targets: List<String>,
    val direction: EventDirection,
    val multiplierMin: Double,
    val multiplierMax: Double,
    val message: String,
    val badge: List<String>,
)
