package com.example.tgrehber

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatter {

    private const val SEP = "━━━━━━━━━━━"
    private const val MAX_LEN = 3500 // Telegram limiti 4096, güvenli pay bırakıyoruz

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun header(count: Int): String {
        val ts = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        return "📇 <b>Rehber</b> — ${count} kişi · $ts"
    }

    /** Kişi listesini Telegram mesaj limitine sığan parçalara böler. */
    fun buildMessages(header: String, contacts: List<Contact>): List<String> {
        val out = ArrayList<String>()
        var sb = StringBuilder(header).append("\n").append(SEP).append("\n")
        var part = 1

        contacts.forEachIndexed { index, contact ->
            val block = buildString {
                append(index + 1).append(". <b>").append(escape(contact.name)).append("</b>\n")
                contact.numbers.forEach { n ->
                    append("   • <code>").append(escape(n)).append("</code>\n")
                }
                append("\n")
            }

            if (sb.length + block.length > MAX_LEN) {
                out.add(sb.toString().trimEnd())
                part++
                sb = StringBuilder("📇 <b>Rehber (devam $part)</b>\n$SEP\n")
            }
            sb.append(block)
        }

        if (sb.isNotBlank()) out.add(sb.toString().trimEnd())
        return out
    }
}
