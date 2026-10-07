package com.depressometer

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Thin wrapper around an AdMob rewarded ad.
 *
 * NOTE: the ad unit id comes from `BuildConfig.ADMOB_REWARDED_UNIT`, which is injected
 * from gradle (`admobRewardedUnit`, see gradle.properties). Defaults to Google's
 * official test unit id so the app works out of the box; replace it before publishing.
 */
class RewardedAdManager(
    private val activity: Activity,
    private val onReward: (Int) -> Unit,
    private val onStateChanged: () -> Unit
) {

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    fun isReady(): Boolean = rewardedAd != null

    fun isLoading(): Boolean = loading

    fun preload() {
        if (rewardedAd != null || loading) return
        loading = true
        onStateChanged()
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_UNIT,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                    onStateChanged()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    loading = false
                    onStateChanged()
                }
            }
        )
    }

    /** Shows the ad if ready; calls [onReward] once the user earns it. */
    fun show() {
        val ad = rewardedAd
        if (ad == null) {
            onStateChanged()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onStateChanged()
                preload() // get the next one ready
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                onStateChanged()
            }
        }
        ad.show(activity) { rewardItem ->
            onReward(rewardItem.amount.coerceAtLeast(1).let { REWARD_POINTS })
        }
    }

    companion object {
        private const val TAG = "RewardedAd"

        // Google's official test rewarded ad unit — used only as the gradle default.
        const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"
        const val REWARD_POINTS = PointsStore.POINTS_REWARDED_AD
    }
}
