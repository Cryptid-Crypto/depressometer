package com.depressometer

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge insets handling.
 *
 * Apps targeting SDK 35+ are drawn behind the status and navigation bars and
 * `android:statusBarColor` is ignored. Without padding the top bar back down, it
 * sits under the status bar and taps on it are consumed by the system UI — which
 * makes the hamburger button appear to stop working.
 */
object SystemBars {

    private fun initialPadding(v: View) =
        intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)

    /** Adds the status-bar inset to each view's top padding, preserving its XML padding. */
    fun padTop(vararg views: View?) = views.filterNotNull().forEach { v ->
        val p = initialPadding(v)
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(p[0], p[1] + bars.top, p[2], p[3])
            insets
        }
    }

    /** Adds the navigation-bar inset to each view's bottom padding, preserving its XML padding. */
    fun padBottom(vararg views: View?) = views.filterNotNull().forEach { v ->
        val p = initialPadding(v)
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(p[0], p[1], p[2], p[3] + bars.bottom)
            insets
        }
    }
}
