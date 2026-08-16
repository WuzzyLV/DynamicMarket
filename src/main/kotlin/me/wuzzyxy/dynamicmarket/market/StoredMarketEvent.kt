package me.wuzzyxy.dynamicmarket.market

/***
 * One fired event as it round-trips through the database. itemDeltas is what each
 * targeted item's net position was shocked by — enough on its own to work out how much
 * of the event is still "live" later, since net decays the same way regardless of when
 * or why a contribution to it was added (see MarketEventManager).
 */
data class StoredMarketEvent(
    val eventId: Long,
    val definitionId: String,
    val scope: String,
    val target: String?,
    val direction: String,
    val multiplier: Double,
    val triggeredAt: Long,
    val itemDeltas: Map<String, Double>,
)
