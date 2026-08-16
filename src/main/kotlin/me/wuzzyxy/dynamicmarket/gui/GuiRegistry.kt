package me.wuzzyxy.dynamicmarket.gui

import xyz.xenondevs.invui.item.Item
import java.util.UUID

/***
 * Which open windows are currently showing which market item's price, so a trade can push a
 * refresh to every other viewer the instant it happens instead of leaving them looking at a
 * stale price until their own next click.
 *
 * A player can only have one window open at a time, so registering a session always replaces
 * whatever this player had open before, and closing a window (or opening one that doesn't
 * show live prices, like the home screen) clears it — see the handlers in
 * ItemTradeGui/CategoryGui.
 */
class GuiRegistry {

    private class Session(val itemNames: Set<String>, val liveItems: List<Item>)

    private val sessions = mutableMapOf<UUID, Session>()

    fun register(player: UUID, itemNames: Set<String>, liveItems: List<Item>) {
        sessions[player] = Session(itemNames, liveItems)
    }

    /***
     * [liveItems] says which screen is asking. InvUI closes the outgoing window from inside
     * the incoming one's open(), so a close handler that cleared unconditionally would wipe
     * whatever the screen being opened had just registered — only the screen that still owns
     * the session gets to drop it.
     */
    fun unregister(player: UUID, liveItems: List<Item>) {
        if (sessions[player]?.liveItems === liveItems) sessions.remove(player)
    }

    /*** Refreshes every open window showing [itemName], other than [exclude] (which already refreshed itself). */
    fun refresh(itemName: String, exclude: UUID? = null) = refresh(setOf(itemName), exclude)

    fun refresh(itemNames: Collection<String>, exclude: UUID? = null) {
        if (itemNames.isEmpty()) return
        for ((player, session) in sessions) {
            if (player == exclude) continue
            if (session.itemNames.any { it in itemNames }) session.liveItems.forEach(Item::notifyWindows)
        }
    }
}
