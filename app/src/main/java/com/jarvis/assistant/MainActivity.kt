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
            log("Ø¬Ø§Ø±ÙØ³: Ø£Ù‡Ù„Ø§ ${userName}ØŒ Ù…Ø¨Ø³ÙˆØ· Ø¥Ù†Ùƒ Ø±Ø¬Ø¹Øª")
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
                    respond("ØªØ´Ø±ÙØª ÙÙŠÙƒ ÙŠØ§ ${name}ØŒ Ù…Ù† Ù‡Ù„Ù‚ Ø±Ø­ Ø£Ø¹Ø±ÙÙƒ")
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
            cmd.contains("ÙˆØ¶Ø¹ 
