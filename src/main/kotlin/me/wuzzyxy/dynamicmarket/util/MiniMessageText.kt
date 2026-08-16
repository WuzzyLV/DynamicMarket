package me.wuzzyxy.dynamicmarket.util

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/***
 * Every player-facing template in this plugin (report lines, event messages/badges) is
 * MiniMessage but ends up in places — item lore, chat broadcasts — that want the legacy
 * section-sign format, so it's rendered through both serializers in one place.
 */
object MiniMessageText {
    private val MINI: MiniMessage = MiniMessage.miniMessage()
    private val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

    fun render(template: String, vararg resolvers: TagResolver): String =
        LEGACY.serialize(MINI.deserialize(template, *resolvers))
}
