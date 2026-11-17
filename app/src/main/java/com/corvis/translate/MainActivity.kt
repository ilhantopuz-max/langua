package com.corvis.translate

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

data class Lang(val code: String, val name: String, val locale: String)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val langs = listOf(
        Lang("tr", "Türkçe", "tr-TR"), Lang("en", "İngilizce", "en-US"),
        Lang("de", "Almanca", "de-DE"), Lang("fr", "Fransızca", "fr-FR"),
        Lang("it", "İtalyanca", "it-IT"), Lang("es", "İspanyolca", "es-ES"),
        Lang("ru", "Rusça", "ru-RU"), Lang("ka", "Gürcüce", "ka-GE"),
        Lang("sq", "Arnavutça", "sq-AL"), Lang("zh-CN", "Çince", "zh-CN"),
        Lang("ko", "Korece", "ko-KR"), Lang("nl", "Hollandaca", "nl-NL"),
        Lang("af", "Afrikaans", "af-ZA"), Lang("sw", "Svahili", "sw-KE"),
        Lang("zu", "Zulu", "zu-ZA"), Lang("xh", "Xhosa", "xh-ZA"),
        Lang("yo", "Yoruba", "yo-NG"), Lang("ig", "Igbo", "ig-NG"),
        Lang("ha", "Hausa", "ha-NG"), Lang("am", "Amharca", "am-ET"),
        Lang("so", "Somalice", "so-SO"), Lang("sn", "Shona", "sn-ZW")
    )

    private lateinit var spA: Spinner
    private lateinit var spB: Spinner
    private lateinit var etInput: EditText
    private lateinit var tvResult: TextView
    private lateinit var tvHistory: TextView

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var micSide = 0 // 0: sol->sağ, 1: sağ->sol
    private var lastText = ""
    private var lastLang: Lang? = null
    private val io = Executors.newSingleThreadExecutor()
    private val history = ArrayList<String>()

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (r.resultCode == RESULT_OK && !text.isNullOrBlank()) {
                etInput.setText(text)
                if (micSide == 0) translate(text, langA(), langB(), true)
                else translate(text, langB(), langA(), true)
            }
        }

    private fun langA() = langs[spA.selectedItemPosition]
    private fun langB() = langs[spB.selectedItemPosition]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        spA = findViewById(R.id.spA); spB = findViewById(R.id.spB)
        etInput = findViewById(R.id.etInput)
        tvResult = findViewById(R.id.tvResult); tvHistory = findViewById(R.id.tvHistory)

        val names = langs.map { it.name }
        for (sp in listOf(spA, spB)) {
            val ad = ArrayAdapter(this, R.layout.spinner_item, names)
            ad.setDropDownViewResource(R.layout.spinner_dropdown)
            sp.adapter = ad
        }
        val prefs = getSharedPreferences("corvis", MODE_PRIVATE)
        spA.setSelection(prefs.getInt("a", 0)); spB.setSelection(prefs.getInt("b", 1))
        val saveSel = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putInt("a", spA.selectedItemPosition).putInt("b", spB.selectedItemPosition).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spA.onItemSelectedListener = saveSel; spB.onItemSelectedListener = saveSel

        tts = TextToSpeech(this, this)
        loadHistory()

        findViewById<Button>(R.id.btnSwap).setOnClickListener {
            val a = spA.selectedItemPosition
            spA.setSelection(spB.selectedItemPosition); spB.setSelection(a)
        }
        findViewById<Button>(R.id.btnMicA).setOnClickListener { listen(0) }
        findViewById<Button>(R.id.btnMicB).setOnClickListener { listen(1) }
        findViewById<Button>(R.id.btnTranslate).setOnClickListener {
            val t = etInput.text.toString().trim()
            if (t.isEmpty()) toast("Önce bir metin yazın veya konuşun") else translate(t, langA(), langB(), false)
        }
        findViewById<Button>(R.id.btnSpeak).setOnClickListener {
            val l = lastLang
            if (lastText.isNotEmpty() && l != null) speak(lastText, l) else toast("Henüz çeviri yok")
        }
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            if (lastText.isEmpty()) { toast("Henüz çeviri yok"); return@setOnClickListener }
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Corvis", lastText))
            toast("Kopyalandı")
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            history.clear(); saveHistory(); tvHistory.text = ""
        }
    }

    private fun listen(side: Int) {
        micSide = side
        val l = if (side == 0) langA() else langB()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, l.locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "${l.name} konuşun…")
        }
        try { speechLauncher.launch(i) }
        catch (e: ActivityNotFoundException) { toast("Bu cihazda konuşma tanıma yok (Google uygulamasını kurun/güncelleyin)") }
    }

    private fun online(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun translate(text: String, from: Lang, to: Lang, speakIt: Boolean) {
        if (text.length > 1500) { toast("Metin çok uzun (en fazla 1500 karakter)"); return }
        if (!online()) { toast("İnternet bağlantısı yok"); return }
        tvResult.text = "Çevriliyor…"
        io.execute {
            try {
                val out = fetch(text, from.code, to.code)
                runOnUiThread {
                    tvResult.text = out; lastText = out; lastLang = to
                    addHistory("${from.name} → ${to.name}\n$text\n⇒ $out")
                    if (speakIt) speak(out, to)
                }
            } catch (e: Exception) {
                runOnUiThread { tvResult.text = ""; toast("Çeviri hatası: ${e.message}") }
            }
        }
    }

    private fun fetch(text: String, sl: String, tl: String): String {
        val u = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=" +
                URLEncoder.encode(text, "UTF-8"))
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val parts = JSONArray(body).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONArray(i)
                if (!p.isNull(0)) sb.append(p.getString(0))
            }
            return sb.toString()
        } finally { c.disconnect() }
    }

    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS }

    private fun speak(text: String, l: Lang) {
        val t = tts
        if (t == null || !ttsReady) { toast("Ses motoru hazır değil"); return }
        val r = t.setLanguage(Locale.forLanguageTag(l.locale))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("${l.name} için cihazda ses (TTS) yok; Ayarlar > Metinden sese bölümünden indirin")
            return
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "corvis")
    }

    private fun addHistory(s: String) {
        history.add(0, s); while (history.size > 30) history.removeAt(history.size - 1)
        saveHistory(); renderHistory()
    }
    private fun renderHistory() { tvHistory.text = history.joinToString("\n\n──────────\n\n") }
    private fun saveHistory() {
        getSharedPreferences("corvis", MODE_PRIVATE).edit()
            .putString("h", JSONArray(history).toString()).apply()
    }
    private fun loadHistory() {
        try {
            val a = JSONArray(getSharedPreferences("corvis", MODE_PRIVATE).getString("h", "[]"))
            for (i in 0 until a.length()) history.add(a.getString(i))
        } catch (_: Exception) {}
        renderHistory()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); io.shutdown(); super.onDestroy() }
}
