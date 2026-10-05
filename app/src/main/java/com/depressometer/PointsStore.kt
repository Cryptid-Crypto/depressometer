package com.depressometer

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Points wallet + cosmetics ownership.
 *
 * Points are earned by scanning while in a good mood (score < 40) and by
 * voluntarily watching rewarded ads. They are spent on cosmetic items
 * (cat skins, badges). Everything is local; nothing leaves the device.
 */
class PointsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ balance

    fun balance(): Int = prefs.getInt(KEY_BALANCE, 0)

    fun addPoints(amount: Int): Int {
        val updated = (balance() + amount).coerceAtLeast(0)
        prefs.edit().putInt(KEY_BALANCE, updated).apply()
        return updated
    }

    private fun deduct(amount: Int): Boolean {
        val b = balance()
        if (b < amount) return false
        prefs.edit().putInt(KEY_BALANCE, b - amount).apply()
        return true
    }

    // ------------------------------------------------------------ ownership

    fun owned(): Set<String> = prefs.getStringSet(KEY_OWNED, emptySet()) ?: emptySet()

    fun owns(id: String): Boolean = Shop.isDefault(id) || owned().contains(id)

    /** Buy an item if affordable. Returns true on success. */
    fun purchase(item: ShopItem): Boolean {
        if (owns(item.id)) return true
        if (!deduct(item.cost)) return false
        val set = owned().toMutableSet().apply { add(item.id) }
        prefs.edit().putStringSet(KEY_OWNED, set).apply()
        return true
    }

    // ------------------------------------------------------------ equipped

    fun equippedSkin(): String = prefs.getString(KEY_SKIN, Shop.SKIN_CLASSIC) ?: Shop.SKIN_CLASSIC

    fun equipSkin(id: String) {
        prefs.edit().putString(KEY_SKIN, id).apply()
    }

    fun equippedBadge(): String? = prefs.getString(KEY_BADGE, null)

    fun equipBadge(id: String?) {
        prefs.edit().putString(KEY_BADGE, id).apply()
    }

    // ------------------------------------------------------------ rewarded ads

    /** How many rewarded ads were watched today (capped per day). */
    fun rewardedAdsToday(): Int {
        val today = todayKey()
        return if (prefs.getString(KEY_AD_DATE, null) == today) prefs.getInt(KEY_AD_COUNT, 0) else 0
    }

    fun canWatchRewardedAd(): Boolean = rewardedAdsToday() < DAILY_AD_CAP

    fun registerRewardedAd(): Int {
        val today = todayKey()
        val count = if (prefs.getString(KEY_AD_DATE, null) == today) {
            prefs.getInt(KEY_AD_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit().putString(KEY_AD_DATE, today).putInt(KEY_AD_COUNT, count).apply()
        return count
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val PREFS = "depressometer_points"
        private const val KEY_BALANCE = "balance"
        private const val KEY_OWNED = "owned"
        private const val KEY_SKIN = "equipped_skin"
        private const val KEY_BADGE = "equipped_badge"
        private const val KEY_AD_DATE = "ad_date"
        private const val KEY_AD_COUNT = "ad_count"

        /** Points awarded for a scan, by mood band. */
        const val POINTS_SCORE_GREAT = 15 // score < 20
        const val POINTS_SCORE_GOOD = 10  // score < 40
        const val POINTS_REWARDED_AD = 25
        const val DAILY_AD_CAP = 6
    }
}
