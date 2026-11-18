package com.example.tgrehber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private companion object {
        const val KEY_TOKEN = "bot_token"
        const val KEY_CHAT = "chat_id"
        const val PERM = Manifest.permission.READ_CONTACTS
    }

    private lateinit var etToken: EditText
    private lateinit var etChat: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnSend: Button
    private lateinit var btnTest: Button
    private lateinit var btnPreview: Button

    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSend() else setStatus("Rehber izni verilmedi.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etToken = findViewById(R.id.etToken)
        etChat = findViewById(R.id.etChat)
        tvStatus = findViewById(R.id.tvStatus)
        btnSend = findViewById(R.id.btnSend)
        btnTest = findViewById(R.id.btnTest)
        btnPreview = findViewById(R.id.btnPreview)

        // İlk açılışta Config içindeki gömülü değerler gelir,
        // kullanıcı değiştirirse kaydedilen değer kullanılır.
        etToken.setText(prefs.getString(KEY_TOKEN, null) ?: Config.DEFAULT_TOKEN)
        etChat.setText(prefs.getString(KEY_CHAT, null) ?: Config.DEFAULT_CHAT_ID)

        btnSend.setOnClickListener { ensurePermission() }
        btnTest.setOnClickListener { sendTest() }
        btnPreview.setOnClickListener { preview() }
    }

    private fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(this, PERM) == PackageManager.PERMISSION_GRANTED) {
            startSend()
        } else {
            permLauncher.launch(PERM)
        }
    }

    private fun configOrNull(): Pair<String, String>? {
        val token = etToken.text.toString().trim().ifBlank { Config.DEFAULT_TOKEN }
        val chat = etChat.text.toString().trim().ifBlank { Config.DEFAULT_CHAT_ID }

        if (token.isBlank() || chat.isBlank()) {
            setStatus("Bot token ve chat id gerekli.")
            return null
        }
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_CHAT, chat).apply()
        return token to chat
    }

    private fun preview() {
        setBusy(true)
        setStatus("Rehber okunuyor...")
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val contacts = ContactsRepository.readAll(this@MainActivity)
                    val msgs = Formatter.buildMessages(Formatter.header(contacts.size), contacts)
                    "Toplam ${contacts.size} kişi → ${msgs.size} mesaj.\nİlk mesajın önizlemesi:\n\n" +
                        msgs.firstOrNull().orEmpty().take(400)
                }.getOrElse { "Hata: ${it.message}" }
            }
            setStatus(text)
            setBusy(false)
        }
    }

    private fun sendTest() {
        val (token, chat) = configOrNull() ?: return
        setBusy(true)
        setStatus("Test mesajı gönderiliyor...")
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    TelegramClient.sendHtml(
                        token,
                        chat,
                        "✅ <b>Bağlantı testi</b>\nUygulama botuna erişebiliyor."
                    )
                    "Test mesajı ulaştı."
                }.getOrElse { "Hata: ${it.message}" }
            }
            setStatus(text)
            setBusy(false)
        }
    }

    private fun startSend() {
        val (token, chat) = configOrNull() ?: return
        setBusy(true)
        setStatus("Rehber okunuyor ve gönderiliyor...")

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val contacts = ContactsRepository.readAll(this@MainActivity)
                    if (contacts.isEmpty()) return@runCatching "Rehberde numaralı kişi bulunamadı."

                    val messages = Formatter.buildMessages(Formatter.header(contacts.size), contacts)
                    messages.forEachIndexed { i, msg ->
                        TelegramClient.sendHtml(token, chat, msg)
                        if (i < messages.lastIndex) Thread.sleep(1200) // rate limit dostu
                    }
                    "${contacts.size} kişi, ${messages.size} mesaj olarak gönderildi."
                }.getOrElse { "Hata: ${it.message}" }
            }
            setStatus(text)
            setBusy(false)
        }
    }

    private fun setStatus(s: String) {
        runOnUiThread { tvStatus.text = s }
    }

    private fun setBusy(busy: Boolean) {
        runOnUiThread {
            btnSend.isEnabled = !busy
            btnTest.isEnabled = !busy
            btnPreview.isEnabled = !busy
        }
    }
}
