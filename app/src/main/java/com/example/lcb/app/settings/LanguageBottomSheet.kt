package com.example.lcb.app.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Reusable language picker. Applies the chosen language app-wide via
 * AppCompatDelegate per-app locales (auto-persisted by the framework).
 */
class LanguageBottomSheet : BottomSheetDialogFragment() {

    var onLanguageSelected: ((AppLanguage) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_language, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<RecyclerView>(R.id.languageList)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = LanguageAdapter(
            languages = AppLanguage.entries,
            selectedCode = currentCode()
        ) { language ->
            applyLanguage(language)
            onLanguageSelected?.invoke(language)
            dismiss()
        }
    }

    private fun currentCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales[0]?.language ?: ""
    }

    private fun applyLanguage(language: AppLanguage) {
        val locales = if (language.code.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList() // 跟随系统
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        const val TAG = "LanguageBottomSheet"
    }
}
