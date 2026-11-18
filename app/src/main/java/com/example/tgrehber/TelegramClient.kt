package com.example.tgrehber

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramClient {

    private const val MAX_RETRY = 3

    /**
     * sendMessage çağrısı yapar. Başarısızsa açıklayıcı mesajla exception fırlatır.
     * 429 (rate limit) ve 5xx için otomatik yeniden dener.
     */
    fun sendHtml(token: String, chatId: String, html: String) {
        var attempt = 0

        while (true) {
            attempt++
            val conn = (URL("https://api.telegram.org/bot$token/sendMessage")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val body = buildString {
                append("chat_id=").append(enc(chatId))
                append("&text=").append(enc(html))
                append("&parse_mode=HTML")
                append("&disable_web_page_preview=true")
            }

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()

            if (code in 200..299) return

            val json = runCatching { JSONObject(resp) }.getOrNull()
            val desc = json?.optString("description").orEmpty()
            val retryAfter = json?.optJSONObject("parameters")?.optInt("retry_after", 0) ?: 0

            if (code == 429 && attempt <= MAX_RETRY) {
                Thread.sleep((retryAfter.coerceAtLeast(1) + 1) * 1000L)
                continue
            }
            if (code in 500..599 && attempt <= MAX_RETRY) {
                Thread.sleep(1500L * attempt)
                continue
            }

            throw RuntimeException(
                "Telegram hatası ($code): ${desc.ifBlank { resp.take(200) }}"
            )
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
