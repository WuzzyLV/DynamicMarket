package me.wuzzyxy.dynamicmarket.gui

import me.wuzzyxy.dynamicmarket.configs.ShopConfig
import me.wuzzyxy.dynamicmarket.economy.VaultEconomyService
import me.wuzzyxy.dynamicmarket.items.prettyItemName
import me.wuzzyxy.dynamicmarket.market.SweepResult
import me.wuzzyxy.dynamicmarket.market.TradeResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Sound
import org.bukkit.entity.Player

private val MINI: MiniMessage = MiniMessage.miniMessage()

private fun Player.sendMini(template: String, vararg resolvers: TagResolver) {
    sendMessage(MINI.deserialize(template, *resolvers))
}

private fun Player.playFeedback(config: ShopConfig, sound: Sound, pitch: Float = 1f) {
    if (config.SOUNDS_ENABLED) playSound(location, sound, 1f, pitch)
}

/*** Chat + sound feedback for a single buy/sell/sell-all click. Shared so every GUI screen reports trades identically. */
fun Player.announce(result: TradeResult, economy: VaultEconomyService, config: ShopConfig) {
    when (result) {
        is TradeResult.Bought -> {
            sendMini(
                "<green>Bought <white><amount> <green>x <white><item> <green>for <gold><price>",
                Placeholder.unparsed("amount", result.amount.toString()),
                Placeholder.unparsed("item", prettyItemName(result.item.name)),
                Placeholder.unparsed("price", economy.format(result.price)),
            )
            playFeedback(config, Sound.ENTITY_VILLAGER_YES, 1.3f)
        }

        is TradeResult.Sold -> {
            sendMini(
                "<green>Sold <white><amount> <green>x <white><item> <green>for <gold><price>",
                Placeholder.unparsed("amount", result.amount.toString()),
                Placeholder.unparsed("item", prettyItemName(result.item.name)),
                Placeholder.unparsed("price", economy.format(result.price)),
            )
            playFeedback(config, Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
        }

        is TradeResult.Denied -> {
            sendMini("<red><reason>", Placeholder.unparsed("reason", result.reason))
            playFeedback(config, Sound.ENTITY_VILLAGER_NO)
        }
    }
}

/*** Chat + sound feedback for "sell everything" — an itemised breakdown, not just a total. */
fun Player.announceSweep(result: SweepResult, economy: VaultEconomyService, config: ShopConfig) {
    if (result.denied != null) {
        sendMini("<red><reason>", Placeholder.unparsed("reason", result.denied))
        playFeedback(config, Sound.ENTITY_VILLAGER_NO)
        return
    }

    if (result.lines.isEmpty()) {
        sendMini("<yellow>You aren't carrying anything this market buys.")
        playFeedback(config, Sound.ENTITY_VILLAGER_NO)
        return
    }

    // One message, not one per line — a sweep across a full items.yml was otherwise thirty-odd
    // chat packets for a single click. Still deserialized per line so prices keep going through
    // unparsed and an economy plugin's formatting can't be read as MiniMessage.
    val breakdown = Component.text().append(MINI.deserialize("<gold><bold>Sold everything:"))
    for (line in result.lines) {
        breakdown.append(Component.newline()).append(
            MINI.deserialize(
                "  <gray>- <white><amount> <gray>x <white><item> <gray>» <gold><price>",
                Placeholder.unparsed("amount", line.amount.toString()),
                Placeholder.unparsed("item", prettyItemName(line.item.name)),
                Placeholder.unparsed("price", economy.format(line.price)),
            )
        )
    }
    breakdown.append(Component.newline()).append(
        MINI.deserialize("<green>Total: <gold><total>", Placeholder.unparsed("total", economy.format(result.total)))
    )

    sendMessage(breakdown.build())
    playFeedback(config, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f)
}
