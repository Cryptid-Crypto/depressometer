package com.depressometer

enum class ItemType { CAT_SKIN, BADGE }

data class ShopItem(
    val id: String,
    val type: ItemType,
    val nameRes: Int,
    val cost: Int,
    val fur: Int? = null,      // CAT_SKIN: fur colour (null = mood-derived)
    val accent: Int? = null,   // CAT_SKIN: inner-ear / nose tint
    val badgeRes: Int? = null  // BADGE: title string
)

/** Static catalog of cosmetic items purchasable with points. */
object Shop {

    const val SKIN_CLASSIC = "skin_classic"
    const val SKIN_NEON = "skin_neon"
    const val SKIN_GOLD = "skin_gold"
    const val SKIN_MIDNIGHT = "skin_midnight"
    const val BADGE_EXPLORER = "badge_explorer"
    const val BADGE_ZEN = "badge_zen"
    const val BADGE_MASTER = "badge_master"

    val items: List<ShopItem> = listOf(
        ShopItem(SKIN_CLASSIC, ItemType.CAT_SKIN, R.string.item_skin_classic, 0),
        ShopItem(
            SKIN_NEON, ItemType.CAT_SKIN, R.string.item_skin_neon, 80,
            fur = 0xFF00E5FF.toInt(), accent = 0xFFFF2BD6.toInt()
        ),
        ShopItem(
            SKIN_GOLD, ItemType.CAT_SKIN, R.string.item_skin_gold, 150,
            fur = 0xFFFFC107.toInt(), accent = 0xFFB8860B.toInt()
        ),
        ShopItem(
            SKIN_MIDNIGHT, ItemType.CAT_SKIN, R.string.item_skin_midnight, 100,
            fur = 0xFF5C6BC0.toInt(), accent = 0xFF283593.toInt()
        ),
        ShopItem(BADGE_EXPLORER, ItemType.BADGE, R.string.item_badge_explorer, 60,
            badgeRes = R.string.badge_explorer),
        ShopItem(BADGE_ZEN, ItemType.BADGE, R.string.item_badge_zen, 120,
            badgeRes = R.string.badge_zen),
        ShopItem(BADGE_MASTER, ItemType.BADGE, R.string.item_badge_master, 250,
            badgeRes = R.string.badge_master)
    )

    fun byId(id: String?): ShopItem? = items.firstOrNull { it.id == id }

    fun defaultSkin(): ShopItem = items.first { it.id == SKIN_CLASSIC }

    fun isDefault(id: String): Boolean = id == SKIN_CLASSIC
}
