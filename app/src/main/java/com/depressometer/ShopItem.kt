package com.depressometer

enum class ItemType { CAT_SKIN, BADGE }

data class ShopItem(
    val id: String,
    val type: ItemType,
    val nameRes: Int,
    val cost: Int,
    val imageRes: Int = 0,     // artwork used in the shop / as the mascot skin
    val tint: Int? = null,     // colour wash applied over the mascot art (null = none)
    val badgeRes: Int? = null  // BADGE: title string
    // NFT-ready: when items are tokenised, add e.g.
    //   val nftContract: String? = null,
    //   val nftTokenId: Long? = null
    // and gate ownership on the wallet instead of PointsStore.owns().
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
        ShopItem(
            SKIN_CLASSIC, ItemType.CAT_SKIN, R.string.item_skin_classic, 0,
            imageRes = R.drawable.skin_classic
        ),
        ShopItem(
            SKIN_NEON, ItemType.CAT_SKIN, R.string.item_skin_neon, 80,
            imageRes = R.drawable.skin_neon, tint = 0xFF00E5FF.toInt()
        ),
        ShopItem(
            SKIN_GOLD, ItemType.CAT_SKIN, R.string.item_skin_gold, 150,
            imageRes = R.drawable.skin_gold, tint = 0xFFFFC107.toInt()
        ),
        ShopItem(
            SKIN_MIDNIGHT, ItemType.CAT_SKIN, R.string.item_skin_midnight, 100,
            imageRes = R.drawable.skin_midnight, tint = 0xFF3F51B5.toInt()
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
