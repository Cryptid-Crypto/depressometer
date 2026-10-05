package com.depressometer

import android.content.Intent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

/** Shared navigation drawer wiring used by every screen. */
object NavMenu {

    private const val ID_REMIND = 99

    fun setup(
        activity: AppCompatActivity,
        drawer: DrawerLayout,
        nav: NavigationView,
        currentItemId: Int,
        onRemind: (() -> Unit)? = null
    ) {
        nav.setCheckedItem(currentItemId)

        // Use the generated cat logo in the drawer header when available.
        val logoId = activity.resources.getIdentifier("cat_logo", "drawable", activity.packageName)
        if (logoId != 0) {
            nav.getHeaderView(0).findViewById<ImageView>(R.id.nav_header_cat)
                ?.setImageResource(logoId)
        }

        if (onRemind != null) {
            nav.menu.add(0, ID_REMIND, 10, activity.getString(R.string.nav_remind))
                .setIcon(R.drawable.ic_nav_remind)
        }

        nav.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan -> if (currentItemId != R.id.nav_scan) {
                    activity.startActivity(
                        Intent(activity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
                R.id.nav_history -> if (currentItemId != R.id.nav_history) {
                    activity.startActivity(Intent(activity, HistoryActivity::class.java))
                }
                R.id.nav_points -> if (currentItemId != R.id.nav_points) {
                    activity.startActivity(Intent(activity, PointsActivity::class.java))
                }
                R.id.nav_info -> if (currentItemId != R.id.nav_info) {
                    activity.startActivity(Intent(activity, InfoActivity::class.java))
                }
                ID_REMIND -> onRemind?.invoke()
            }
            drawer.closeDrawers()
            true
        }
    }

    fun open(drawer: DrawerLayout) = drawer.openDrawer(GravityCompat.START)
}
