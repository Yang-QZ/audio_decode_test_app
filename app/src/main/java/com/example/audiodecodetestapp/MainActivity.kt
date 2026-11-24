package com.example.audiodecodetestapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.audiodecodetestapp.ui.about.AboutFragment
import com.example.audiodecodetestapp.ui.codecs.CodecsFragment
import com.example.audiodecodetestapp.ui.files.FilesFragment
import com.example.audiodecodetestapp.ui.home.HomeFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ 强制 edge-to-edge，这里手动处理系统栏 insets，避免顶部内容被状态栏遮挡
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_test -> show(HomeFragment(), getString(R.string.nav_test))
                R.id.nav_files -> show(FilesFragment(), getString(R.string.nav_files))
                R.id.nav_codecs -> show(CodecsFragment(), getString(R.string.nav_codecs))
                R.id.nav_about -> show(AboutFragment(), getString(R.string.nav_about))
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_test
        }
    }

    private fun show(fragment: Fragment, title: String): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        supportActionBar?.title = title
        return true
    }
}
