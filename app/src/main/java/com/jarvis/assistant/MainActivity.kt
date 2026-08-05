package com.jarvis.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import net.objecthunter.exp4j.ExpressionBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import java.util.*
import android.provider.ContactsContract
import android.provider.AlarmClock
import android.app.SearchManager
import android.os.BatteryManager
import android.media.AudioManager
import android.net.Uri
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var flashOn = false
    private var continuousMode = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLangCode = "ar"
    private var pulseAnimator: ObjectAnimator? = null
    private var userName: String = ""
    private var lectureMode = false
    private var lectureBuffer = StringBuilder()
    private val client = OkHttpClient()

    // ---- Ø¶ÙŠÙ Ù…ÙØªØ§Ø­ Google Gemini Ø§Ù„Ø®Ø§Øµ ÙÙŠÙƒ Ù‡ÙˆÙ† Ø¨ÙŠÙ† Ø¹Ù„Ø§Ù…ØªÙŠ Ø§Ù„ØªÙ†ØµÙŠØµ ----
    // Ø§Ø­ØµÙ„ Ø¹Ù„ÙŠÙ‡ Ù…Ø¬Ø§Ù†Ù‹Ø§ Ù…Ù†: https://aistudio.google.com/apikey
    // Ø®Ù„ÙŠÙ‡ ÙØ§Ø¶ÙŠ "" Ø¥Ø°Ø§ Ø¨Ø¯Ùƒ ØªØ¨Ù‚ÙŠ Ø¬Ø§Ø±ÙØ³ Ø£ÙˆÙÙ„Ø§ÙŠÙ† Ø¨Ø§Ù„ÙƒØ§Ù…Ù„
    private val GEMINI_API_KEY = "AQ.Ab8RN6JAWNvpqDQDaeRnpIWYKL8-7q_ENOjLPB8iMt__-l5jPA"

    // ---- Ø¶ÙŠÙ Ù…ÙØªØ§Ø­ Google Maps Ù‡ÙˆÙ† Ù„Ù…Ø³Ø§ÙØ§Øª Ø­Ù‚ÙŠÙ‚ÙŠØ© Ø¨Ø§Ù„Ø·Ø±ÙŠÙ‚ ----
    // Ø§Ø­ØµÙ„ Ø¹Ù„ÙŠÙ‡ Ù…Ù†: https://console.cloud.google.com/google/maps-apis
    // Ø®Ù„ÙŠÙ‡ ÙØ§Ø¶ÙŠ "" Ø¥Ø°Ø§ Ø¨Ø¯Ùƒ ÙŠØ³ØªØ®Ø¯Ù… Ø­Ø³Ø§Ø¨ ØªÙ‚Ø±ÙŠØ¨ÙŠ (Ø®Ø· Ù…Ø³ØªÙ‚ÙŠÙ…) Ø¨Ø¯ÙˆÙ† Ù…ÙØªØ§Ø­
    private val GOOGLE_MAPS_API_KEY = ""

    companion object {
        private const val REQ_SPEECH = 100
        private const val REQ_PERMISSIONS = 200
        private const val REQ_CONTACTS = 300
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        tts = TextToSpeech(this, this)

        userName = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("user_name", "") ?: ""
        if (userName.isNotBlank()) {
            log("Ø¬Ø§Ø±ÙØ³: Ø£Ù‡Ù„Ø§ $userNameØŒ Ù…Ø¨Ø³ÙˆØ· Ø¥Ù†Ùƒ Ø±Ø¬Ø¹Øª")
        }

        requestNeededPermissions()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(recognitionListener)

        findViewById<Button>(R.id.micButton).setOnClickListener {
            toggleContinuousMode(it as Button)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            enableContinuousMode()
        }
    }

    private fun toggleContinuousMode(button: Button) {
        if (continuousMode) {
            disableContinuousMode(button)
        } else {
            enableContinuousMode()
        }
    }

    private fun enableContinuousMode() {
        continuousMode = true
        val button = findViewById<Button>(R.id.micButton)
        button.text = "â¹ï¸"
        statusText.text = "Ø¨Ø³Ù…Ø¹Ùƒ... Ù‚ÙˆÙ„ \"Ø¬Ø§Ø±ÙØ³\""
        findViewById<View>(R.id.statusDot).setBackgroundResource(R.drawable.status_dot)
        findViewById<View>(R.id.statusDot).alpha = 1f
        startPulseAnimation(button)
        startListening()
    }

    private fun disableContinuousMode(button: Button) {
        continuousMode = false
        button.text = "ðŸŽ™ï¸"
        statusText.text = "Ø¬Ø§Ù‡Ø² Ù„Ù„Ø§Ø³ØªÙ…Ø§Ø¹"
        findViewById<View>(R.id.statusDot).alpha = 0.3f
        stopPulseAnimation(button)
        log("ØªÙˆÙ‚Ù ÙˆØ¶Ø¹ Ø§Ù„Ø§Ø³ØªÙ…Ø§Ø¹ Ø§Ù„Ù…Ø³ØªÙ…Ø±")
    }

    private fun startPulseAnimation(view: View) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
        ).apply {
            duration = 700
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulseAnimation(view: View) {
        pulseAnimator?.cancel()
        view.scaleX = 1f
        view.scaleY = 1f
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ar")
            tts.setPitch(0.7f)
            tts.setSpeechRate(0.95f)
            val arabicVoices = tts.voices?.filter { it.locale.language == "ar" }
            val maleVoice = arabicVoices?.firstOrNull { voice ->
                val n = voice.name.lowercase(Locale.ROOT)
                (n.contains("male") && !n.contains("female")) ||
                        n.contains("-d-") || n.contains("#male")
            }
            if (maleVoice != null) {
                tts.voice = maleVoice
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        for (p in listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val audioIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
            if (audioIndex != -1 && grantResults.getOrNull(audioIndex) == PackageManager.PERMISSION_GRANTED &&
                !continuousMode
            ) {
                enableContinuousMode()
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLangCode)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            continuousMode = false
            val button = findViewById<Button>(R.id.micButton)
            button.text = "ðŸŽ™ï¸"
            statusText.text = "Ø¬Ø§Ù‡Ø² Ù„Ù„Ø§Ø³ØªÙ…Ø§Ø¹"
            stopPulseAnimation(button)
            log("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£Ø¨Ù„Ø´ Ø§Ù„Ø§Ø³ØªÙ…Ø§Ø¹")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Ø¨ÙŠØµÙŠØ± Ø¹Ø§Ø¯ÙŠ ÙˆÙ‚Øª Ø§Ù„ØµÙ…Øª Ø£Ùˆ Ø§Ù„Ø¶Ø¬ÙŠØ¬ØŒ Ù…Ù†Ø¹ÙŠØ¯ Ø§Ù„Ø§Ø³ØªÙ…Ø§Ø¹ Ø¥Ø°Ø§ Ù„Ø³Ø§ Ø¨ÙˆØ¶Ø¹ Ù…Ø³ØªÙ…Ø±
            if (continuousMode) startListening()
        }

        override fun onResults(resultsBundle: Bundle?) {
            val matches = resultsBundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull()?.trim() ?: ""
            handleSpeechResult(spoken)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpeechResult(spoken: String) {
        if (lectureMode) {
            if (spoken.contains("ÙˆÙ‚Ù Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©") || spoken.contains("Ø®Ù„ØµØª Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©") ||
                spoken.contains("Ø§Ù†Ù‡ÙŠ Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©")
            ) {
                stopLectureModeAndSummarize()
            } else if (spoken.isNotBlank()) {
                lectureBuffer.append(spoken).append(". ")
                log("ðŸ“ $spoken")
            }
            if (continuousMode) startListening()
            return
        }

        if (continuousMode) {
            val lower = spoken.lowercase(Locale.getDefault())
            val wakeIndex = when {
                spoken.contains("Ø¬Ø§Ø±ÙØ³") -> spoken.indexOf("Ø¬Ø§Ø±ÙØ³").let { it + "Ø¬Ø§Ø±ÙØ³".length }
                lower.contains("jarvis") -> lower.indexOf("jarvis") + "jarvis".length
                spoken.contains("Ð´Ð¶Ð°Ñ€Ð²Ð¸Ñ") -> spoken.indexOf("Ð´Ð¶Ð°Ñ€Ð²Ð¸Ñ") + "Ð´Ð¶Ð°Ñ€Ð²Ð¸Ñ".length
                spoken.contains("è´¾ç»´æ–¯") -> spoken.indexOf("è´¾ç»´æ–¯") + "è´¾ç»´æ–¯".length
                else -> -1
            }
            if (wakeIndex != -1) {
                val commandOnly = spoken.substring(wakeIndex.coerceAtMost(spoken.length)).trim()
                log("Ø£Ù†Øª: $commandOnly")
                if (commandOnly.isNotBlank()) handleCommand(commandOnly)
            }
            if (continuousMode) startListening()
        } else if (spoken.isNotBlank()) {
            log("Ø£Ù†Øª: $spoken")
            handleCommand(spoken)
        }
    }

    // ---------------- Command routing ----------------

    private fun handleCommand(text: String) {
        try {
            handleCommandInternal(text)
        } catch (e: Exception) {
            respond("ØµØ§Ø± Ø®Ø·Ø£ Ø¨Ø³ÙŠØ·ØŒ Ø¨Ø³ Ø£Ù†Ø§ Ù„Ø³Ø§ Ø´ØºØ§Ù„ØŒ Ø¬Ø±Ø¨ Ø£Ù…Ø± ØªØ§Ù†ÙŠ")
        }
    }

    private fun handleCommandInternal(text: String) {
        val cmd = text.lowercase(Locale("ar")).trim()

        when {
            cmd.contains("Ø§Ø¨Ø¯Ø§ Ù…Ø­Ø§Ø¶Ø±Ø©") || cmd.contains("Ø§Ø¨Ø¯Ø£ Ù…Ø­Ø§Ø¶Ø±Ø©") ||
                    cmd.contains("Ø³Ø¬Ù„ Ù…Ø­Ø§Ø¶Ø±Ø©") -> {
                startLectureMode()
            }
            cmd.contains("Ø§Ø³Ù…ÙŠ ") -> {
                val name = extractNameAfter(cmd, "Ø§Ø³Ù…ÙŠ")
                if (name.isNotBlank()) {
                    saveUserName(name)
                    respond("ØªØ´Ø±ÙØª ÙÙŠÙƒ ÙŠØ§ $nameØŒ Ù…Ù† Ù‡Ù„Ù‚ Ø±Ø­ Ø£Ø¹Ø±ÙÙƒ")
                } else {
                    respond("Ù‚Ù„ÙŠ Ø§Ø³Ù…ÙƒØŸ")
                }
            }
            cmd.contains("Ø´Ùˆ Ø§Ø³Ù…ÙŠ") || cmd.contains("ÙˆØ´ Ø§Ø³Ù…ÙŠ") -> {
                if (userName.isNotBlank()) {
                    respond("Ø§Ø³Ù…Ùƒ $userName")
                } else {
                    respond("Ù…Ø§ ØªÙ‚Ù„ÙŠ Ø§Ø³Ù…Ùƒ Ù„Ø³Ø§ØŒ Ù‚Ù„ÙŠ Ø§Ø³Ù…ÙŠ ÙÙ„Ø§Ù†")
                }
            }
            cmd.contains("Ø§Ù†Ø³Ù‰ Ø§Ø³Ù…ÙŠ") -> {
                userName = ""
                getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit()
                    .remove("user_name").apply()
                respond("ØªÙ…Ø§Ù…ØŒ Ù†Ø³ÙŠØª Ø§Ø³Ù…Ùƒ")
            }
            cmd.contains("Ø´ØºÙ„ Ø§Ù„ÙÙ„Ø§Ø´") || cmd.contains("Ø§ÙØªØ­ Ø§Ù„ÙÙ„Ø§Ø´") ||
                    cmd.contains("Ø´Ø¹Ù„ Ø§Ù„ÙÙ„Ø§Ø´") || cmd.contains("Ø´Ø¹Ù„ ÙÙ„Ø§Ø´") ||
                    cmd.contains("Ø´ØºÙ„ ÙÙ„Ø§Ø´") ||
                    cmd.contains("turn on the flash") || cmd.contains("turn on flash") ||
                    cmd.contains("allume la lampe") || cmd.contains("allume le flash") -> {
                setFlashlight(true)
                respond(flashOnPhrases.random())
            }
            cmd.contains("Ø·ÙÙŠ Ø§Ù„ÙÙ„Ø§Ø´") || cmd.contains("Ø§Ø·ÙÙŠ Ø§Ù„ÙÙ„Ø§Ø´") ||
                    cmd.contains("Ø·ÙØ¦ Ø§Ù„ÙÙ„Ø§Ø´") ||
                    cmd.contains("turn off the flash") || cmd.contains("turn off flash") ||
                    cmd.contains("Ã©teins la lampe") || cmd.contains("Ã©teins le flash") -> {
                setFlashlight(false)
                respond(flashOffPhrases.random())
            }
            cmd.contains("ØºÙŠØ± Ø§Ù„Ù„ØºØ©") || cmd.contains("change language") || cmd.contains("changer la langue") -> {
                handleLanguageSwitch(cmd)
            }
            cmd.contains("Ø´ØºÙ„ Ù…ÙˆØ³ÙŠÙ‚Ù‰") || cmd.contains("Ø´ØºÙ„ Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰") ||
                    cmd.contains("play music") || cmd.contains("joue de la musique") ||
                    cmd.contains("lance la musique") -> {
                playMusic()
                respond(musicOnPhrases.random())
            }
            cmd.contains("ÙˆÙ‚Ù Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰") || cmd.contains("Ø·ÙÙŠ Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰") ||
                    cmd.contains("stop music") || cmd.contains("arrÃªte la musique") -> {
                stopMusic()
                respond(musicOffPhrases.random())
            }
            cmd.contains("Ø´ØºÙ„ Ø§ØºÙ†ÙŠØ©") || cmd.contains("Ø´ØºÙ„ Ø£ØºÙ†ÙŠØ©") -> {
                val name = extractNameAfter(cmd, "Ø§ØºÙ†ÙŠØ©").ifBlank { extractNameAfter(cmd, "Ø£ØºÙ†ÙŠØ©") }
                playSongByName(name)
            }
            cmd.contains("Ø§Ù„Ø§ØºÙ†ÙŠØ© Ø§Ù„Ù„ÙŠ Ø¨Ø¹Ø¯Ù‡Ø§") || cmd.contains("Ø§Ù„Ø£ØºÙ†ÙŠØ© Ø§Ù„Ù„ÙŠ Ø¨Ø¹Ø¯Ù‡Ø§") ||
                    cmd.contains("Ø§Ù„Ø§ØºÙ†ÙŠØ© Ø§Ù„Ø¬Ø§ÙŠØ©") || cmd.contains("Ø§ØºÙ†ÙŠØ© Ø¨Ø¹Ø¯Ù‡Ø§") ||
                    cmd.contains("Ø§Ù„ØªØ§Ù„ÙŠ") -> {
                playNextInPlaylist()
            }
            cmd.contains("Ø¶ÙŠÙ Ø§ØºÙ†ÙŠØ©") || cmd.contains("Ø¶ÙŠÙ Ø£ØºÙ†ÙŠØ©") -> {
                val name = extractNameAfter(cmd, "Ø§ØºÙ†ÙŠØ©").ifBlank { extractNameAfter(cmd, "Ø£ØºÙ†ÙŠØ©") }
                addSongToPlaylist(name)
            }
            cmd.contains("Ø´Ùˆ Ù‚Ø§Ø¦Ù…ØªÙŠ") || cmd.contains("Ø§Ø¹Ø±Ø¶ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©") -> {
                respond(showPlaylist())
            }
            cmd.contains("Ø§Ù…Ø³Ø­ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©") -> {
                clearPlaylist()
            }
            cmd.contains("Ø§Ø­Ø³Ø¨") || containsMath(cmd) -> {
                val result = calculate(cmd)
                respond(result)
            }
            cmd.contains("Ø°ÙƒØ±Ù†ÙŠ") || cmd.contains("ØªØ°ÙƒÙŠØ±") -> {
                // Expects something like: "Ø°ÙƒØ±Ù†ÙŠ Ø¨Ø¹Ø¯ 10 Ø¯Ù‚Ø§ÙŠÙ‚ Ø§Ø´Ø±Ø¨ Ù…ÙŠ"
                val minutes = extractMinutes(cmd) ?: 5
                scheduleReminder(minutes, cmd)
                respond("Ù‚Ø¨ÙˆÙ„ØŒ Ø±Ø­ Ù†ÙÙƒØ±Ùƒ Ø¨Ø¹Ø¯ $minutes Ø¯Ù‚ÙŠÙ‚Ø©")
            }
            cmd.contains("Ø§ÙØªØ­ Ø§Ù†Ø³ØªÙ‚Ø±Ø§Ù…") || cmd.contains("Ø§ÙØªØ­ Ø§Ù†Ø³ØªØºØ±Ø§Ù…") ||
                    cmd.contains("open instagram") || cmd.contains("ouvre instagram") -> {
                openApp("com.instagram.android", "Ø§Ù†Ø³ØªÙ‚Ø±Ø§Ù…")
            }
            cmd.contains("Ø§ÙØªØ­ ÙŠÙˆØªÙŠÙˆØ¨") || cmd.contains("Ø§ÙØªØ­ ÙŠÙˆØªÙˆØ¨") ||
                    cmd.contains("open youtube") || cmd.contains("ouvre youtube") -> {
                openApp("com.google.android.youtube", "ÙŠÙˆØªÙŠÙˆØ¨")
            }
            cmd.contains("Ø§ÙØªØ­ ÙÙŠØ³Ø¨ÙˆÙƒ") ||
                    cmd.contains("open facebook") || cmd.contains("ouvre facebook") -> {
                openApp("com.facebook.katana", "ÙÙŠØ³Ø¨ÙˆÙƒ")
            }
            cmd.contains("Ø§ØªØµÙ„ Ø¨") -> {
                val name = extractNameAfter(cmd, "Ø§ØªØµÙ„ Ø¨")
                callContact(name)
            }
            cmd.contains("call ") -> {
                val name = extractNameAfter(cmd, "call ")
                callContact(name)
            }
            cmd.contains("appelle ") -> {
                val name = extractNameAfter(cmd, "appelle ")
                callContact(name)
            }
            cmd.contains("Ø±Ø³Ù…Ø© Ø§Ù„ÙŠÙˆÙ…") || cmd.contains("Ø§Ù‚ØªØ±Ø­ Ù„ÙŠ Ø±Ø³Ù…Ø©") ||
                    cmd.contains("drawing idea") || cmd.contains("idÃ©e de dessin") -> {
                respond(suggestDrawing())
            }
            cmd.contains("ÙØ·ÙˆØ±") || cmd.contains("breakfast idea") ||
                    cmd.contains("idÃ©e de petit") -> {
                respond(suggestBreakfast())
            }
            cmd.contains("Ø§Ø´Ø±Ø­Ù„ÙŠ") || cmd.contains("Ø§Ø´Ø±Ø­ Ù„ÙŠ") || cmd.contains("ÙÙ‡Ù…Ù†ÙŠ") ||
                    cmd.contains("Ø§ÙØªØ­ Ù…ÙˆØ¶ÙˆØ¹") || cmd.contains("Ø´Ùˆ Ù‡Ùˆ") || cmd.contains("Ø´Ùˆ Ù‡ÙŠ") -> {
                val topic = extractExplainTopic(cmd)
                explainTopic(topic)
            }
            cmd.contains("Ø§ÙƒØªØ¨Ù„ÙŠ ÙƒÙˆØ¯") || cmd.contains("Ø¨Ø±Ù…Ø¬Ù„ÙŠ") || cmd.contains("write code") -> {
                val marker = when {
                    cmd.contains("Ø§ÙƒØªØ¨Ù„ÙŠ ÙƒÙˆØ¯") -> "ÙƒÙˆØ¯"
                    cmd.contains("Ø¨Ø±Ù…Ø¬Ù„ÙŠ") -> "Ø¨Ø±Ù…Ø¬Ù„ÙŠ"
                    else -> "code"
                }
                val topic = extractNameAfter(cmd, marker)
                writeCode(topic)
            }
            cmd.contains("ØµÙ…Ù…Ù„ÙŠ") || cmd.contains("ØªØµÙ…ÙŠÙ… Ù‡ÙˆÙ„ÙˆØ¬Ø±Ø§Ù…ÙŠ") ||
                    cmd.contains("design hologram") -> {
                val marker = if (cmd.contains("ØµÙ…Ù…Ù„ÙŠ")) "ØµÙ…Ù…Ù„ÙŠ" else "ØªØµÙ…ÙŠÙ… Ù‡ÙˆÙ„ÙˆØ¬Ø±Ø§Ù…ÙŠ"
                val description = extractNameAfter(cmd, marker)
                designHologram(description)
            }
            cmd.contains("Ø­ÙˆÙ„") && (cmd.contains("ÙƒÙŠÙ„ÙˆÙ…ØªØ±") || cmd.contains("Ù…ÙŠÙ„") ||
                    cmd.contains("ÙƒÙŠÙ„Ùˆ") || cmd.contains("Ø¨Ø§ÙˆÙ†Ø¯") ||
                    cmd.contains("Ù…Ø¦ÙˆÙŠØ©") || cmd.contains("ÙÙ‡Ø±Ù†Ù‡Ø§ÙŠØª")) -> {
                respond(convertUnits(cmd))
            }
            cmd.contains("Ù…Ø¹Ù„ÙˆÙ…Ø© Ø¹Ø´ÙˆØ§Ø¦ÙŠØ©") || cmd.contains("Ù…Ø¹Ù„ÙˆÙ…Ø© Ø§Ù„ÙŠÙˆÙ…") ||
                    cmd.contains("random fact") -> {
                respond(funFacts.random())
            }
            cmd.contains("Ø§Ù„Ø¨Ø·Ø§Ø±ÙŠØ©") || cmd.contains("battery") -> {
                respond("Ø§Ù„Ø¨Ø·Ø§Ø±ÙŠØ© Ø¹Ù†Ø¯ ${getBatteryLevel()}%")
            }
            cmd.contains("Ø§Ù„ØªØ§Ø±ÙŠØ®") || cmd.contains("date") -> {
                val today = java.text.SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date())
                respond("Ø§Ù„ØªØ§Ø±ÙŠØ® Ø§Ù„ÙŠÙˆÙ… $today")
            }
            cmd.contains("Ø§Ø±ÙØ¹ Ø§Ù„ØµÙˆØª") || cmd.contains("Ø²ÙˆØ¯ Ø§Ù„ØµÙˆØª") -> {
                adjustVolume(true)
                respond("Ø±ÙØ¹Øª Ø§Ù„ØµÙˆØª")
            }
            cmd.contains("Ù†Ø²Ù„ Ø§Ù„ØµÙˆØª") || cmd.contains("Ø®ÙØ¶ Ø§Ù„ØµÙˆØª") -> {
                adjustVolume(false)
                respond("Ù†Ø²Ù„Øª Ø§Ù„ØµÙˆØª")
            }
            cmd.contains("ÙˆØ¶Ø¹ Ø§Ù„ØµØ§Ù…Øª") -> {
                setRingerMode(AudioManager.RINGER_MODE_SILENT)
            }
            cmd.contains("ÙˆØ¶Ø¹ Ø§Ù„Ø§Ù‡ØªØ²Ø§Ø²") -> {
                setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
            }
            cmd.contains("Ø§Ù„ÙˆØ¶Ø¹ Ø§Ù„Ø¹Ø§Ø¯ÙŠ") || cmd.contains("Ø±Ø¬Ø¹ Ø§Ù„ØµÙˆØª Ø§Ù„Ø¹Ø§Ø¯ÙŠ") -> {
                setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            }
            cmd.contains("Ù…Ù†Ø¨Ù‡ Ø§Ù„Ø³Ø§Ø¹Ø©") || cmd.contains("Ø­Ø· Ù…Ù†Ø¨Ù‡") -> {
                handleSetAlarm(cmd)
            }
            cmd.contains("Ø§Ø¨Ø­Ø« Ø¹Ù†") || cmd.contains("Ø¯ÙˆØ± Ù„ÙŠ Ø¹Ù„Ù‰") -> {
                val query = extractSearchQuery(cmd)
                searchGoogle(query)
            }
            cmd.contains("Ø·Ø±ÙŠÙ‚ Ù…Ø´ÙŠ") || cmd.contains("Ø§Ù…Ø´ÙŠ Ø§Ù„Ù‰") ||
                    cmd.contains("Ø§Ù…Ø´ÙŠ Ù„") || cmd.contains("Ù…Ø´ÙŠ Ø§Ù„Ù‰") -> {
                val place = extractNameAfter(cmd, "Ø§Ù„Ù‰")
                navigateTo(place, "walking")
            }
            cmd.contains("ÙˆØ¯Ù‘ÙŠÙ†ÙŠ Ø§Ù„Ù‰") || cmd.contains("ÙˆØ¯ÙŠÙ†ÙŠ Ø§Ù„Ù‰") ||
                    cmd.contains("Ø®Ø°Ù†ÙŠ Ø§Ù„Ù‰") || cmd.contains("Ø§Ù„Ø·Ø±ÙŠÙ‚ Ø§Ù„Ù‰") -> {
                val place = extractNameAfter(cmd, "Ø§Ù„Ù‰")
                navigateTo(place)
            }
            cmd.contains("Ù†ÙƒØªØ©") || cmd.contains("joke") -> {
                respond(jokes.random())
            }
            cmd.contains("Ø¯ÙˆÙ† Ù…Ù„Ø§Ø­Ø¸Ø©") || cmd.contains("Ø³Ø¬Ù„ Ù…Ù„Ø§Ø­Ø¸Ø©") -> {
                val note = extractNameAfter(cmd, "Ù…Ù„Ø§Ø­Ø¸Ø©")
                if (note.isNotBlank()) {
                    saveNote(note)
                    respond("Ø³Ø¬Ù„Øª Ø§Ù„Ù…Ù„Ø§Ø­Ø¸Ø©")
                } else {
                    respond("Ù‚Ù„ÙŠ Ø´Ùˆ Ø§Ù„Ù…Ù„Ø§Ø­Ø¸Ø© ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ ØªØ³Ø¬Ù„Ù‡Ø§")
                }
            }
            cmd.contains("Ø§Ù‚Ø±Ø§ Ø§Ù„Ù…Ù„Ø§Ø­Ø¸Ø§Øª") || cmd.contains("Ø´Ùˆ Ù…Ù„Ø§Ø­Ø¸Ø§ØªÙŠ") -> {
                respond(readNotes())
            }
            cmd.contains("Ø§ÙØªØ­ ÙˆØ§ØªØ³Ø§Ø¨") || cmd.contains("open whatsapp") -> {
                openApp("com.whatsapp", "ÙˆØ§ØªØ³Ø§Ø¨")
            }
            cmd.contains("Ø§ÙØªØ­ ØªÙŠÙƒ ØªÙˆÙƒ") || cmd.contains("open tiktok") -> {
                openApp("com.zhiliaoapp.musically", "ØªÙŠÙƒ ØªÙˆÙƒ")
            }
            cmd.contains("Ø§ÙØªØ­ ØªÙˆÙŠØªØ±") || cmd.contains("Ø§ÙØªØ­ Ø¥ÙƒØ³") || cmd.contains("open twitter") -> {
                openApp("com.twitter.android", "ØªÙˆÙŠØªØ±")
            }
            cmd.contains("Ø§ÙØªØ­ Ø®Ø±Ø§Ø¦Ø·") || cmd.contains("open maps") -> {
                openApp("com.google.android.apps.maps", "Ø§Ù„Ø®Ø±Ø§Ø¦Ø·")
            }
            cmd.contains("Ø§ÙØªØ­ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§") || cmd.contains("open camera") -> {
                try {
                    startActivity(Intent("android.media.action.IMAGE_CAPTURE"))
                    respond("Ø¬Ø§Ø±ÙŠ ÙØªØ­ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§")
                } catch (e: Exception) {
                    respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙØªØ­ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§")
                }
            }
            cmd.contains("Ø§ÙØªØ­ Ø§Ù„Ø§Ø¹Ø¯Ø§Ø¯Ø§Øª") || cmd.contains("open settings") -> {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    respond("Ø¬Ø§Ø±ÙŠ ÙØªØ­ Ø§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª")
                } catch (e: Exception) {
                    respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙØªØ­ Ø§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª")
                }
            }
            (cmd.contains("Ù…Ø³Ø§ÙØ©") || cmd.contains("Ù…Ø³Ø§ÙÙ‡")) &&
                    (cmd.contains("Ø§Ù„Ù‰") || cmd.contains("Ø¥Ù„Ù‰")) -> {
                handleDistanceQuery(cmd)
            }
            else -> {
                respond(chatReply(cmd))
            }
        }
    }

    // ---------------- Flashlight ----------------

    private fun setFlashlight(on: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, on)
            flashOn = on
        } catch (e: Exception) {
            log("ØªØ¹Ø°Ø± Ø§Ù„ØªØ­ÙƒÙ… Ø¨Ø§Ù„ÙÙ„Ø§Ø´: ${e.message}")
        }
    }

    // ---------------- Music ----------------
    // Place an mp3 file named "sample_music.mp3" inside app/src/main/res/raw/

    private fun playMusic() {
        stopMusic()
        try {
            val resId = resources.getIdentifier("sample_music", "raw", packageName)
            if (resId == 0) {
                log("Ù…Ø§ Ù„Ù‚ÙŠØª Ù…Ù„Ù Ù…ÙˆØ³ÙŠÙ‚Ù‰. Ø¶ÙŠÙ mp3 Ø¨Ø§Ø³Ù… sample_music.mp3 Ø¯Ø§Ø®Ù„ res/raw")
                return
            }
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.start()
        } catch (e: Exception) {
            log("Ù…Ø§ Ù„Ù‚ÙŠØª Ù…Ù„Ù Ù…ÙˆØ³ÙŠÙ‚Ù‰. Ø¶ÙŠÙ mp3 Ø¨Ø§Ø³Ù… sample_music.mp3 Ø¯Ø§Ø®Ù„ res/raw")
        }
    }

    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ---------------- Calculator ----------------

    private fun containsMath(cmd: String): Boolean {
        return cmd.any { it.isDigit() } && (cmd.contains("+") || cmd.contains("-") ||
                cmd.contains("*") || cmd.contains("/") || cmd.contains("Ø²Ø§Ø¦Ø¯") ||
                cmd.contains("Ù†Ø§Ù‚Øµ") || cmd.contains("Ø¶Ø±Ø¨") || cmd.contains("Ù‚Ø³Ù…Ø©") ||
                cmd.contains("Ø¬Ø°Ø±") || cmd.contains("Ù†Ø³Ø¨Ø©") || cmd.contains("%"))
    }

    private fun calculate(cmd: String): String {
        return try {
            if (cmd.contains("Ù†Ø³Ø¨Ø©") || cmd.contains("%")) {
                val percentRegex = Regex("""(\d+(?:\.\d+)?)\s*%?[^\d]*Ù…Ù†\s*(\d+(?:\.\d+)?)""")
                val match = percentRegex.find(cmd)
                if (match != null) {
                    val percent = match.groupValues[1].toDouble()
                    val total = match.groupValues[2].toDouble()
                    val result = (percent / 100.0) * total
                    return "Ø§Ù„Ù†ØªÙŠØ¬Ø© ØªØ·Ù„Ø¹ $result"
                }
            }

            if (cmd.contains("Ø¬Ø°Ø±")) {
                val rootRegex = Regex("""(\d+(?:\.\d+)?)""")
                val match = rootRegex.find(cmd)
                if (match != null) {
                    val number = match.groupValues[1].toDouble()
                    val result = sqrt(number)
                    return "Ø§Ù„Ø¬Ø°Ø± Ø§Ù„ØªØ±Ø¨ÙŠØ¹ÙŠ ÙŠØ·Ù„Ø¹ $result"
                }
            }

            var expr = cmd
                .replace("Ø§Ø­Ø³Ø¨", "")
                .replace("Ø²Ø§Ø¦Ø¯", "+")
                .replace("Ù†Ø§Ù‚Øµ", "-")
                .replace("Ø¶Ø±Ø¨", "*")
                .replace("Ù‚Ø³Ù…Ø©", "/")
                .trim()
            val result = ExpressionBuilder(expr).build().evaluate()
            "Ø§Ù„Ù†ØªÙŠØ¬Ø© ØªØ·Ù„Ø¹ $result"
        } catch (e: Exception) {
            "Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙÙ‡Ù… Ø§Ù„Ø¹Ù…Ù„ÙŠØ© Ø§Ù„Ø­Ø³Ø§Ø¨ÙŠØ©"
        }
    }

    // ---------------- Reminders ----------------

    private fun extractMinutes(cmd: String): Int? {
        val regex = Regex("""(\d+)\s*(Ø¯Ù‚ÙŠÙ‚Ø©|Ø¯Ù‚Ø§ÙŠÙ‚|Ø¯Ù‚Ø§Ø¦Ù‚)""")
        val match = regex.find(cmd) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun scheduleReminder(minutes: Int, message: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        intent.putExtra("message", message)
        val pendingIntent = PendingIntent.getBroadcast(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            log("Ù„Ø§Ø²Ù… ØªØ³Ù…Ø­ Ø¨ØµÙ„Ø§Ø­ÙŠØ© 'Schedule Exact Alarm' Ù…Ù† Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ù†Ø¸Ø§Ù…")
        }
    }

    // ---------------- Simple chat (offline rules + optional online fallback) ----------------

    private fun chatReply(cmd: String): String {
        val offlineReply = offlineRules(cmd)
        if (offlineReply != null) return offlineReply

        if (GEMINI_API_KEY.isNotBlank()) {
            askGemini(cmd)
            return "Ø¨ÙÙƒØ±..."
        }
        return "Ù…Ø§ ÙÙ‡Ù…Øª Ø¹Ù„ÙŠÙƒ ØªÙ…Ø§Ù…Ù‹Ø§ØŒ Ø¬Ø±Ø¨ ØµÙŠØºØ© ØªØ§Ù†ÙŠØ©"
    }

    private fun offlineRules(cmd: String): String? {
        val nameSuffix = if (userName.isNotBlank()) " ÙŠØ§ $userName" else ""
        return when {
            cmd.contains("Ù…Ø±Ø­Ø¨Ø§") || cmd.contains("Ù‡Ù„Ø§") || cmd.contains("Ø§Ù„Ø³Ù„Ø§Ù…") ->
                listOf("Ø£Ù‡Ù„Ø§ Ø¨ÙŠÙƒ$nameSuffixØŒ ÙˆÙŠÙ† Ø±Ø§ÙƒØŸ", "Ù‡Ù„Ø§$nameSuffixØŒ Ø´Ù†Ùˆ Ù†Ø¯ÙŠØ±Ù„ÙƒØŸ", "Ø£Ù‡Ù„ÙŠÙ†$nameSuffixØŒ Ù‚ÙˆÙ„Ù‘ÙŠ ÙƒÙŠ Ù†Ø¹Ø§ÙˆÙ†Ùƒ").random()
            cmd.contains("ÙƒÙŠÙÙƒ") || cmd.contains("Ø´Ø®Ø¨Ø§Ø±Ùƒ") ->
                listOf("Ù„Ø§Ø¨Ø§Ø³ Ø§Ù„Ø­Ù…Ø¯Ù„Ù„Ù‡ØŒ ÙˆØ§Ù†Øª ÙƒÙŠÙÙƒ$nameSuffixØŸ", "Ù…Ù„ÙŠØ­ Ø¨Ø²Ø§ÙØŒ ÙˆØ§Ù†ØªØŸ").random()
            cmd.contains("Ø§Ù„Ø³Ø§Ø¹Ø©") ->
                "Ø§Ù„Ø³Ø§Ø¹Ø© Ù‡Ù„Ù‚ ${java.text.SimpleDateFormat("HH:mm").format(Date())}"
            cmd.contains("Ù…ÙŠÙ† Ø§Ù†Øª") || cmd.contains("Ø´Ùˆ Ø§Ø³Ù…Ùƒ") ->
                "Ø£Ù†Ø§ Ø¬Ø§Ø±ÙØ³ØŒ ØµØ§Ø­Ø¨Ùƒ Ø§Ù„Ø´Ø®ØµÙŠØŒ Ø¬Ø§Ù‡Ø² Ù†Ø¹Ø§ÙˆÙ†Ùƒ Ø¨Ø£ÙŠ Ø­Ø§Ø¬Ø©"
            cmd.contains("Ø´ÙƒØ±Ø§") || cmd.contains("ÙŠØ¹Ø·ÙŠÙƒ Ø§Ù„ØµØ­Ø©") ->
                listOf("Ø§Ù„Ø¹ÙÙˆØŒ Ù‡Ø°Ø§ ÙˆØ§Ø¬Ø¨ÙŠ", "ÙˆÙ„Ø§ ÙŠÙ‡Ù…ÙƒØŒ Ø£Ù†Ø§ Ù‡Ù†Ø§ ÙˆÙ‚ØªØ§Ø´ ØªØ­ØªØ§Ø¬Ù†ÙŠ").random()
            else -> null
        }
    }

    private fun askGemini(message: String) {
        val nameContext = if (userName.isNotBlank()) "Ø§Ø³Ù…ÙŠ $userNameØŒ Ø®Ø§Ø·Ø¨Ù†ÙŠ Ø¨Ø§Ø³Ù…ÙŠ Ø£Ø­ÙŠØ§Ù†Ù‹Ø§. " else ""
        val identityContext = "Ø£Ù†Øª Ø¬Ø§Ø±ÙØ³ØŒ Ù…Ø³Ø§Ø¹Ø¯ Ø´Ø®ØµÙŠ Ø¨Ø´Ø®ØµÙŠØ© ÙˆØ§Ø­Ø¯Ø© Ù…ÙˆØ­Ø¯Ø© Ø¨ÙƒÙ„ Ø§Ù„Ù„ØºØ§ØªØŒ Ø¬Ø§ÙˆØ¨ Ø¨Ù†ÙØ³ Ø§Ù„Ù„ØºØ© ÙŠÙ„ÙŠ Ø¥Ø¬Ø§Ùƒ ÙÙŠÙ‡Ø§ Ø§Ù„Ø³Ø¤Ø§Ù„. "
        val promptWithStyle = "$identityContext$nameContext" +
                "Ø¬Ø§ÙˆØ¨Ù†ÙŠ Ø¨Ø£Ø³Ù„ÙˆØ¨ Ø·Ø¨ÙŠØ¹ÙŠ ÙˆØ¯Ø§ÙØ¦ ÙˆÙ‚Ø±ÙŠØ¨ Ù…Ù† Ù„Ù‡Ø¬Ø© Ø§Ù„Ø­ÙƒÙŠ Ø§Ù„Ø¹Ø§Ø¯ÙŠØŒ Ø±Ø¯ÙˆØ¯ Ù‚ØµÙŠØ±Ø© ÙˆÙ…ÙÙ‡ÙˆÙ…Ø©ØŒ Ù…Ù† ØºÙŠØ± Ø±Ø³Ù…ÙŠØ§Øª Ø²Ø§ÙŠØ¯Ø©: $message"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply { put("text", promptWithStyle) }
                    ))
                }
            ))
        }

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            jsonBody.toString()
        )
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙˆØµÙ„ Ù„Ù„Ù†Øª") }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    if (json.has("error")) {
                        val errMsg = json.getJSONObject("error").optString("message", "Ø®Ø·Ø£ ØºÙŠØ± Ù…Ø¹Ø±ÙˆÙ")
                        runOnUiThread { respond("ØµØ§Ø± Ø®Ø·Ø£ Ù…Ù† Gemini: $errMsg") }
                        return
                    }
                    val reply = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    runOnUiThread { respond(reply.trim()) }
                } catch (e: Exception) {
                    runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙÙ‡Ù… Ø±Ø¯ Gemini") }
                }
            }
        })
    }

    // ---------------- Play songs by name & playlist ----------------

    private fun playSongByName(name: String) {
        if (name.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ø§Ø³Ù… Ø§Ù„Ø£ØºÙ†ÙŠØ©")
            return
        }
        try {
            val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            intent.putExtra(SearchManager.QUERY, name)
            intent.putExtra("android.intent.extra.focus", "vnd.android.cursor.item/audio")
            startActivity(intent)
            addToPlaylistAndSetCurrent(name)
            respond("Ù‡Ø§ÙƒÙ‡Ø§ $name")
        } catch (e: Exception) {
            respond("Ù…Ø§ Ù„Ù‚ÙŠØª ØªØ·Ø¨ÙŠÙ‚ Ù…ÙˆØ³ÙŠÙ‚Ù‰ ÙŠÙÙ‡Ù… Ù‡Ø§Ù„Ø£Ù…Ø± Ø¹Ù†Ø¯Ùƒ")
        }
    }

    private fun playNextInPlaylist() {
        val list = getPlaylist()
        if (list.isEmpty()) {
            respond("Ù…Ø§ Ø¹Ù†Ø¯Ùƒ Ø£ØºØ§Ù†ÙŠ Ø¨Ø§Ù„Ù‚Ø§Ø¦Ù…Ø© Ù„Ø³Ø§")
            return
        }
        var index = getCurrentIndex() + 1
        if (index >= list.size) {
            index = list.size - 1
        }
        setCurrentIndex(index)
        val song = list[index]
        try {
            val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            intent.putExtra(SearchManager.QUERY, song)
            intent.putExtra("android.intent.extra.focus", "vnd.android.cursor.item/audio")
            startActivity(intent)
            respond("Ù‡Ø§ÙƒÙ‡Ø§ $song")
        } catch (e: Exception) {
            respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£Ø´ØºÙ„ Ø§Ù„Ø£ØºÙ†ÙŠØ©")
        }
    }

    private fun addSongToPlaylist(name: String) {
        if (name.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ø§Ø³Ù… Ø§Ù„Ø£ØºÙ†ÙŠØ© ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ ØªØ¶ÙŠÙÙ‡Ø§")
            return
        }
        val list = getPlaylist()
        if (!list.contains(name)) {
            list.add(name)
            savePlaylist(list)
        }
        respond("Ø¶ÙØª $name Ù„Ù„Ù‚Ø§Ø¦Ù…Ø©")
    }

    private fun showPlaylist(): String {
        val list = getPlaylist()
        if (list.isEmpty()) return "Ø§Ù„Ù‚Ø§Ø¦Ù…Ø© ÙØ§Ø¶ÙŠØ© Ù„Ø³Ø§"
        return "Ù‚Ø§Ø¦Ù…ØªÙƒ: " + list.joinToString("ØŒ ")
    }

    private fun clearPlaylist() {
        savePlaylist(emptyList())
        setCurrentIndex(-1)
        respond("Ù…Ø³Ø­Øª Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©")
    }

    private fun addToPlaylistAndSetCurrent(name: String) {
        val list = getPlaylist()
        var index = list.indexOf(name)
        if (index == -1) {
            list.add(name)
            index = list.size - 1
            savePlaylist(list)
        }
        setCurrentIndex(index)
    }

    private fun getPlaylist(): MutableList<String> {
        val raw = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("playlist", "") ?: ""
        return if (raw.isBlank()) mutableListOf() else raw.split("||").toMutableList()
    }

    private fun savePlaylist(list: List<String>) {
        getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit()
            .putString("playlist", list.joinToString("||")).apply()
    }

    private fun getCurrentIndex(): Int {
        return getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getInt("playlist_index", -1)
    }

    private fun setCurrentIndex(index: Int) {
        getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit()
            .putInt("playlist_index", index).apply()
    }

    // ---------------- Lecture mode ----------------

    private fun startLectureMode() {
        lectureMode = true
        lectureBuffer = StringBuilder()
        statusText.text = "ðŸ“ ÙˆØ¶Ø¹ Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©... Ù‚ÙˆÙ„ \"ÙˆÙ‚Ù Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©\" Ù„Ù…Ø§ ØªØ®Ù„Øµ"
        respond("ØªÙ…Ø§Ù…ØŒ Ø¨Ù„Ø´Øª Ø£Ø³Ù…Ø¹ Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©ØŒ Ù‚Ù„ÙŠ ÙˆÙ‚Ù Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø© Ù„Ù…Ø§ ØªØ®Ù„Øµ")
    }

    private fun stopLectureModeAndSummarize() {
        lectureMode = false
        statusText.text = if (continuousMode) "ðŸ”´ Ø¨Ø³Ù…Ø¹Ùƒ... Ù‚ÙˆÙ„ \"Ø¬Ø§Ø±ÙØ³\"" else "âšª Ù…ØªÙˆÙ‚ÙØŒ Ø¯ÙˆØ³ Ù„ØªØ´ØºÙ‘Ù„ Ø§Ù„Ø§Ø³ØªÙ…Ø§Ø¹"
        val fullText = lectureBuffer.toString().trim()
        if (fullText.isBlank()) {
            respond("Ù…Ø§ Ø³Ø¬Ù„Øª Ø´ÙŠØŒ Ø¬Ø±Ø¨ ØªØ§Ù†ÙŠ")
            return
        }
        respond("Ø®Ù„ØµØªØŒ Ø¹Ù… Ù„Ø®ØµÙ„Ùƒ Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø©...")
        if (GEMINI_API_KEY.isNotBlank()) {
            summarizeLecture(fullText)
        } else {
            saveNote("Ù…Ø­Ø§Ø¶Ø±Ø©: $fullText")
            respond("Ø³Ø¬Ù„Øª Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø© ÙƒØ§Ù…Ù„Ø© ÙƒÙ…Ù„Ø§Ø­Ø¸Ø©ØŒ Ø¨Ø³ Ù…Ø­ØªØ§Ø¬ Ù…ÙØªØ§Ø­ Gemini Ø¹Ø´Ø§Ù† Ø£Ù„Ø®ØµÙ„Ùƒ ÙŠØ§Ù‡Ø§")
        }
    }

    private fun summarizeLecture(text: String) {
        val prompt = "Ù„Ø®ØµÙ„ÙŠ Ù‡Ø§ÙŠ Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø© Ø¨Ù†Ù‚Ø§Ø· Ù…Ù†Ø¸Ù…Ø© ÙˆØ¨Ø³ÙŠØ·Ø©ØŒ ÙˆÙØ³Ø±Ù„ÙŠ Ø£Ù‡Ù… Ø§Ù„Ø£ÙÙƒØ§Ø± Ø¨Ø£Ø³Ù„ÙˆØ¨ Ø³Ù‡Ù„: $text"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply { put("text", prompt) }
                    ))
                }
            ))
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                saveNote("Ù…Ø­Ø§Ø¶Ø±Ø© (Ø¨Ø¯ÙˆÙ† ØªÙ„Ø®ÙŠØµ): $text")
                runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙˆØµÙ„ Ù„Ù„Ù†ØªØŒ Ø¨Ø³ Ø­ÙØ¸Øª Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø© Ø®Ø§Ù… ÙƒÙ…Ù„Ø§Ø­Ø¸Ø©") }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    val summary = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    saveNote("Ù…Ù„Ø®Øµ Ù…Ø­Ø§Ø¶Ø±Ø©: ${summary.trim()}")
                    runOnUiThread { respond(summary.trim()) }
                } catch (e: Exception) {
                    saveNote("Ù…Ø­Ø§Ø¶Ø±Ø© (Ø¨Ø¯ÙˆÙ† ØªÙ„Ø®ÙŠØµ): $text")
                    runOnUiThread { respond("Ø³Ø¬Ù„Øª Ø§Ù„Ù…Ø­Ø§Ø¶Ø±Ø© Ø¨Ø³ Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£Ù„Ø®ØµÙ‡Ø§") }
                }
            }
        })
    }

    // ---------------- User name ----------------

    private fun saveUserName(name: String) {
        userName = name
        getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_name", name).apply()
    }

    // ---------------- Language switching ----------------

    private fun handleLanguageSwitch(cmd: String) {
        when {
            cmd.contains("Ø¹Ø±Ø¨ÙŠ") || cmd.contains("arabic") || cmd.contains("arabe") -> {
                currentLangCode = "ar"
                tts.language = Locale("ar")
                respond("ØªÙ…Ø§Ù…ØŒ Ø±Ø­ Ø£Ø³Ù…Ø¹Ùƒ Ø¨Ø§Ù„Ø¹Ø±Ø¨ÙŠ Ù‡Ù„Ù‚ØŒ Ø£Ù†Ø§ Ù„Ø³Ø§ Ø¬Ø§Ø±ÙØ³")
            }
            cmd.contains("ÙØ±Ù†Ø³") || cmd.contains("french") || cmd.contains("franÃ§ais") -> {
                currentLangCode = "fr"
                tts.language = Locale.FRENCH
                respond("D'accord, je t'Ã©coute en franÃ§ais maintenant, je suis toujours Jarvis")
            }
            cmd.contains("Ø§Ù†Ø¬Ù„ÙŠØ²") || cmd.contains("english") || cmd.contains("anglais") -> {
                currentLangCode = "en"
                tts.language = Locale.ENGLISH
                respond("Okay, I'm listening in English now, still Jarvis")
            }
            cmd.contains("Ø§Ø³Ø¨Ø§Ù†") || cmd.contains("spanish") || cmd.contains("espaÃ±ol") -> {
                currentLangCode = "es"
                tts.language = Locale("es")
                respond("Vale, ahora te escucho en espaÃ±ol, sigo siendo Jarvis")
            }
            cmd.contains("Ø±ÙˆØ³") || cmd.contains("russian") || cmd.contains("Ñ€ÑƒÑÑÐº") -> {
                currentLangCode = "ru"
                tts.language = Locale("ru")
                respond("Ð¥Ð¾Ñ€Ð¾ÑˆÐ¾, Ñ‚ÐµÐ¿ÐµÑ€ÑŒ Ñ ÑÐ»ÑƒÑˆÐ°ÑŽ Ð¿Ð¾-Ñ€ÑƒÑÑÐºÐ¸, Ñ Ð²ÑÑ‘ Ñ‚Ð¾Ñ‚ Ð¶Ðµ Ð”Ð¶Ð°Ñ€Ð²Ð¸Ñ")
            }
            cmd.contains("Ù…Ø§Ù†Ø¯Ø±ÙŠÙ†") || cmd.contains("ØµÙŠÙ†ÙŠ") || cmd.contains("mandarin") ||
                    cmd.contains("chinese") || cmd.contains("ä¸­æ–‡") -> {
                currentLangCode = "zh"
                tts.language = Locale.SIMPLIFIED_CHINESE
                respond("å¥½çš„ï¼ŒçŽ°åœ¨æˆ‘å¬ä¸­æ–‡äº†ï¼Œæˆ‘è¿˜æ˜¯è´¾ç»´æ–¯")
            }
            else -> {
                respond("Ù‚Ù„ÙŠ Ø¹Ø±Ø¨ÙŠØŒ ÙØ±Ù†Ø³ÙŠØŒ Ø§Ù†Ø¬Ù„ÙŠØ²ÙŠØŒ Ø§Ø³Ø¨Ø§Ù†ÙŠØŒ Ø±ÙˆØ³ÙŠØŒ Ø£Ùˆ Ù…Ø§Ù†Ø¯Ø±ÙŠÙ†")
            }
        }
    }

    // ---------------- Battery & date ----------------

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ---------------- Volume & ringer mode ----------------

    private fun adjustVolume(up: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun setRingerMode(mode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = mode
            respond("ØªÙ… ØªØºÙŠÙŠØ± ÙˆØ¶Ø¹ Ø§Ù„ØµÙˆØª")
        } catch (e: SecurityException) {
            respond("Ø¨Ø¯ÙŠ Ø¥Ø°Ù† Ø§Ù„ÙˆØµÙˆÙ„ Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø¹Ø¯Ù… Ø§Ù„Ø¥Ø²Ø¹Ø§Ø¬ Ø£ÙˆÙ„ Ù…Ù† Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ù‡Ø§ØªÙ")
        }
    }

    // ---------------- Alarm ----------------

    private fun handleSetAlarm(cmd: String) {
        val regex = Regex("""(\d{1,2})(?:[:Ùˆ]\s*(\d{1,2}))?""")
        val match = regex.find(cmd)
        if (match == null) {
            respond("Ù‚Ù„ÙŠ Ø§Ù„ÙˆÙ‚Øª Ù‡ÙŠÙƒ: Ù…Ù†Ø¨Ù‡ Ø§Ù„Ø³Ø§Ø¹Ø© 7")
            return
        }
        val hour = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Ù…Ù†Ø¨Ù‡ Ù…Ù† Ø¬Ø§Ø±ÙØ³")
        }
        try {
            startActivity(intent)
            respond("ØªÙ…Ø§Ù…ØŒ Ø­Ø·ÙŠØª Ù…Ù†Ø¨Ù‡ Ø§Ù„Ø³Ø§Ø¹Ø© $hour Ùˆ $minute")
        } catch (e: Exception) {
            respond("Ù…Ø§ Ù„Ù‚ÙŠØª ØªØ·Ø¨ÙŠÙ‚ Ù…Ù†Ø¨Ù‡ Ø¹Ù„Ù‰ Ù‡Ø§ØªÙÙƒ")
        }
    }

    // ---------------- Search & navigation ----------------

    private fun extractSearchQuery(cmd: String): String {
        val marker = if (cmd.contains("Ø§Ø¨Ø­Ø« Ø¹Ù†")) "Ø§Ø¨Ø­Ø« Ø¹Ù†" else "Ø¯ÙˆØ± Ù„ÙŠ Ø¹Ù„Ù‰"
        return extractNameAfter(cmd, marker)
    }

    private fun searchGoogle(query: String) {
        if (query.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ø´Ùˆ Ø¨Ø¯Ùƒ Ø£Ø¨Ø­Ø« Ø¹Ù†Ù‡")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            startActivity(intent)
            respond("Ø¨Ø¯ÙˆØ± Ù„Ùƒ Ø¹Ù† $query")
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
                )
                startActivity(browserIntent)
                respond("Ø¨Ø¯ÙˆØ± Ù„Ùƒ Ø¹Ù† $query")
            } catch (e2: Exception) {
                respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙØªØ­ Ø§Ù„Ø¨Ø­Ø«")
            }
        }
    }

    private fun navigateTo(place: String, mode: String = "driving") {
        if (place.isBlank()) {
            respond("Ù‚Ù„ÙŠ ÙˆÙŠÙ† Ø¨Ø¯Ùƒ ØªØ±ÙˆØ­")
            return
        }
        val encodedPlace = Uri.encode(place)
        val mapsUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$encodedPlace&travelmode=$mode"
        )
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, mapsUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
            respondNavigation(place, mode)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, mapsUri))
                respondNavigation(place, mode)
            } catch (e2: Exception) {
                respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙØªØ­ Ø§Ù„Ø®Ø±Ø§Ø¦Ø·")
            }
        }
    }

    private fun respondNavigation(place: String, mode: String) {
        if (mode == "walking") {
            respond("Ù‡Ø§Ùƒ Ø·Ø±ÙŠÙ‚ Ø§Ù„Ù…Ø´ÙŠ Ø§Ù„Ù‰ $place")
        } else {
            respond("Ø¬Ø§Ø±ÙŠ ÙØªØ­ Ø§Ù„Ø·Ø±ÙŠÙ‚ Ø§Ù„Ù‰ $place")
        }
    }

    // ---------------- Jokes ----------------

    private val jokes = listOf(
        "ÙˆØ§Ø­Ø¯ Ø³Ø£Ù„ ØµØ§Ø­Ø¨Ùˆ: Ø¹Ù„Ø§Ø´ Ø§Ù„Ø¯ÙŠÙƒ ÙŠØµÙŠØ­ Ø§Ù„ØµØ¨Ø§Ø­ØŸ Ù‚Ø§Ù„Ù‡: Ø¨Ø§Ø´ ÙŠÙÙˆÙ‚Ùƒ Ù‚Ø¨Ù„ Ù…Ø§ ØªÙÙˆØªÙ‡ Ø¨Ø§Ù„Ù†ÙˆÙ….",
        "Ø·ÙÙ„ Ø³Ø£Ù„ Ø¨Ø§Ø¨Ø§Ù‡: Ø¨Ø§Ø¨Ø§ ÙˆÙŠÙ† ØªØ­Ø¨ ØªÙƒÙˆÙ† Ù„Ù…Ø§ ØªÙƒØ¨Ø±ØŸ Ù‚Ø§Ù„Ù‡: Ù‡Ø§Ø¯ÙŠ Ù‡ÙŠ Ø§Ù„Ù…Ø´ÙƒÙ„Ø©ØŒ Ø£Ù†Ø§ ÙƒØ¨Ø±Øª ÙˆÙ…Ø§ Ø²Ù„Øª Ù…Ø§ Ø¹Ø±ÙØªØ´.",
        "ÙˆØ§Ø­Ø¯ Ø¯Ø®Ù„ ÙŠØ´ØªØ±ÙŠ Ø³Ø§Ø¹Ø©ØŒ Ù‚Ø§Ù„Ù‡ Ø§Ù„Ø¨ÙŠØ§Ø¹: Ù‡Ø§ÙŠ Ø§Ù„Ø³Ø§Ø¹Ø© Ø¨ØªØ¹ÙŠØ´ Ù…Ø¹Ø§Ùƒ Ù„Ù„Ø£Ø¨Ø¯. Ù‚Ø§Ù„Ù‡: Ø·ÙŠØ¨ Ø£Ø¹Ø·ÙŠÙ†ÙŠ ÙˆØ­Ø¯Ø© ØªØ¹ÙŠØ´ Ø£Ø³Ø¨ÙˆØ¹ Ø¨Ø³ØŒ Ø®Ø§ÙŠÙ Ù†Ø¶ÙŠØ¹Ù‡Ø§.",
        "Ø¹Ù„Ø§Ø´ Ø§Ù„ÙƒÙ…Ø¨ÙŠÙˆØªØ± Ù…Ø§ Ø¨ÙŠØ­Ø³ Ø¨Ø§Ù„Ø¨Ø±Ø¯ØŸ Ù„Ø£Ù†Ù‡ Ø¹Ù†Ø¯Ù‡ Windows Ù…Ø³ÙƒØ±Ø© Ø²ÙŠÙ†."
    )

    // ---------------- Notes ----------------

    private fun saveNote(note: String) {
        val prefs = getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("notes", mutableSetOf()) ?: mutableSetOf()
        val updated = existing.toMutableSet()
        updated.add(note)
        prefs.edit().putStringSet("notes", updated).apply()
    }

    private fun readNotes(): String {
        val prefs = getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)
        val notes = prefs.getStringSet("notes", setOf()) ?: setOf()
        if (notes.isEmpty()) return "Ù…Ø§ Ø¹Ù†Ø¯Ùƒ Ù…Ù„Ø§Ø­Ø¸Ø§Øª Ù…Ø­ÙÙˆØ¸Ø©"
        return "Ù…Ù„Ø§Ø­Ø¸Ø§ØªÙƒ: " + notes.joinToString("ØŒ ")
    }

    // ---------------- Natural response variety ----------------

    private val flashOnPhrases = listOf(
        "Ø¯Ø§ÙŠØ±Ù„Ùƒ Ø§Ù„ÙÙ„Ø§Ø´", "ØªÙ…Ø§Ù…ØŒ ÙˆÙ„Ù‘Ù‰ Ø§Ù„ÙÙ„Ø§Ø´ Ø´Ø§Ø¹Ù„", "Ù‡Ø§Ùƒ Ø§Ù„ÙÙ„Ø§Ø´ Ø´Ø§Ø¹Ù„"
    )
    private val flashOffPhrases = listOf(
        "Ø·ÙÙŠØª Ø§Ù„ÙÙ„Ø§Ø´", "ØªÙ…Ø§Ù…ØŒ Ø§Ù„ÙÙ„Ø§Ø´ Ø·Ø§ÙÙŠ Ù‡Ù„Ù‚", "Ø®Ù„Ø§Øµ Ø·ÙØ§Ù‡"
    )
    private val musicOnPhrases = listOf(
        "Ù‡Ø§ÙƒÙ‡Ø§ Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰ Ø¨Ø¯Ø§Øª", "ØªÙ…Ø§Ù…ØŒ Ù†Ø¯ÙŠØ±Ù„Ùƒ Ù…ÙˆØ³ÙŠÙ‚Ù‰", "Ø§Ø³ØªÙ…ØªØ¹ Ø¨Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰"
    )
    private val musicOffPhrases = listOf(
        "ÙˆÙ‚ÙØª Ø§Ù„Ù…ÙˆØ³ÙŠÙ‚Ù‰", "ØªÙ…Ø§Ù…ØŒ Ø³ÙƒØªÙ‡Ø§"
    )

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            respond("Ø¬Ø§Ø±ÙŠ ÙØªØ­ $appName")
        } else {
            respond("$appName Ù…Ø´ Ù…Ø«Ø¨Øª Ø¹Ù„Ù‰ Ø¬Ù‡Ø§Ø²Ùƒ")
        }
    }

    // ---------------- Call a contact ----------------

    private fun extractNameAfter(cmd: String, marker: String): String {
        val idx = cmd.indexOf(marker)
        if (idx == -1) return ""
        return cmd.substring(idx + marker.length).trim()
    }

    private fun callContact(name: String) {
        if (name.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ù…ÙŠÙ† Ø¨Ø¯Ùƒ Ø£ØªØµÙ„ ÙÙŠÙ‡")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            respond("Ø¨Ø¯ÙŠ Ø¥Ø°Ù† Ù‚Ø±Ø§Ø¡Ø© Ø¬Ù‡Ø§Øª Ø§Ù„Ø§ØªØµØ§Ù„ Ø£ÙˆÙ„ØŒ Ø¬Ø±Ø¨ Ù…Ø±Ø© ØªØ§Ù†ÙŠØ©")
            return
        }
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val contactId = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        val number = pc.getString(
                            pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        )
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        startActivity(dialIntent)
                        respond("Ø¬Ø§Ø±ÙŠ Ø§Ù„Ø§ØªØµØ§Ù„ Ø¨Ù€ $name")
                    } else {
                        respond("Ù…Ø§ Ù„Ù‚ÙŠØª Ø±Ù‚Ù… Ù‡Ø§ØªÙ Ù„Ù€ $name")
                    }
                }
            } else {
                respond("Ù…Ø§ Ù„Ù‚ÙŠØª Ø¬Ù‡Ø© Ø§ØªØµØ§Ù„ Ø¨Ø§Ø³Ù… $name")
            }
        }
    }

    private fun writeCode(topic: String) {
        if (topic.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ø´Ùˆ Ø§Ù„ÙƒÙˆØ¯ ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ Ø§ÙŠØ§Ù‡")
            return
        }
        if (GEMINI_API_KEY.isBlank()) {
            respond("Ù„Ø§Ø²Ù… ØªØ­Ø· Ù…ÙØªØ§Ø­ Gemini Ø§Ù„Ø£ÙˆÙ„ Ø¹Ø´Ø§Ù† Ø£Ù‚Ø¯Ø± Ø£ÙƒØªØ¨Ù„Ùƒ ÙƒÙˆØ¯")
            return
        }
        respond("Ø®Ù„ÙŠÙ†ÙŠ Ø£ÙƒØªØ¨Ù„Ùƒ Ø§Ù„ÙƒÙˆØ¯...")
        val prompt = "Ø§ÙƒØªØ¨ ÙƒÙˆØ¯ Ø¨Ø±Ù…Ø¬ÙŠ ÙˆØ§Ø¶Ø­ ÙˆÙ…Ø±ØªØ¨ Ù„Ù€: $topic. Ø§Ø´Ø±Ø­ Ø¨Ø¬Ù…Ù„Ø© Ù‚ØµÙŠØ±Ø© Ø´Ùˆ Ø¨ÙŠØ³ÙˆÙŠ Ø§Ù„ÙƒÙˆØ¯."
        askGeminiForCode(prompt)
    }

    private fun askGeminiForCode(prompt: String) {
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply { put("text", prompt) }
                    ))
                }
            ))
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙˆØµÙ„ Ù„Ù„Ù†Øª") }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    val code = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    runOnUiThread {
                        log("Ø¬Ø§Ø±ÙØ³:\n${code.trim()}")
                        tts.speak("ÙƒØªØ¨ØªÙ„Ùƒ Ø§Ù„ÙƒÙˆØ¯ Ø¨Ø§Ù„Ø´Ø§Ø´Ø©ØŒ Ø´ÙˆÙÙ‡", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                } catch (e: Exception) {
                    runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙÙ‡Ù… Ø±Ø¯ Gemini") }
                }
            }
        })
    }

    // ---------------- Holographic-style product design ----------------

    private fun designHologram(description: String) {
        if (description.isBlank()) {
            respond("Ù‚Ù„ÙŠ ÙˆØµÙ Ø§Ù„Ù…Ù†ØªØ¬ ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ ØªØµÙ…Ù…Ù‡")
            return
        }
        if (GEMINI_API_KEY.isBlank()) {
            respond("Ù„Ø§Ø²Ù… ØªØ­Ø· Ù…ÙØªØ§Ø­ Gemini Ø§Ù„Ø£ÙˆÙ„ Ø¹Ø´Ø§Ù† Ø£Ù‚Ø¯Ø± Ø£ØµÙ…Ù…Ù„Ùƒ")
            return
        }
        respond("Ø¨ØµÙ…Ù…Ù„Ùƒ...")
        val prompt = "Ø¨Ù†Ø§Ø¡Ù‹ Ø¹Ù„Ù‰ Ù‡Ø§Ø¯ Ø§Ù„ÙˆØµÙ: \"$description\"ØŒ Ø§ÙƒØªØ¨Ù„ÙŠ Ù…ÙˆØ§ØµÙØ§Øª ØªØµÙ…ÙŠÙ… Ù…Ù†Ø¸Ù…Ø© ÙˆÙ‚ØµÙŠØ±Ø© " +
                "(Ø§Ø³Ù… Ø§Ù„Ù…Ù†ØªØ¬ØŒ Ø§Ù„Ù‚ÙŠØ§Ø³Ø§Øª Ù„Ùˆ Ù…ÙˆØ¬ÙˆØ¯Ø©ØŒ Ø§Ù„Ù…ÙˆØ§Ø¯ Ø§Ù„Ù…Ù‚ØªØ±Ø­Ø©ØŒ ÙˆØµÙ Ù…Ø®ØªØµØ± Ø¨Ø¬Ù…Ù„ØªÙŠÙ†)ØŒ " +
                "Ø¨Ø´ÙƒÙ„ Ù†Ù‚Ø§Ø· Ù‚ØµÙŠØ±Ø© ØªØµÙ„Ø­ ØªÙ†Ø¹Ø±Ø¶ Ø¨Ø´Ø§Ø´Ø© Ù‡ÙˆÙ„ÙˆØ¬Ø±Ø§Ù…ÙŠØ©"
        askGeminiForHologram(prompt)
    }

    private fun askGeminiForHologram(prompt: String) {
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply { put("text", prompt) }
                    ))
                }
            ))
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", GEMINI_API_KEY)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£ÙˆØµÙ„ Ù„Ù„Ù†Øª") }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    val spec = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    runOnUiThread {
                        showHologramDialog(spec.trim())
                        respond("Ù‡Ø§Ùƒ Ø§Ù„ØªØµÙ…ÙŠÙ…")
                    }
                } catch (e: Exception) {
                    runOnUiThread { respond("Ù…Ø§ Ù‚Ø¯Ø±Øª Ø£Ø¬Ù‡Ø² Ø§Ù„ØªØµÙ…ÙŠÙ…") }
                }
            }
        })
    }

    private fun showHologramDialog(specText: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#000000"))
        }

        val textView = TextView(this).apply {
            text = specText
            setTextColor(Color.parseColor("#00F6FF"))
            textSize = 18f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            setShadowLayer(24f, 0f, 0f, Color.parseColor("#00F6FF"))
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        container.addView(textView, textParams)

        val closeButton = Button(this).apply {
            text = "âœ• Ø¥ØºÙ„Ø§Ù‚"
            setTextColor(Color.parseColor("#00F6FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dialog.dismiss() }
        }
        val closeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 60
            rightMargin = 40
        }
        container.addView(closeButton, closeParams)

        dialog.setContentView(container)
        dialog.show()

        ObjectAnimator.ofFloat(textView, "rotationY", 0f, 360f).apply {
            duration = 6000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(textView, "alpha", 1f, 0.6f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    // ---------------- Explain any topic (geology, etc.) via Gemini ----------------

    private fun extractExplainTopic(cmd: String): String {
        val marker = when {
            cmd.contains("Ø§Ø´Ø±Ø­Ù„ÙŠ") -> "Ø§Ø´Ø±Ø­Ù„ÙŠ"
            cmd.contains("Ø§Ø´Ø±Ø­ Ù„ÙŠ") -> "Ø§Ø´Ø±Ø­ Ù„ÙŠ"
            cmd.contains("ÙÙ‡Ù…Ù†ÙŠ") -> "ÙÙ‡Ù…Ù†ÙŠ"
            cmd.contains("Ø§ÙØªØ­ Ù…ÙˆØ¶ÙˆØ¹") -> "Ù…ÙˆØ¶ÙˆØ¹"
            cmd.contains("Ø´Ùˆ Ù‡Ùˆ") -> "Ø´Ùˆ Ù‡Ùˆ"
            else -> "Ø´Ùˆ Ù‡ÙŠ"
        }
        return extractNameAfter(cmd, marker)
    }

    private val offlineKnowledge = mapOf(
        "Ø§Ù„Ø§Ø³Ù„Ø§Ù…" to "Ø§Ù„Ø¥Ø³Ù„Ø§Ù… Ø¯ÙŠÙ† ØªÙˆØ­ÙŠØ¯ÙŠØŒ Ù†Ø²Ù„ Ø¹Ù„Ù‰ Ø§Ù„Ù†Ø¨ÙŠ Ù…Ø­Ù…Ø¯ ØµÙ„Ù‰ Ø§Ù„Ù„Ù‡ Ø¹Ù„ÙŠÙ‡ ÙˆØ³Ù„Ù… Ø¨Ø§Ù„Ù‚Ø±Ø¢Ù† Ø§Ù„ÙƒØ±ÙŠÙ…. Ù…Ù† Ø£Ø±ÙƒØ§Ù†Ù‡ Ø§Ù„Ø®Ù…Ø³Ø©: Ø§Ù„Ø´Ù‡Ø§Ø¯ØªÙŠÙ†ØŒ Ø§Ù„ØµÙ„Ø§Ø©ØŒ Ø§Ù„Ø²ÙƒØ§Ø©ØŒ Ø§Ù„ØµÙŠØ§Ù… Ø¨Ø±Ù…Ø¶Ø§Ù†ØŒ ÙˆØ§Ù„Ø­Ø¬ Ù„Ù…Ù† Ø§Ø³ØªØ·Ø§Ø¹. ÙŠØ¤Ù…Ù† Ø§Ù„Ù…Ø³Ù„Ù…ÙˆÙ† Ø¨Ø§Ù„Ù„Ù‡ Ø§Ù„ÙˆØ§Ø­Ø¯ØŒ ÙˆØ¨Ø§Ù„Ø£Ù†Ø¨ÙŠØ§Ø¡ ÙˆØ§Ù„Ø±Ø³Ù„ Ù…Ù† Ù‚Ø¨Ù„ Ù…Ø­Ù…Ø¯ Ù…ØªÙ„ Ù…ÙˆØ³Ù‰ ÙˆØ¹ÙŠØ³Ù‰ Ø¹Ù„ÙŠÙ‡Ù… Ø§Ù„Ø³Ù„Ø§Ù….",
        "Ø§Ù„Ù…Ø³ÙŠØ­ÙŠØ©" to "Ø§Ù„Ù…Ø³ÙŠØ­ÙŠØ© Ø¯ÙŠÙ† ØªÙˆØ­ÙŠØ¯ÙŠ ÙŠÙ‚ÙˆÙ… Ø¹Ù„Ù‰ ØªØ¹Ø§Ù„ÙŠÙ… Ø§Ù„Ø³ÙŠØ¯ Ø§Ù„Ù…Ø³ÙŠØ­ Ø¹ÙŠØ³Ù‰ Ø¨Ù† Ù…Ø±ÙŠÙ… ÙƒÙ…Ø§ ÙˆØ±Ø¯Øª Ø¨Ø§Ù„Ø¥Ù†Ø¬ÙŠÙ„. Ù…Ù† Ø£Ù‡Ù… Ù…Ø¹ØªÙ‚Ø¯Ø§ØªÙ‡Ø§ ÙÙƒØ±Ø© Ø§Ù„Ø«Ø§Ù„ÙˆØ« Ø§Ù„Ø£Ù‚Ø¯Ø³ (Ø§Ù„Ø¢Ø¨ ÙˆØ§Ù„Ø§Ø¨Ù† ÙˆØ§Ù„Ø±ÙˆØ­ Ø§Ù„Ù‚Ø¯Ø³)ØŒ ÙˆØ·Ù‚ÙˆØ³Ù‡Ø§ Ø§Ù„Ø£Ø³Ø§Ø³ÙŠØ© ØªØ´Ù…Ù„ Ø§Ù„Ù…Ø¹Ù…ÙˆØ¯ÙŠØ© ÙˆØ§Ù„Ù‚Ø±Ø¨Ø§Ù† Ø§Ù„Ù…Ù‚Ø¯Ø³ØŒ ÙˆÙÙŠÙ‡Ø§ Ø·ÙˆØ§Ø¦Ù ÙƒØ¨Ø±Ù‰ Ù…ØªÙ„ Ø§Ù„ÙƒØ§Ø«ÙˆÙ„ÙŠÙƒ ÙˆØ§Ù„Ø£Ø±Ø«ÙˆØ°ÙƒØ³ ÙˆØ§Ù„Ø¨Ø±ÙˆØªØ³ØªØ§Ù†Øª.",
        "Ø§Ù„ÙŠÙ‡ÙˆØ¯ÙŠØ©" to "Ø§Ù„ÙŠÙ‡ÙˆØ¯ÙŠØ© Ù…Ù† Ø£Ù‚Ø¯Ù… Ø§Ù„Ø¯ÙŠØ§Ù†Ø§Øª Ø§Ù„ØªÙˆØ­ÙŠØ¯ÙŠØ©ØŒ ÙƒØªØ§Ø¨Ù‡Ø§ Ø§Ù„Ù…Ù‚Ø¯Ø³ Ø§Ù„ØªÙˆØ±Ø§Ø© (Ø§Ù„Ø¹Ù‡Ø¯ Ø§Ù„Ù‚Ø¯ÙŠÙ…). ØªØ¤Ù…Ù† Ø¨Ù†Ø¨ÙˆØ© Ù…ÙˆØ³Ù‰ Ø¹Ù„ÙŠÙ‡ Ø§Ù„Ø³Ù„Ø§Ù… ÙˆØ§Ø³ØªÙ„Ø§Ù…Ù‡ Ø§Ù„ÙˆØµØ§ÙŠØ§ Ø§Ù„Ø¹Ø´Ø±ØŒ ÙˆÙ…Ù† Ø´Ø¹Ø§Ø¦Ø±Ù‡Ø§ Ø§Ù„Ø£Ø³Ø§Ø³ÙŠØ© Ø§Ù„Ø³Ø¨Øª (ÙŠÙˆÙ… Ø§Ù„Ø±Ø§Ø­Ø©) ÙˆÙ‚ÙˆØ§Ø¹Ø¯ Ø§Ù„Ø·Ø¹Ø§Ù… Ø§Ù„Ø­Ù„Ø§Ù„ Ø­Ø³Ø¨ Ø§Ù„Ø´Ø±ÙŠØ¹Ø© Ø§Ù„ÙŠÙ‡ÙˆØ¯ÙŠØ© (ÙƒÙˆØ´ÙŠØ±).",
        "Ø§Ù„Ø¨ÙˆØ°ÙŠØ©" to "Ø§Ù„Ø¨ÙˆØ°ÙŠØ© Ø¯ÙŠØ§Ù†Ø© ÙˆÙÙ„Ø³ÙØ© Ø±ÙˆØ­ÙŠØ© Ø£Ø³Ø³Ù‡Ø§ Ø³ÙŠØ¯Ù‡Ø§Ø±ØªØ§ ØºÙˆØªØ§Ù…Ø§ (Ø¨ÙˆØ°Ø§) Ø¨Ø§Ù„Ù‡Ù†Ø¯. ØªØ±ÙƒØ² Ø¹Ù„Ù‰ ØªØ­Ù‚ÙŠÙ‚ Ø§Ù„ØªÙ†ÙˆÙŠØ± ÙˆØ§Ù„ØªØ­Ø±Ø± Ù…Ù† Ø§Ù„Ù…Ø¹Ø§Ù†Ø§Ø© Ø¹Ù† Ø·Ø±ÙŠÙ‚ Ø§ØªØ¨Ø§Ø¹ Ø§Ù„Ø·Ø±ÙŠÙ‚ Ø§Ù„Ø«Ù…Ø§Ù†ÙŠ Ø§Ù„Ù†Ø¨ÙŠÙ„ØŒ ÙˆØªØ¤Ù…Ù† Ø¨Ù…Ø¨Ø¯Ø£ Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªØ¬Ø³Ø¯ (Ø§Ù„ÙƒØ§Ø±Ù…Ø§).",
        "Ø§Ù„Ù‡Ù†Ø¯ÙˆØ³ÙŠØ©" to "Ø§Ù„Ù‡Ù†Ø¯ÙˆØ³ÙŠØ© Ù…Ù† Ø£Ù‚Ø¯Ù… Ø§Ù„Ø¯ÙŠØ§Ù†Ø§Øª Ø¨Ø§Ù„Ø¹Ø§Ù„Ù…ØŒ Ù…ØªØ¹Ø¯Ø¯Ø© Ø§Ù„Ø¢Ù„Ù‡Ø© ÙˆÙÙŠÙ‡Ø§ ÙÙ„Ø³ÙØ§Øª Ù…ØªÙ†ÙˆØ¹Ø©. ØªØ¤Ù…Ù† Ø¨Ù…Ø¨Ø¯Ø£ Ø§Ù„ÙƒØ§Ø±Ù…Ø§ ÙˆØ¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªØ¬Ø³Ø¯ (Ø§Ù„ØªÙ†Ø§Ø³Ø®)ØŒ ÙˆÙƒØªØ¨Ù‡Ø§ Ø§Ù„Ù…Ù‚Ø¯Ø³Ø© ØªØ´Ù…Ù„ Ø§Ù„ÙÙŠØ¯Ø§ ÙˆØ§Ù„Ø¨Ù‡Ø§ØºØ§ÙØ§Ø¯ØºÙŠØªØ§ØŒ ÙˆØ£Ù‡Ù… Ø¢Ù„Ù‡ØªÙ‡Ø§ Ø¨Ø±Ø§Ù‡Ù…Ø§ ÙˆÙÙŠØ´Ù†Ùˆ ÙˆØ´ÙŠÙØ§."
    )

    private fun explainTopic(topic: String) {
        if (topic.isBlank()) {
            respond("Ù‚Ù„ÙŠ Ø´Ùˆ Ø§Ù„Ù…ÙˆØ¶ÙˆØ¹ ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ Ø£Ø´Ø±Ø­Ù„Ùƒ ÙŠØ§Ù‡")
            return
        }

        val offlineMatch = offlineKnowledge.entries.firstOrNull { topic.contains(it.key) }
        if (offlineMatch != null) {
            respond(offlineMatch.value)
            return
        }

        if (GEMINI_API_KEY.isBlank()) {
            respond("Ù„Ø§Ø²Ù… ØªØ­Ø· Ù…ÙØªØ§Ø­ Gemini Ø§Ù„Ø£ÙˆÙ„ Ø¹Ø´Ø§Ù† Ø£Ù‚Ø¯Ø± Ø£Ø´Ø±Ø­Ù„Ùƒ Ù…ÙˆØ§Ø¶ÙŠØ¹ Ø²ÙŠØ§Ø¯Ø©")
            return
        }
        respond("Ø®Ù„ÙŠÙ†ÙŠ Ø£Ø´Ø±Ø­Ù„Ùƒ...")
        val prompt = "Ø§Ø´Ø±Ø­Ù„ÙŠ Ù…ÙˆØ¶ÙˆØ¹ \"$topic\" Ø¨Ø·Ø±ÙŠÙ‚Ø© Ø³Ù‡Ù„Ø© ÙˆÙ…Ø¨Ø³Ø·Ø© Ù…Ø¹ Ù…Ø«Ø§Ù„ Ø¥Ø°Ø§ Ø£Ù…ÙƒÙ†ØŒ Ø¨Ø£Ø³Ù„ÙˆØ¨ Ù‚Ø±ÙŠØ¨ ÙˆÙ…ÙÙ‡ÙˆÙ…"
        askGemini(prompt)
    }

    // ---------------- Unit converter ----------------

    private fun convertUnits(cmd: String): String {
        val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
        val match = numberRegex.find(cmd) ?: return "Ù‚Ù„ÙŠ Ø§Ù„Ø±Ù‚Ù… ÙŠÙ„ÙŠ Ø¨Ø¯Ùƒ ØªØ­ÙˆÙ„Ù‡"
        val value = match.groupValues[1].toDouble()

        return when {
            cmd.contains("ÙƒÙŠÙ„ÙˆÙ…ØªØ±") && cmd.contains("Ù…ÙŠÙ„") -> {
                val miles = value * 0.621371
                "${value} ÙƒÙ… ÙŠØ³Ø§ÙˆÙŠ ØªÙ‚Ø±ÙŠØ¨Ù‹Ø§ ${"%.2f".format(miles)} Ù…ÙŠÙ„"
            }
            cmd.contains("ÙƒÙŠÙ„Ùˆ") && cmd.contains("Ø¨Ø§ÙˆÙ†Ø¯") -> {
                val pounds = value * 2.20462
                "${value} ÙƒÙŠÙ„Ùˆ ÙŠØ³Ø§ÙˆÙŠ ØªÙ‚Ø±ÙŠØ¨Ù‹Ø§ ${"%.2f".format(pounds)} Ø¨Ø§ÙˆÙ†Ø¯"
            }
            cmd.contains("Ù…Ø¦ÙˆÙŠØ©") && cmd.contains("ÙÙ‡Ø±Ù†Ù‡Ø§ÙŠØª") -> {
                val fahrenheit = (value * 9 / 5) + 32
                "${value} Ø¯Ø±Ø¬Ø© Ù…Ø¦ÙˆÙŠØ© ÙŠØ³Ø§ÙˆÙŠ ${"%.1f".format(fahrenheit)} ÙÙ‡Ø±Ù†Ù‡Ø§ÙŠØª"
            }
            else -> "Ù‚Ù„ÙŠ Ø§Ù„ØªØ­ÙˆÙŠÙ„ Ø¨Ù‡Ø§Ù„ØµÙŠØºØ©: Ø­ÙˆÙ„ 10 ÙƒÙŠÙ„ÙˆÙ…ØªØ± Ø§Ù„Ù‰ Ù…ÙŠÙ„"
        }
    }

    // ---------------- Fun facts ----------------

    private val funFacts = listOf(
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ ØµØ­Ø±Ø§Ø¡ Ø§Ù„Ø¬Ø²Ø§Ø¦Ø± (Ø§Ù„ØµØ­Ø±Ø§Ø¡ Ø§Ù„ÙƒØ¨Ø±Ù‰) ØªØºØ·ÙŠ Ø£ÙƒØªØ± Ù…Ù† 80% Ù…Ù† Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø¨Ù„Ø§Ø¯.",
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ Ø§Ù„Ø¹Ø³Ù„ Ù…Ø§ ÙŠÙØ³Ø¯Ø´ Ø£Ø¨Ø¯Ù‹Ø§ØŒ Ø­ØªÙ‰ Ø¨Ø¹Ø¯ Ø¢Ù„Ø§Ù Ø§Ù„Ø³Ù†ÙŠÙ†.",
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ Ø§Ù„Ù‚Ù„Ø¨ Ø§Ù„Ø¨Ø´Ø±ÙŠ ÙŠØ¯Ù‚ Ø­ÙˆØ§Ù„ÙŠ 100 Ø£Ù„Ù Ù…Ø±Ø© Ø¨Ø§Ù„ÙŠÙˆÙ… Ø§Ù„ÙˆØ§Ø­Ø¯.",
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ Ø§Ù„Ø£Ø®Ø·Ø¨ÙˆØ· Ø¹Ù†Ø¯Ù‡ Ø«Ù„Ø§Ø«Ø© Ù‚Ù„ÙˆØ¨ ÙˆØ¯Ù…Ù‡ Ù„ÙˆÙ†Ù‡ Ø£Ø²Ø±Ù‚.",
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ Ø§Ù„Ø¶ÙˆØ¡ Ù…Ù† Ø§Ù„Ø´Ù…Ø³ ÙŠÙˆØµÙ„ Ù„Ù„Ø£Ø±Ø¶ Ø¨Ø­ÙˆØ§Ù„ÙŠ 8 Ø¯Ù‚Ø§ÙŠÙ‚ Ø¨Ø³.",
        "Ù‡Ù„ ØªØ¹Ø±ÙØŸ Ø¬Ø¨Ù„ Ø·ÙˆØ¨Ù‚Ø§Ù„ Ø¨Ø§Ù„Ù…ØºØ±Ø¨ Ù‡Ùˆ Ø£Ø¹Ù„Ù‰ Ù‚Ù…Ø© Ø¨Ø´Ù…Ø§Ù„ Ø£ÙØ±ÙŠÙ‚ÙŠØ§."
    )

    // ---------------- Suggestions ----------------

    private fun suggestDrawing(): String {
        val ideas = listOf(
            "Ø§Ø±Ø³Ù… Ù…Ù†Ø¸Ø± Ø·Ø¨ÙŠØ¹ÙŠ ÙÙŠÙ‡ Ø¬Ø¨Ø§Ù„ ÙˆØ¨Ø­Ø±",
            "Ø¬Ø±Ø¨ ØªØ±Ø³Ù… Ø¨ÙˆØ±ØªØ±ÙŠÙ‡ Ù„Ø´Ø®Øµ Ù‚Ø±ÙŠØ¨ Ù…Ù†Ùƒ",
            "Ø§Ø±Ø³Ù… Ø­ÙŠÙˆØ§Ù† Ø£Ù„ÙŠÙ Ø¨Ø£Ø³Ù„ÙˆØ¨ ÙƒØ±ØªÙˆÙ†ÙŠ",
            "Ø¬Ø±Ø¨ Ø±Ø³Ù… Ù…Ø¯ÙŠÙ†Ø© Ø®ÙŠØ§Ù„ÙŠØ© Ù…Ù† Ø®ÙŠØ§Ù„Ùƒ",
            "Ø§Ø±Ø³Ù… Ù„ÙˆØ­Ø© ØªØ¬Ø±ÙŠØ¯ÙŠØ© Ø¨Ø§Ù„Ø£Ù„ÙˆØ§Ù† ÙŠÙ„ÙŠ Ø¨ØªØ­Ø¨Ù‡Ø§"
        )
        return "ÙÙƒØ±Ø© Ø±Ø³Ù…Ø© Ø§Ù„ÙŠÙˆÙ…: ${ideas.random()}"
    }

    private fun suggestBreakfast(): String {
        val ideas = listOf(
            "Ø¨ÙŠØ¶ Ù…Ø¹ Ø²Ø¹ØªØ± ÙˆØ²ÙŠØª Ø²ÙŠØªÙˆÙ† ÙˆØ®Ø¨Ø² Ø·Ø§Ø²Ø©",
            "ÙÙˆÙ„ Ù…Ø¯Ù…Ø³ Ù…Ø¹ Ø®Ø¶Ø±Ø© ÙˆÙ„ÙŠÙ…ÙˆÙ†",
            "Ù„Ø¨Ù†Ø© Ù…Ø¹ Ø®ÙŠØ§Ø± ÙˆØ·Ù…Ø§Ø·Ù…",
            "Ù…Ù†Ø§Ù‚ÙŠØ´ Ø²Ø¹ØªØ± Ø£Ùˆ Ø¬Ø¨Ù†Ø©",
            "Ø´ÙƒØ´ÙˆÙƒØ© Ø¨Ø§Ù„Ø¨ÙŠØ¶ ÙˆØ§Ù„Ø¨Ù†Ø¯ÙˆØ±Ø©"
        )
        return "Ø§Ù‚ØªØ±Ø§Ø­ ÙØ·ÙˆØ± Ø§Ù„ÙŠÙˆÙ…: ${ideas.random()}"
    }

    // ---------------- Distance between cities ----------------

    private val cityCoordinates = mapOf(
        "Ø¯Ù…Ø´Ù‚" to Pair(33.5138, 36.2765),
        "Ø­Ù„Ø¨" to Pair(36.2021, 37.1343),
        "Ø­Ù…Øµ" to Pair(34.7324, 36.7137),
        "Ø­Ù…Ø§Ø©" to Pair(35.1318, 36.7578),
        "Ø§Ù„Ù„Ø§Ø°Ù‚ÙŠØ©" to Pair(35.5317, 35.7911),
        "Ø·Ø±Ø·ÙˆØ³" to Pair(34.8890, 35.8866),
        "Ø¥Ø¯Ù„Ø¨" to Pair(35.9306, 36.6339),
        "Ø¯Ø±Ø¹Ø§" to Pair(32.6189, 36.1021),
        "Ø¨ÙŠØ±ÙˆØª" to Pair(33.8938, 35.5018),
        "Ø¹Ù…Ø§Ù†" to Pair(31.9454, 35.9284),
        "Ø§Ù„Ù‚Ø¯Ø³" to Pair(31.7683, 35.2137),
        "Ø§Ù„Ù‚Ø§Ù‡Ø±Ø©" to Pair(30.0444, 31.2357),
        "Ø¨ØºØ¯Ø§Ø¯" to Pair(33.3152, 44.3661),
        "Ø§Ù„Ø±ÙŠØ§Ø¶" to Pair(24.7136, 46.6753),
        "Ø§Ø³Ø·Ù†Ø¨ÙˆÙ„" to Pair(41.0082, 28.9784),
        "Ø¨Ø§Ø±ÙŠØ³" to Pair(48.8566, 2.3522),
        "Ù„Ù†Ø¯Ù†" to Pair(51.5074, -0.1278),
        "Ø§Ù„Ø¬Ø²Ø§Ø¦Ø±" to Pair(36.7538, 3.0588),
        "ØªÙˆÙ†Ø³" to Pair(36.8065, 10.1815),
        "Ø§Ù„Ø±Ø¨Ø§Ø·" to Pair(34.0209, -6.8416),
        "Ø§Ù„Ø¯Ø§Ø± Ø§Ù„Ø¨ÙŠØ¶Ø§Ø¡" to Pair(33.5731, -7.5898),
        "Ø·Ø±Ø§Ø¨Ù„Ø³" to Pair(32.8872, 13.1913),
        // ÙˆÙ„Ø§ÙŠØ§Øª Ø§Ù„Ø¬Ø²Ø§Ø¦Ø± (58 ÙˆÙ„Ø§ÙŠØ©)
        "Ø£Ø¯Ø±Ø§Ø±" to Pair(27.8702, -0.2911),
        "Ø§Ù„Ø´Ù„Ù" to Pair(36.1650, 1.3350),
        "Ø§Ù„Ø£ØºÙˆØ§Ø·" to Pair(33.8000, 2.8650),
        "Ø£Ù… Ø§Ù„Ø¨ÙˆØ§Ù‚ÙŠ" to Pair(35.8770, 7.1170),
        "Ø¨Ø§ØªÙ†Ø©" to Pair(35.5560, 6.1740),
        "Ø¨Ø¬Ø§ÙŠØ©" to Pair(36.7530, 5.0840),
        "Ø¨Ø³ÙƒØ±Ø©" to Pair(34.8500, 5.7280),
        "Ø¨Ø´Ø§Ø±" to Pair(31.6150, -2.2180),
        "Ø§Ù„Ø¨Ù„ÙŠØ¯Ø©" to Pair(36.4700, 2.8280),
        "Ø§Ù„Ø¨ÙˆÙŠØ±Ø©" to Pair(36.3730, 3.9020),
        "ØªÙ…Ù†Ø±Ø§Ø³Øª" to Pair(22.7850, 5.5220),
        "ØªØ¨Ø³Ø©" to Pair(35.4040, 8.1240),
        "ØªÙ„Ù…Ø³Ø§Ù†" to Pair(34.8780, -1.3150),
        "ØªÙŠØ§Ø±Øª" to Pair(35.3710, 1.3170),
        "ØªÙŠØ²ÙŠ ÙˆØ²Ùˆ" to Pair(36.7120, 4.0450),
        "Ø§Ù„Ø¬Ù„ÙØ©" to Pair(34.6730, 3.2630),
        "Ø¬ÙŠØ¬Ù„" to Pair(36.8220, 5.7660),
        "Ø³Ø·ÙŠÙ" to Pair(36.1910, 5.4080),
        "Ø³Ø¹ÙŠØ¯Ø©" to Pair(34.8300, 0.1510),
        "Ø³ÙƒÙŠÙƒØ¯Ø©" to Pair(36.8760, 6.9090),
        "Ø³ÙŠØ¯ÙŠ Ø¨Ù„Ø¹Ø¨Ø§Ø³" to Pair(35.1900, -0.6300),
        "Ø¹Ù†Ø§Ø¨Ø©" to Pair(36.9000, 7.7670),
        "Ù‚Ø§Ù„Ù…Ø©" to Pair(36.4620, 7.4270),
        "Ù‚Ø³Ù†Ø·ÙŠÙ†Ø©" to Pair(36.3650, 6.6150),
        "Ø§Ù„Ù…Ø¯ÙŠØ©" to Pair(36.2640, 2.7540),
        "Ù…Ø³ØªØºØ§Ù†Ù…" to Pair(35.9350, 0.0890),
        "Ø§Ù„Ù…Ø³ÙŠÙ„Ø©" to Pair(35.7050, 4.5410),
        "Ù…Ø¹Ø³ÙƒØ±" to Pair(35.3970, 0.1400),
        "ÙˆØ±Ù‚Ù„Ø©" to Pair(31.9490, 5.3250),
        "ÙˆÙ‡Ø±Ø§Ù†" to Pair(35.6970, -0.6330),
        "Ø§Ù„Ø¨ÙŠØ¶" to Pair(33.6860, 1.0190),
        "Ø¥Ù„ÙŠØ²ÙŠ" to Pair(26.4830, 8.4670),
        "Ø¨Ø±Ø¬ Ø¨ÙˆØ¹Ø±ÙŠØ±ÙŠØ¬" to Pair(36.0730, 4.7610),
        "Ø¨ÙˆÙ…Ø±Ø¯Ø§Ø³" to Pair(36.7660, 3.4770),
        "Ø§Ù„Ø·Ø§Ø±Ù" to Pair(36.7670, 8.3130),
        "ØªÙ†Ø¯ÙˆÙ" to Pair(27.6710, -8.1470),
        "ØªÙŠØ³Ù…Ø³ÙŠÙ„Øª" to Pair(35.6070, 1.8110),
        "Ø§Ù„ÙˆØ§Ø¯ÙŠ" to Pair(33.3680, 6.8670),
        "Ø®Ù†Ø´Ù„Ø©" to Pair(35.4360, 7.1430),
        "Ø³ÙˆÙ‚ Ø£Ù‡Ø±Ø§Ø³" to Pair(36.2860, 7.9510),
        "ØªÙŠØ¨Ø§Ø²Ø©" to Pair(36.5890, 2.4480),
        "Ù…ÙŠÙ„Ø©" to Pair(36.4500, 6.2640),
        "Ø¹ÙŠÙ† Ø§Ù„Ø¯ÙÙ„Ù‰" to Pair(36.2640, 1.9660),
        "Ø§Ù„Ù†Ø¹Ø§Ù…Ø©" to Pair(33.2660, -0.3170),
        "Ø¹ÙŠÙ† ØªÙ…ÙˆØ´Ù†Øª" to Pair(35.2980, -1.1400),
        "ØºØ±Ø¯Ø§ÙŠØ©" to Pair(32.4910, 3.6730),
        "ØºÙ„ÙŠØ²Ø§Ù†" to Pair(35.7370, 0.5560),
        "ØªÙŠÙ…ÙŠÙ…ÙˆÙ†" to Pair(29.2630, 0.2310),
        "Ø¨Ø±Ø¬ Ø¨Ø§Ø¬ÙŠ Ù…Ø®ØªØ§Ø±" to Pair(21.3280, 0.9560),
        "Ø£ÙˆÙ„Ø§Ø¯ Ø¬Ù„Ø§Ù„" to Pair(34.4120, 5.0680),
        "Ø¨Ù†ÙŠ Ø¹Ø¨Ø§Ø³" to Pair(30.1300, -2.1640),
        "Ø¹ÙŠÙ† ØµØ§Ù„Ø­" to Pair(27.1940, 2.4780),
        "Ø¹ÙŠÙ† Ù‚Ø²Ø§Ù…" to Pair(19.5730, 5.7710),
        "ØªÙ‚Ø±Øª" to Pair(33.1060, 6.0580),
        "Ø¬Ø§Ù†Øª" to Pair(24.5540, 9.4830),
        "Ø§Ù„Ù…ØºÙŠØ±" to Pair(33.9450, 5.9270),
        "Ø§Ù„Ù…Ù†ÙŠØ¹Ø©" to Pair(30.5790, 2.8820)
    )

    private fun handleDistanceQuery(cmd: String) {
        val regex = Regex("""Ù…Ù†\s+(\S+)\s+(?:Ø§Ù„Ù‰|Ø¥Ù„Ù‰)\s+(\S+)""")
        val match = regex.find(cmd)
        if (match == null) {
            respond("Ù‚Ù„ÙŠ Ø§Ù„Ù…Ø³Ø§ÙØ© Ø¨Ù‡Ø§Ù„ØµÙŠØºØ©: ÙƒÙ… Ø§Ù„Ù…Ø³Ø§ÙØ© Ù…Ù† Ø¯Ù…Ø´Ù‚ Ø§Ù„Ù‰ Ø­Ù„Ø¨")
            return
        }
        val cityA = match.groupValues[1]
        val cityB = match.groupValues[2]

        if (GOOGLE_MAPS_API_KEY.isNotBlank()) {
            respond("Ø¨Ø­Ø³Ø¨...")
            askGoogleDistance(cityA, cityB)
        } else {
            respond(calculateDistanceOffline(cityA, cityB))
        }
    }

    private fun askGoogleDistance(cityA: String, cityB: String) {
        val originEnc = java.net.URLEncoder.encode(cityA, "UTF-8")
        val destEnc = java.net.URLEncoder.encode(cityB, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$originEnc&destinations=$destEnc&key=$GOOGLE_MAPS_API_KEY"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { respond(calculateDistanceOffline(cityA, cityB)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    val status = json.optString("status")
                    if (status != "OK") {
                        runOnUiThread { respond(calculateDistanceOffline(cityA, cityB)) }
                        return
                    }
                    val element = json.getJSONArray("rows")
                        .getJSONObject(0)
                        .getJSONArray("elements")
                        .getJSONObject(0)
                    val elementStatus = element.optString("status")
                    if (elementStatus != "OK") {
                        runOnUiThread { respond(calculateDistanceOffline(cityA, cityB)) }
                        return
                    }
                    val distanceText = element.getJSONObject("distance").getString("text")
                    val durationText = element.getJSONObject("duration").getString("text")
                    runOnUiThread {
                        respond("Ø§Ù„Ù…Ø³Ø§ÙØ© Ù…Ù† $cityA Ø§Ù„Ù‰ $cityB Ø­ÙˆØ§Ù„ÙŠ $distanceText Ø¨Ø§Ù„Ø³ÙŠØ§Ø±Ø©ØŒ ÙˆÙˆÙ‚Øª Ø§Ù„Ø±Ø­Ù„Ø© ØªÙ‚Ø±ÙŠØ¨Ù‹Ø§ $durationText")
                    }
                } catch (e: Exception) {
                    runOnUiThread { respond(calculateDistanceOffline(cityA, cityB)) }
                }
            }
        })
    }

    private fun calculateDistanceOffline(cityA: String, cityB: String): String {
        val coordA = cityCoordinates[cityA]
        val coordB = cityCoordinates[cityB]
        if (coordA == null || coordB == null) {
            return "Ù„Ù„Ø£Ø³Ù Ù…Ø§ Ø¹Ù†Ø¯ÙŠ Ø¥Ø­Ø¯Ø§Ø«ÙŠØ§Øª Ù„Ù‡Ø§ÙŠ Ø§Ù„Ù…Ø¯ÙŠÙ†Ø© Ø­Ø§Ù„ÙŠÙ‹Ø§"
        }
        val distanceKm = haversine(coordA.first, coordA.second, coordB.first, coordB.second)
        return "Ø§Ù„Ù…Ø³Ø§ÙØ© Ù…Ù† $cityA Ø§Ù„Ù‰ $cityB Ø­ÙˆØ§Ù„ÙŠ ${distanceKm.toInt()} ÙƒÙ… (Ø®Ø· Ù…Ø³ØªÙ‚ÙŠÙ… ØªÙ‚Ø±ÙŠØ¨ÙŠ)"
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    // ---------------- Output helpers ----------------

    private fun respond(text: String) {
        log("Ø¬Ø§Ø±ÙØ³: $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun log(text: String) {
        logText.append("\n\n$text")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        stopMusic()
        speechRecognizer?.destroy()
    }
}
