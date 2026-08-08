package me.wuzzyxy.dynamicmarket.items

/***
 * cobbled_deepslate reads badly in a lore line, and a CraftEngine id's namespace is noise to
 * a player. Shared by the report placeholders and the shop GUI so the two never drift.
 */
fun prettyItemName(rawName: String): String =
    rawName.substringAfter(':')
        .split('_')
        .filter(String::isNotEmpty)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
