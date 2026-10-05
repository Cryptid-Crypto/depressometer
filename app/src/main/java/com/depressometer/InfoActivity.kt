package com.depressometer

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

/** Privacy statement, disclaimer and research citations. */
class InfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val drawer = findViewById<DrawerLayout>(R.id.drawer)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        NavMenu.setup(this, drawer, navView, R.id.nav_info)
        findViewById<ImageButton>(R.id.btn_nav).setOnClickListener { NavMenu.open(drawer) }

        findViewById<TextView>(R.id.info_text).text = getString(R.string.info_body)
    }
}
