package com.example.lcb.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.lcb.app.utils.applySystemBarInsets

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about)

        // 普通内容页整体避让状态栏，以及手势/三键导航栏。
        findViewById<View>(R.id.aboutRoot).applySystemBarInsets()

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        findViewById<TextView>(R.id.appVersion).text = getString(R.string.about_version, version)
    }

    companion object {
        fun newIntent(context: Context) =
            android.content.Intent(context, AboutActivity::class.java)
    }
}
