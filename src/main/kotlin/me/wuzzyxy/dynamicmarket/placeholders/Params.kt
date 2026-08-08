package me.wuzzyxy.dynamicmarket.placeholders

/***
 * String.split in Java drops trailing empties and Kotlin's keeps them, so a stray comma
 * in a menu config used to be tolerated and would otherwise start reading as an extra
 * argument.
 */
internal fun String.splitParams(): Array<String> =
    split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
