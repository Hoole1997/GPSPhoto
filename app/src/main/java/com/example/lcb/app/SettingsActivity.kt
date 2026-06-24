package com.example.lcb.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lcb.app.settings.AppLanguage
import com.example.lcb.app.settings.LanguageBottomSheet
import com.example.lcb.app.utils.loadNative

class SettingsActivity : AppCompatActivity() {

    private lateinit var languageValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        languageValue = findViewById(R.id.languageValue)
        updateLanguageValue()

        findViewById<View>(R.id.rowLanguage).setOnClickListener {
            val sheet = LanguageBottomSheet()
            sheet.onLanguageSelected = { updateLanguageValue() }
            sheet.show(supportFragmentManager, LanguageBottomSheet.TAG)
        }
        findViewById<View>(R.id.rowPrivacy).setOnClickListener { openPrivacy() }
        findViewById<View>(R.id.rowAbout).setOnClickListener {
            startActivity(AboutActivity.newIntent(this))
        }

        // 设置页底部原生广告
        loadNative(container = findViewById(R.id.adContainer))
    }

    private fun updateLanguageValue() {
        val locales = AppCompatDelegate.getApplicationLocales()
        val code = if (locales.isEmpty) "" else locales[0]?.language ?: ""
        languageValue.text = if (code.isEmpty()) {
            getString(R.string.settings_language_system)
        } else {
            AppLanguage.fromCode(code).nativeName
        }
    }

    private fun openPrivacy() {
        try {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(PRIVACY_URL)
                )
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        private const val PRIVACY_URL = "https://bioluminescents.com/privacy.html"

        fun newIntent(context: Context) =
            android.content.Intent(context, SettingsActivity::class.java)
    }
}
