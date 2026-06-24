package com.example.lcb.app.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.R

class LanguageAdapter(
    private val languages: List<AppLanguage>,
    private var selectedCode: String,
    private val onClick: (AppLanguage) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = languages.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(languages[position])
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flag: TextView = itemView.findViewById(R.id.langFlag)
        private val native: TextView = itemView.findViewById(R.id.langNative)
        private val english: TextView = itemView.findViewById(R.id.langEnglish)
        private val check: ImageView = itemView.findViewById(R.id.langCheck)

        fun bind(language: AppLanguage) {
            flag.text = language.flag
            native.text = language.nativeName
            english.text = language.englishName
            check.visibility = if (language.code == selectedCode) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(language) }
        }
    }
}
