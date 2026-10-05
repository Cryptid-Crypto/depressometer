package com.depressometer

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView

/**
 * Points wallet + shop. Earn points by scanning in a good mood (< 40) or by
 * voluntarily watching a rewarded ad, then spend them on cat skins and badges.
 */
class PointsActivity : AppCompatActivity() {

    private lateinit var store: PointsStore
    private lateinit var balanceView: TextView
    private lateinit var watchButton: Button
    private lateinit var adStatus: TextView
    private lateinit var shopContainer: LinearLayout
    private lateinit var ads: RewardedAdManager

    private var pendingShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points)

        val drawer = findViewById<DrawerLayout>(R.id.drawer)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        NavMenu.setup(this, drawer, navView, R.id.nav_points)
        findViewById<ImageButton>(R.id.btn_nav).setOnClickListener { NavMenu.open(drawer) }

        MobileAds.initialize(this)
        store = PointsStore(this)

        balanceView = findViewById(R.id.points_balance)
        watchButton = findViewById(R.id.btn_watch_ad)
        adStatus = findViewById(R.id.ad_status)
        shopContainer = findViewById(R.id.shop_container)

        ads = RewardedAdManager(
            activity = this,
            onReward = { amount ->
                store.addPoints(amount)
                store.registerRewardedAd()
                toast(getString(R.string.points_ad_reward_earned, amount))
                refresh()
                updateAdUi()
            },
            onStateChanged = {
                updateAdUi()
                if (pendingShow && ads.isReady()) {
                    pendingShow = false
                    ads.show()
                }
            }
        )
        ads.preload()

        watchButton.setOnClickListener { onWatchAdClicked() }

        refresh()
        updateAdUi()
    }

    override fun onResume() {
        super.onResume()
        ads.preload()
    }

    private fun onWatchAdClicked() {
        if (!store.canWatchRewardedAd()) {
            toast(getString(R.string.points_ad_cap))
            return
        }
        when {
            ads.isReady() -> ads.show()
            ads.isLoading() -> toast(getString(R.string.points_ad_loading))
            else -> {
                pendingShow = true
                ads.preload()
                toast(getString(R.string.points_ad_loading))
            }
        }
    }

    private fun updateAdUi() {
        val remaining = (PointsStore.DAILY_AD_CAP - store.rewardedAdsToday()).coerceAtLeast(0)
        watchButton.isEnabled = remaining > 0
        adStatus.text = when {
            remaining <= 0 -> getString(R.string.points_ad_cap)
            ads.isReady() -> getString(R.string.points_ad_ready, remaining)
            else -> getString(R.string.points_ad_remaining, remaining)
        }
    }

    private fun refresh() {
        balanceView.text = store.balance().toString()
        renderShop()
    }

    // ---------------------------------------------------------------- shop

    private fun renderShop() {
        shopContainer.removeAllViews()
        for (item in Shop.items) {
            shopContainer.addView(buildRow(item))
        }
    }

    private fun buildRow(item: ShopItem): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Artwork preview for cat skins
        if (item.imageRes != 0) {
            val preview = android.widget.ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                setImageResource(item.imageRes)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            row.addView(preview)
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        col.addView(TextView(this).apply {
            text = getString(item.nameRes)
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = if (item.cost == 0) getString(R.string.points_free)
            else getString(R.string.points_cost, item.cost)
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 13f
        })
        row.addView(col)

        val equipped = isEquipped(item)
        val owned = store.owns(item.id)

        val btn = Button(this).apply {
            text = when {
                equipped -> getString(R.string.points_equipped)
                owned -> getString(R.string.points_equip)
                else -> getString(R.string.points_buy)
            }
            textSize = 13f
            isEnabled = !equipped
        }
        btn.setOnClickListener { onItemAction(item, equipped, owned) }
        row.addView(btn)

        return row
    }

    private fun isEquipped(item: ShopItem): Boolean = when (item.type) {
        ItemType.CAT_SKIN -> store.equippedSkin() == item.id
        ItemType.BADGE -> store.equippedBadge() == item.id
    }

    private fun onItemAction(item: ShopItem, equipped: Boolean, owned: Boolean) {
        when {
            equipped -> {
                // Allow removing an equipped badge (skins always stay equipped).
                if (item.type == ItemType.BADGE) {
                    store.equipBadge(null)
                    refresh()
                }
            }
            owned -> {
                when (item.type) {
                    ItemType.CAT_SKIN -> store.equipSkin(item.id)
                    ItemType.BADGE -> store.equipBadge(item.id)
                }
                refresh()
            }
            else -> {
                if (store.purchase(item)) {
                    toast(getString(R.string.points_purchased, getString(item.nameRes)))
                    when (item.type) {
                        ItemType.CAT_SKIN -> store.equipSkin(item.id)
                        ItemType.BADGE -> store.equipBadge(item.id)
                    }
                    refresh()
                } else {
                    toast(getString(R.string.points_not_enough))
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
