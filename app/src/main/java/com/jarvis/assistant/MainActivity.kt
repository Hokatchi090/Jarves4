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

    // ---- \u0636\u064A\u0641 \u0645\u0641\u062A\u0627\u062D Google Gemini \u0627\u0644\u062E\u0627\u0635 \u0641\u064A\u0643 \u0647\u0648\u0646 \u0628\u064A\u0646 \u0639\u0644\u0627\u0645\u062A\u064A \u0627\u0644\u062A\u0646\u0635\u064A\u0635 ----
    // \u0627\u062D\u0635\u0644 \u0639\u0644\u064A\u0647 \u0645\u062C\u0627\u0646\u064B\u0627 \u0645\u0646: https://aistudio.google.com/apikey
    // \u062E\u0644\u064A\u0647 \u0641\u0627\u0636\u064A "" \u0625\u0630\u0627 \u0628\u062F\u0643 \u062A\u0628\u0642\u064A \u062C\u0627\u0631\u0641\u0633 \u0623\u0648\u0641\u0644\u0627\u064A\u0646 \u0628\u0627\u0644\u0643\u0627\u0645\u0644
    private val GEMINI_API_KEY = "AQ.Ab8RN6LgH3zeyTqUtRWkHS-0CvJZccqUBh-AWTjpe97ieIevjg"

    // ---- \u0636\u064A\u0641 \u0645\u0641\u062A\u0627\u062D Google Maps \u0647\u0648\u0646 \u0644\u0645\u0633\u0627\u0641\u0627\u062A \u062D\u0642\u064A\u0642\u064A\u0629 \u0628\u0627\u0644\u0637\u0631\u064A\u0642 ----
    // \u0627\u062D\u0635\u0644 \u0639\u0644\u064A\u0647 \u0645\u0646: https://console.cloud.google.com/google/maps-apis
    // \u062E\u0644\u064A\u0647 \u0641\u0627\u0636\u064A "" \u0625\u0630\u0627 \u0628\u062F\u0643 \u064A\u0633\u062A\u062E\u062F\u0645 \u062D\u0633\u0627\u0628 \u062A\u0642\u0631\u064A\u0628\u064A (\u062E\u0637 \u0645\u0633\u062A\u0642\u064A\u0645) \u0628\u062F\u0648\u0646 \u0645\u0641\u062A\u0627\u062D
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
            log("\u062C\u0627\u0631\u0641\u0633: \u0623\u0647\u0644\u0627 ${userName}\u060C \u0645\u0628\u0633\u0648\u0637 \u0625\u0646\u0643 \u0631\u062C\u0639\u062A")
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
        button.text = "\u23F9\uFE0F"
        statusText.text = "\u0628\u0633\u0645\u0639\u0643... \u0642\u0648\u0644 \"\u062C\u0627\u0631\u0641\u0633\""
        findViewById<View>(R.id.statusDot).setBackgroundResource(R.drawable.status_dot)
        findViewById<View>(R.id.statusDot).alpha = 1f
        startPulseAnimation(button)
        startListening()
    }

    private fun disableContinuousMode(button: Button) {
        continuousMode = false
        button.text = "\uD83C\uDF99\uFE0F"
        statusText.text = "\u062C\u0627\u0647\u0632 \u0644\u0644\u0627\u0633\u062A\u0645\u0627\u0639"
        findViewById<View>(R.id.statusDot).alpha = 0.3f
        stopPulseAnimation(button)
        log("\u062A\u0648\u0642\u0641 \u0648\u0636\u0639 \u0627\u0644\u0627\u0633\u062A\u0645\u0627\u0639 \u0627\u0644\u0645\u0633\u062A\u0645\u0631")
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
            button.text = "\uD83C\uDF99\uFE0F"
            statusText.text = "\u062C\u0627\u0647\u0632 \u0644\u0644\u0627\u0633\u062A\u0645\u0627\u0639"
            stopPulseAnimation(button)
            log("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0628\u0644\u0634 \u0627\u0644\u0627\u0633\u062A\u0645\u0627\u0639")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // \u0628\u064A\u0635\u064A\u0631 \u0639\u0627\u062F\u064A \u0648\u0642\u062A \u0627\u0644\u0635\u0645\u062A \u0623\u0648 \u0627\u0644\u0636\u062C\u064A\u062C\u060C \u0645\u0646\u0639\u064A\u062F \u0627\u0644\u0627\u0633\u062A\u0645\u0627\u0639 \u0625\u0630\u0627 \u0644\u0633\u0627 \u0628\u0648\u0636\u0639 \u0645\u0633\u062A\u0645\u0631
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
            if (spoken.contains("\u0648\u0642\u0641 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629") || spoken.contains("\u062E\u0644\u0635\u062A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629") ||
                spoken.contains("\u0627\u0646\u0647\u064A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629")
            ) {
                stopLectureModeAndSummarize()
            } else if (spoken.isNotBlank()) {
                lectureBuffer.append(spoken).append(". ")
                log("\uD83D\uDCDD $spoken")
            }
            if (continuousMode) startListening()
            return
        }

        if (continuousMode) {
            val lower = spoken.lowercase(Locale.getDefault())
            val wakeIndex = when {
                spoken.contains("\u062C\u0627\u0631\u0641\u0633") -> spoken.indexOf("\u062C\u0627\u0631\u0641\u0633").let { it + "\u062C\u0627\u0631\u0641\u0633".length }
                lower.contains("jarvis") -> lower.indexOf("jarvis") + "jarvis".length
                spoken.contains("\u0434\u0436\u0430\u0440\u0432\u0438\u0441") -> spoken.indexOf("\u0434\u0436\u0430\u0440\u0432\u0438\u0441") + "\u0434\u0436\u0430\u0440\u0432\u0438\u0441".length
                spoken.contains("\u8D3E\u7EF4\u65AF") -> spoken.indexOf("\u8D3E\u7EF4\u65AF") + "\u8D3E\u7EF4\u65AF".length
                else -> -1
            }
            if (wakeIndex != -1) {
                val commandOnly = spoken.substring(wakeIndex.coerceAtMost(spoken.length)).trim()
                log("\u0623\u0646\u062A: $commandOnly")
                if (commandOnly.isNotBlank()) handleCommand(commandOnly)
            }
            if (continuousMode) startListening()
        } else if (spoken.isNotBlank()) {
            log("\u0623\u0646\u062A: $spoken")
            handleCommand(spoken)
        }
    }

    // ---------------- Command routing ----------------

    private fun handleCommand(text: String) {
        try {
            handleCommandInternal(text)
        } catch (e: Exception) {
            respond("\u0635\u0627\u0631 \u062E\u0637\u0623 \u0628\u0633\u064A\u0637\u060C \u0628\u0633 \u0623\u0646\u0627 \u0644\u0633\u0627 \u0634\u063A\u0627\u0644\u060C \u062C\u0631\u0628 \u0623\u0645\u0631 \u062A\u0627\u0646\u064A")
        }
    }

    private fun handleCommandInternal(text: String) {
        val cmd = text.lowercase(Locale("ar")).trim()

        when {
            cmd.contains("\u0627\u0628\u062F\u0627 \u0645\u062D\u0627\u0636\u0631\u0629") || cmd.contains("\u0627\u0628\u062F\u0623 \u0645\u062D\u0627\u0636\u0631\u0629") ||
                    cmd.contains("\u0633\u062C\u0644 \u0645\u062D\u0627\u0636\u0631\u0629") -> {
                startLectureMode()
            }
            cmd.contains("\u0627\u0633\u0645\u064A ") -> {
                val name = extractNameAfter(cmd, "\u0627\u0633\u0645\u064A")
                if (name.isNotBlank()) {
                    saveUserName(name)
                    respond("\u062A\u0634\u0631\u0641\u062A \u0641\u064A\u0643 \u064A\u0627 ${name}\u060C \u0645\u0646 \u0647\u0644\u0642 \u0631\u062D \u0623\u0639\u0631\u0641\u0643")
                } else {
                    respond("\u0642\u0644\u064A \u0627\u0633\u0645\u0643\u061F")
                }
            }
            cmd.contains("\u0634\u0648 \u0627\u0633\u0645\u064A") || cmd.contains("\u0648\u0634 \u0627\u0633\u0645\u064A") -> {
                if (userName.isNotBlank()) {
                    respond("\u0627\u0633\u0645\u0643 $userName")
                } else {
                    respond("\u0645\u0627 \u062A\u0642\u0644\u064A \u0627\u0633\u0645\u0643 \u0644\u0633\u0627\u060C \u0642\u0644\u064A \u0627\u0633\u0645\u064A \u0641\u0644\u0627\u0646")
                }
            }
            cmd.contains("\u0627\u0646\u0633\u0649 \u0627\u0633\u0645\u064A") -> {
                userName = ""
                getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit()
                    .remove("user_name").apply()
                respond("\u062A\u0645\u0627\u0645\u060C \u0646\u0633\u064A\u062A \u0627\u0633\u0645\u0643")
            }
            cmd.contains("\u0634\u063A\u0644 \u0627\u0644\u0641\u0644\u0627\u0634") || cmd.contains("\u0627\u0641\u062A\u062D \u0627\u0644\u0641\u0644\u0627\u0634") ||
                    cmd.contains("\u0634\u0639\u0644 \u0627\u0644\u0641\u0644\u0627\u0634") || cmd.contains("\u0634\u0639\u0644 \u0641\u0644\u0627\u0634") ||
                    cmd.contains("\u0634\u063A\u0644 \u0641\u0644\u0627\u0634") ||
                    cmd.contains("turn on the flash") || cmd.contains("turn on flash") ||
                    cmd.contains("allume la lampe") || cmd.contains("allume le flash") -> {
                setFlashlight(true)
                respond(flashOnPhrases.random())
            }
            cmd.contains("\u0637\u0641\u064A \u0627\u0644\u0641\u0644\u0627\u0634") || cmd.contains("\u0627\u0637\u0641\u064A \u0627\u0644\u0641\u0644\u0627\u0634") ||
                    cmd.contains("\u0637\u0641\u0626 \u0627\u0644\u0641\u0644\u0627\u0634") ||
                    cmd.contains("turn off the flash") || cmd.contains("turn off flash") ||
                    cmd.contains("\u00E9teins la lampe") || cmd.contains("\u00E9teins le flash") -> {
                setFlashlight(false)
                respond(flashOffPhrases.random())
            }
            cmd.contains("\u063A\u064A\u0631 \u0627\u0644\u0644\u063A\u0629") || cmd.contains("change language") || cmd.contains("changer la langue") -> {
                handleLanguageSwitch(cmd)
            }
            cmd.contains("\u0634\u063A\u0644 \u0645\u0648\u0633\u064A\u0642\u0649") || cmd.contains("\u0634\u063A\u0644 \u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649") ||
                    cmd.contains("play music") || cmd.contains("joue de la musique") ||
                    cmd.contains("lance la musique") -> {
                playMusic()
                respond(musicOnPhrases.random())
            }
            cmd.contains("\u0648\u0642\u0641 \u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649") || cmd.contains("\u0637\u0641\u064A \u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649") ||
                    cmd.contains("stop music") || cmd.contains("arr\u00EAte la musique") -> {
                stopMusic()
                respond(musicOffPhrases.random())
            }
            cmd.contains("\u0634\u063A\u0644 \u0627\u063A\u0646\u064A\u0629") || cmd.contains("\u0634\u063A\u0644 \u0623\u063A\u0646\u064A\u0629") -> {
                val name = extractNameAfter(cmd, "\u0627\u063A\u0646\u064A\u0629").ifBlank { extractNameAfter(cmd, "\u0623\u063A\u0646\u064A\u0629") }
                playSongByName(name)
            }
            cmd.contains("\u0627\u0644\u0627\u063A\u0646\u064A\u0629 \u0627\u0644\u0644\u064A \u0628\u0639\u062F\u0647\u0627") || cmd.contains("\u0627\u0644\u0623\u063A\u0646\u064A\u0629 \u0627\u0644\u0644\u064A \u0628\u0639\u062F\u0647\u0627") ||
                    cmd.contains("\u0627\u0644\u0627\u063A\u0646\u064A\u0629 \u0627\u0644\u062C\u0627\u064A\u0629") || cmd.contains("\u0627\u063A\u0646\u064A\u0629 \u0628\u0639\u062F\u0647\u0627") ||
                    cmd.contains("\u0627\u0644\u062A\u0627\u0644\u064A") -> {
                playNextInPlaylist()
            }
            cmd.contains("\u0636\u064A\u0641 \u0627\u063A\u0646\u064A\u0629") || cmd.contains("\u0636\u064A\u0641 \u0623\u063A\u0646\u064A\u0629") -> {
                val name = extractNameAfter(cmd, "\u0627\u063A\u0646\u064A\u0629").ifBlank { extractNameAfter(cmd, "\u0623\u063A\u0646\u064A\u0629") }
                addSongToPlaylist(name)
            }
            cmd.contains("\u0634\u0648 \u0642\u0627\u0626\u0645\u062A\u064A") || cmd.contains("\u0627\u0639\u0631\u0636 \u0627\u0644\u0642\u0627\u0626\u0645\u0629") -> {
                respond(showPlaylist())
            }
            cmd.contains("\u0627\u0645\u0633\u062D \u0627\u0644\u0642\u0627\u0626\u0645\u0629") -> {
                clearPlaylist()
            }
            cmd.contains("\u0627\u062D\u0633\u0628") || containsMath(cmd) -> {
                val result = calculate(cmd)
                respond(result)
            }
            cmd.contains("\u0630\u0643\u0631\u0646\u064A") || cmd.contains("\u062A\u0630\u0643\u064A\u0631") -> {
                // Expects something like: "\u0630\u0643\u0631\u0646\u064A \u0628\u0639\u062F 10 \u062F\u0642\u0627\u064A\u0642 \u0627\u0634\u0631\u0628 \u0645\u064A"
                val minutes = extractMinutes(cmd) ?: 5
                scheduleReminder(minutes, cmd)
                respond("\u0642\u0628\u0648\u0644\u060C \u0631\u062D \u0646\u0641\u0643\u0631\u0643 \u0628\u0639\u062F $minutes \u062F\u0642\u064A\u0642\u0629")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u0627\u0646\u0633\u062A\u0642\u0631\u0627\u0645") || cmd.contains("\u0627\u0641\u062A\u062D \u0627\u0646\u0633\u062A\u063A\u0631\u0627\u0645") ||
                    cmd.contains("open instagram") || cmd.contains("ouvre instagram") -> {
                openApp("com.instagram.android", "\u0627\u0646\u0633\u062A\u0642\u0631\u0627\u0645")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u064A\u0648\u062A\u064A\u0648\u0628") || cmd.contains("\u0627\u0641\u062A\u062D \u064A\u0648\u062A\u0648\u0628") ||
                    cmd.contains("open youtube") || cmd.contains("ouvre youtube") -> {
                openApp("com.google.android.youtube", "\u064A\u0648\u062A\u064A\u0648\u0628")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u0641\u064A\u0633\u0628\u0648\u0643") ||
                    cmd.contains("open facebook") || cmd.contains("ouvre facebook") -> {
                openApp("com.facebook.katana", "\u0641\u064A\u0633\u0628\u0648\u0643")
            }
            cmd.contains("\u0627\u062A\u0635\u0644 \u0628") -> {
                val name = extractNameAfter(cmd, "\u0627\u062A\u0635\u0644 \u0628")
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
            cmd.contains("\u0631\u0633\u0645\u0629 \u0627\u0644\u064A\u0648\u0645") || cmd.contains("\u0627\u0642\u062A\u0631\u062D \u0644\u064A \u0631\u0633\u0645\u0629") ||
                    cmd.contains("drawing idea") || cmd.contains("id\u00E9e de dessin") -> {
                respond(suggestDrawing())
            }
            cmd.contains("\u0641\u0637\u0648\u0631") || cmd.contains("breakfast idea") ||
                    cmd.contains("id\u00E9e de petit") -> {
                respond(suggestBreakfast())
            }
            cmd.contains("\u0627\u0634\u0631\u062D\u0644\u064A") || cmd.contains("\u0627\u0634\u0631\u062D \u0644\u064A") || cmd.contains("\u0641\u0647\u0645\u0646\u064A") ||
                    cmd.contains("\u0627\u0641\u062A\u062D \u0645\u0648\u0636\u0648\u0639") || cmd.contains("\u0634\u0648 \u0647\u0648") || cmd.contains("\u0634\u0648 \u0647\u064A") -> {
                val topic = extractExplainTopic(cmd)
                explainTopic(topic)
            }
            cmd.contains("\u0627\u0643\u062A\u0628\u0644\u064A \u0643\u0648\u062F") || cmd.contains("\u0628\u0631\u0645\u062C\u0644\u064A") || cmd.contains("write code") -> {
                val marker = when {
                    cmd.contains("\u0627\u0643\u062A\u0628\u0644\u064A \u0643\u0648\u062F") -> "\u0643\u0648\u062F"
                    cmd.contains("\u0628\u0631\u0645\u062C\u0644\u064A") -> "\u0628\u0631\u0645\u062C\u0644\u064A"
                    else -> "code"
                }
                val topic = extractNameAfter(cmd, marker)
                writeCode(topic)
            }
            cmd.contains("\u0635\u0645\u0645\u0644\u064A") || cmd.contains("\u062A\u0635\u0645\u064A\u0645 \u0647\u0648\u0644\u0648\u062C\u0631\u0627\u0645\u064A") ||
                    cmd.contains("design hologram") -> {
                val marker = if (cmd.contains("\u0635\u0645\u0645\u0644\u064A")) "\u0635\u0645\u0645\u0644\u064A" else "\u062A\u0635\u0645\u064A\u0645 \u0647\u0648\u0644\u0648\u062C\u0631\u0627\u0645\u064A"
                val description = extractNameAfter(cmd, marker)
                designHologram(description)
            }
            cmd.contains("\u062D\u0648\u0644") && (cmd.contains("\u0643\u064A\u0644\u0648\u0645\u062A\u0631") || cmd.contains("\u0645\u064A\u0644") ||
                    cmd.contains("\u0643\u064A\u0644\u0648") || cmd.contains("\u0628\u0627\u0648\u0646\u062F") ||
                    cmd.contains("\u0645\u0626\u0648\u064A\u0629") || cmd.contains("\u0641\u0647\u0631\u0646\u0647\u0627\u064A\u062A")) -> {
                respond(convertUnits(cmd))
            }
            cmd.contains("\u0645\u0639\u0644\u0648\u0645\u0629 \u0639\u0634\u0648\u0627\u0626\u064A\u0629") || cmd.contains("\u0645\u0639\u0644\u0648\u0645\u0629 \u0627\u0644\u064A\u0648\u0645") ||
                    cmd.contains("random fact") -> {
                respond(funFacts.random())
            }
            cmd.contains("\u0627\u0644\u0628\u0637\u0627\u0631\u064A\u0629") || cmd.contains("battery") -> {
                respond("\u0627\u0644\u0628\u0637\u0627\u0631\u064A\u0629 \u0639\u0646\u062F ${getBatteryLevel()}%")
            }
            cmd.contains("\u0627\u0644\u062A\u0627\u0631\u064A\u062E") || cmd.contains("date") -> {
                val today = java.text.SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date())
                respond("\u0627\u0644\u062A\u0627\u0631\u064A\u062E \u0627\u0644\u064A\u0648\u0645 $today")
            }
            cmd.contains("\u0627\u0631\u0641\u0639 \u0627\u0644\u0635\u0648\u062A") || cmd.contains("\u0632\u0648\u062F \u0627\u0644\u0635\u0648\u062A") -> {
                adjustVolume(true)
                respond("\u0631\u0641\u0639\u062A \u0627\u0644\u0635\u0648\u062A")
            }
            cmd.contains("\u0646\u0632\u0644 \u0627\u0644\u0635\u0648\u062A") || cmd.contains("\u062E\u0641\u0636 \u0627\u0644\u0635\u0648\u062A") -> {
                adjustVolume(false)
                respond("\u0646\u0632\u0644\u062A \u0627\u0644\u0635\u0648\u062A")
            }
            cmd.contains("\u0648\u0636\u0639 \u0627\u0644\u0635\u0627\u0645\u062A") -> {
                setRingerMode(AudioManager.RINGER_MODE_SILENT)
            }
            cmd.contains("\u0648\u0636\u0639 \u0627\u0644\u0627\u0647\u062A\u0632\u0627\u0632") -> {
                setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
            }
            cmd.contains("\u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u0639\u0627\u062F\u064A") || cmd.contains("\u0631\u062C\u0639 \u0627\u0644\u0635\u0648\u062A \u0627\u0644\u0639\u0627\u062F\u064A") -> {
                setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            }
            cmd.contains("\u0645\u0646\u0628\u0647 \u0627\u0644\u0633\u0627\u0639\u0629") || cmd.contains("\u062D\u0637 \u0645\u0646\u0628\u0647") -> {
                handleSetAlarm(cmd)
            }
            cmd.contains("\u0627\u0628\u062D\u062B \u0639\u0646") || cmd.contains("\u062F\u0648\u0631 \u0644\u064A \u0639\u0644\u0649") -> {
                val query = extractSearchQuery(cmd)
                searchGoogle(query)
            }
            cmd.contains("\u0637\u0631\u064A\u0642 \u0645\u0634\u064A") || cmd.contains("\u0627\u0645\u0634\u064A \u0627\u0644\u0649") ||
                    cmd.contains("\u0627\u0645\u0634\u064A \u0644") || cmd.contains("\u0645\u0634\u064A \u0627\u0644\u0649") -> {
                val place = extractNameAfter(cmd, "\u0627\u0644\u0649")
                navigateTo(place, "walking")
            }
            cmd.contains("\u0648\u062F\u0651\u064A\u0646\u064A \u0627\u0644\u0649") || cmd.contains("\u0648\u062F\u064A\u0646\u064A \u0627\u0644\u0649") ||
                    cmd.contains("\u062E\u0630\u0646\u064A \u0627\u0644\u0649") || cmd.contains("\u0627\u0644\u0637\u0631\u064A\u0642 \u0627\u0644\u0649") -> {
                val place = extractNameAfter(cmd, "\u0627\u0644\u0649")
                navigateTo(place)
            }
            cmd.contains("\u0646\u0643\u062A\u0629") || cmd.contains("joke") -> {
                respond(jokes.random())
            }
            cmd.contains("\u062F\u0648\u0646 \u0645\u0644\u0627\u062D\u0638\u0629") || cmd.contains("\u0633\u062C\u0644 \u0645\u0644\u0627\u062D\u0638\u0629") -> {
                val note = extractNameAfter(cmd, "\u0645\u0644\u0627\u062D\u0638\u0629")
                if (note.isNotBlank()) {
                    saveNote(note)
                    respond("\u0633\u062C\u0644\u062A \u0627\u0644\u0645\u0644\u0627\u062D\u0638\u0629")
                } else {
                    respond("\u0642\u0644\u064A \u0634\u0648 \u0627\u0644\u0645\u0644\u0627\u062D\u0638\u0629 \u064A\u0644\u064A \u0628\u062F\u0643 \u062A\u0633\u062C\u0644\u0647\u0627")
                }
            }
            cmd.contains("\u0627\u0642\u0631\u0627 \u0627\u0644\u0645\u0644\u0627\u062D\u0638\u0627\u062A") || cmd.contains("\u0634\u0648 \u0645\u0644\u0627\u062D\u0638\u0627\u062A\u064A") -> {
                respond(readNotes())
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u0648\u0627\u062A\u0633\u0627\u0628") || cmd.contains("open whatsapp") -> {
                openApp("com.whatsapp", "\u0648\u0627\u062A\u0633\u0627\u0628")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u062A\u064A\u0643 \u062A\u0648\u0643") || cmd.contains("open tiktok") -> {
                openApp("com.zhiliaoapp.musically", "\u062A\u064A\u0643 \u062A\u0648\u0643")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u062A\u0648\u064A\u062A\u0631") || cmd.contains("\u0627\u0641\u062A\u062D \u0625\u0643\u0633") || cmd.contains("open twitter") -> {
                openApp("com.twitter.android", "\u062A\u0648\u064A\u062A\u0631")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u062E\u0631\u0627\u0626\u0637") || cmd.contains("open maps") -> {
                openApp("com.google.android.apps.maps", "\u0627\u0644\u062E\u0631\u0627\u0626\u0637")
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627") || cmd.contains("open camera") -> {
                try {
                    startActivity(Intent("android.media.action.IMAGE_CAPTURE"))
                    respond("\u062C\u0627\u0631\u064A \u0641\u062A\u062D \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627")
                } catch (e: Exception) {
                    respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u062A\u062D \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627")
                }
            }
            cmd.contains("\u0627\u0641\u062A\u062D \u0627\u0644\u0627\u0639\u062F\u0627\u062F\u0627\u062A") || cmd.contains("open settings") -> {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    respond("\u062C\u0627\u0631\u064A \u0641\u062A\u062D \u0627\u0644\u0625\u0639\u062F\u0627\u062F\u0627\u062A")
                } catch (e: Exception) {
                    respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u062A\u062D \u0627\u0644\u0625\u0639\u062F\u0627\u062F\u0627\u062A")
                }
            }
            (cmd.contains("\u0645\u0633\u0627\u0641\u0629") || cmd.contains("\u0645\u0633\u0627\u0641\u0647")) &&
                    (cmd.contains("\u0627\u0644\u0649") || cmd.contains("\u0625\u0644\u0649")) -> {
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
            log("\u062A\u0639\u0630\u0631 \u0627\u0644\u062A\u062D\u0643\u0645 \u0628\u0627\u0644\u0641\u0644\u0627\u0634: ${e.message}")
        }
    }

    // ---------------- Music ----------------
    // Place an mp3 file named "sample_music.mp3" inside app/src/main/res/raw/

    private fun playMusic() {
        stopMusic()
        try {
            val resId = resources.getIdentifier("sample_music", "raw", packageName)
            if (resId == 0) {
                log("\u0645\u0627 \u0644\u0642\u064A\u062A \u0645\u0644\u0641 \u0645\u0648\u0633\u064A\u0642\u0649. \u0636\u064A\u0641 mp3 \u0628\u0627\u0633\u0645 sample_music.mp3 \u062F\u0627\u062E\u0644 res/raw")
                return
            }
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.start()
        } catch (e: Exception) {
            log("\u0645\u0627 \u0644\u0642\u064A\u062A \u0645\u0644\u0641 \u0645\u0648\u0633\u064A\u0642\u0649. \u0636\u064A\u0641 mp3 \u0628\u0627\u0633\u0645 sample_music.mp3 \u062F\u0627\u062E\u0644 res/raw")
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
                cmd.contains("*") || cmd.contains("/") || cmd.contains("\u0632\u0627\u0626\u062F") ||
                cmd.contains("\u0646\u0627\u0642\u0635") || cmd.contains("\u0636\u0631\u0628") || cmd.contains("\u0642\u0633\u0645\u0629") ||
                cmd.contains("\u062C\u0630\u0631") || cmd.contains("\u0646\u0633\u0628\u0629") || cmd.contains("%"))
    }

    private fun calculate(cmd: String): String {
        return try {
            if (cmd.contains("\u0646\u0633\u0628\u0629") || cmd.contains("%")) {
                val percentRegex = Regex("""(\d+(?:\.\d+)?)\s*%?[^\d]*\u0645\u0646\s*(\d+(?:\.\d+)?)""")
                val match = percentRegex.find(cmd)
                if (match != null) {
                    val percent = match.groupValues[1].toDouble()
                    val total = match.groupValues[2].toDouble()
                    val result = (percent / 100.0) * total
                    return "\u0627\u0644\u0646\u062A\u064A\u062C\u0629 \u062A\u0637\u0644\u0639 $result"
                }
            }

            if (cmd.contains("\u062C\u0630\u0631")) {
                val rootRegex = Regex("""(\d+(?:\.\d+)?)""")
                val match = rootRegex.find(cmd)
                if (match != null) {
                    val number = match.groupValues[1].toDouble()
                    val result = sqrt(number)
                    return "\u0627\u0644\u062C\u0630\u0631 \u0627\u0644\u062A\u0631\u0628\u064A\u0639\u064A \u064A\u0637\u0644\u0639 $result"
                }
            }

            var expr = cmd
                .replace("\u0627\u062D\u0633\u0628", "")
                .replace("\u0632\u0627\u0626\u062F", "+")
                .replace("\u0646\u0627\u0642\u0635", "-")
                .replace("\u0636\u0631\u0628", "*")
                .replace("\u0642\u0633\u0645\u0629", "/")
                .trim()
            val result = ExpressionBuilder(expr).build().evaluate()
            "\u0627\u0644\u0646\u062A\u064A\u062C\u0629 \u062A\u0637\u0644\u0639 $result"
        } catch (e: Exception) {
            "\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u0647\u0645 \u0627\u0644\u0639\u0645\u0644\u064A\u0629 \u0627\u0644\u062D\u0633\u0627\u0628\u064A\u0629"
        }
    }

    // ---------------- Reminders ----------------

    private fun extractMinutes(cmd: String): Int? {
        val regex = Regex("""(\d+)\s*(\u062F\u0642\u064A\u0642\u0629|\u062F\u0642\u0627\u064A\u0642|\u062F\u0642\u0627\u0626\u0642)""")
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
            log("\u0644\u0627\u0632\u0645 \u062A\u0633\u0645\u062D \u0628\u0635\u0644\u0627\u062D\u064A\u0629 'Schedule Exact Alarm' \u0645\u0646 \u0625\u0639\u062F\u0627\u062F\u0627\u062A \u0627\u0644\u0646\u0638\u0627\u0645")
        }
    }

    // ---------------- Simple chat (offline rules + optional online fallback) ----------------

    private fun chatReply(cmd: String): String {
        val offlineReply = offlineRules(cmd)
        if (offlineReply != null) return offlineReply

        if (GEMINI_API_KEY.isNotBlank()) {
            askGemini(cmd)
            return "\u0628\u0641\u0643\u0631..."
        }
        return "\u0645\u0627 \u0641\u0647\u0645\u062A \u0639\u0644\u064A\u0643 \u062A\u0645\u0627\u0645\u064B\u0627\u060C \u062C\u0631\u0628 \u0635\u064A\u063A\u0629 \u062A\u0627\u0646\u064A\u0629"
    }

    private fun offlineRules(cmd: String): String? {
        val nameSuffix = if (userName.isNotBlank()) " \u064A\u0627 $userName" else ""
        return when {
            cmd.contains("\u0645\u0631\u062D\u0628\u0627") || cmd.contains("\u0647\u0644\u0627") || cmd.contains("\u0627\u0644\u0633\u0644\u0627\u0645") ->
                listOf("\u0623\u0647\u0644\u0627 \u0628\u064A\u0643${nameSuffix}\u060C \u0648\u064A\u0646 \u0631\u0627\u0643\u061F", "\u0647\u0644\u0627${nameSuffix}\u060C \u0634\u0646\u0648 \u0646\u062F\u064A\u0631\u0644\u0643\u061F", "\u0623\u0647\u0644\u064A\u0646${nameSuffix}\u060C \u0642\u0648\u0644\u0651\u064A \u0643\u064A \u0646\u0639\u0627\u0648\u0646\u0643").random()
            cmd.contains("\u0643\u064A\u0641\u0643") || cmd.contains("\u0634\u062E\u0628\u0627\u0631\u0643") ->
                listOf("\u0644\u0627\u0628\u0627\u0633 \u0627\u0644\u062D\u0645\u062F\u0644\u0644\u0647\u060C \u0648\u0627\u0646\u062A \u0643\u064A\u0641\u0643${nameSuffix}\u061F", "\u0645\u0644\u064A\u062D \u0628\u0632\u0627\u0641\u060C \u0648\u0627\u0646\u062A\u061F").random()
            cmd.contains("\u0627\u0644\u0633\u0627\u0639\u0629") ->
                "\u0627\u0644\u0633\u0627\u0639\u0629 \u0647\u0644\u0642 ${java.text.SimpleDateFormat("HH:mm").format(Date())}"
            cmd.contains("\u0645\u064A\u0646 \u0627\u0646\u062A") || cmd.contains("\u0634\u0648 \u0627\u0633\u0645\u0643") ->
                "\u0623\u0646\u0627 \u062C\u0627\u0631\u0641\u0633\u060C \u0635\u0627\u062D\u0628\u0643 \u0627\u0644\u0634\u062E\u0635\u064A\u060C \u062C\u0627\u0647\u0632 \u0646\u0639\u0627\u0648\u0646\u0643 \u0628\u0623\u064A \u062D\u0627\u062C\u0629"
            cmd.contains("\u0634\u0643\u0631\u0627") || cmd.contains("\u064A\u0639\u0637\u064A\u0643 \u0627\u0644\u0635\u062D\u0629") ->
                listOf("\u0627\u0644\u0639\u0641\u0648\u060C \u0647\u0630\u0627 \u0648\u0627\u062C\u0628\u064A", "\u0648\u0644\u0627 \u064A\u0647\u0645\u0643\u060C \u0623\u0646\u0627 \u0647\u0646\u0627 \u0648\u0642\u062A\u0627\u0634 \u062A\u062D\u062A\u0627\u062C\u0646\u064A").random()
            else -> null
        }
    }

    private fun askGemini(message: String) {
        val nameContext = if (userName.isNotBlank()) "\u0627\u0633\u0645\u064A ${userName}\u060C \u062E\u0627\u0637\u0628\u0646\u064A \u0628\u0627\u0633\u0645\u064A \u0623\u062D\u064A\u0627\u0646\u064B\u0627. " else ""
        val identityContext = "\u0623\u0646\u062A \u062C\u0627\u0631\u0641\u0633\u060C \u0645\u0633\u0627\u0639\u062F \u0634\u062E\u0635\u064A \u0628\u0634\u062E\u0635\u064A\u0629 \u0648\u0627\u062D\u062F\u0629 \u0645\u0648\u062D\u062F\u0629 \u0628\u0643\u0644 \u0627\u0644\u0644\u063A\u0627\u062A\u060C \u062C\u0627\u0648\u0628 \u0628\u0646\u0641\u0633 \u0627\u0644\u0644\u063A\u0629 \u064A\u0644\u064A \u0625\u062C\u0627\u0643 \u0641\u064A\u0647\u0627 \u0627\u0644\u0633\u0624\u0627\u0644. "
        val promptWithStyle = "$identityContext$nameContext" +
                "\u062C\u0627\u0648\u0628\u0646\u064A \u0628\u0623\u0633\u0644\u0648\u0628 \u0637\u0628\u064A\u0639\u064A \u0648\u062F\u0627\u0641\u0626 \u0648\u0642\u0631\u064A\u0628 \u0645\u0646 \u0644\u0647\u062C\u0629 \u0627\u0644\u062D\u0643\u064A \u0627\u0644\u0639\u0627\u062F\u064A\u060C \u0631\u062F\u0648\u062F \u0642\u0635\u064A\u0631\u0629 \u0648\u0645\u0641\u0647\u0648\u0645\u0629\u060C \u0645\u0646 \u063A\u064A\u0631 \u0631\u0633\u0645\u064A\u0627\u062A \u0632\u0627\u064A\u062F\u0629: $message"
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
                runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0648\u0635\u0644 \u0644\u0644\u0646\u062A") }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseText = response.body?.string() ?: ""
                    val json = JSONObject(responseText)
                    if (json.has("error")) {
                        val errMsg = json.getJSONObject("error").optString("message", "\u062E\u0637\u0623 \u063A\u064A\u0631 \u0645\u0639\u0631\u0648\u0641")
                        runOnUiThread { respond("\u0635\u0627\u0631 \u062E\u0637\u0623 \u0645\u0646 Gemini: $errMsg") }
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
                    runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u0647\u0645 \u0631\u062F Gemini") }
                }
            }
        })
    }

    // ---------------- Play songs by name & playlist ----------------

    private fun playSongByName(name: String) {
        if (name.isBlank()) {
            respond("\u0642\u0644\u064A \u0627\u0633\u0645 \u0627\u0644\u0623\u063A\u0646\u064A\u0629")
            return
        }
        try {
            val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            intent.putExtra(SearchManager.QUERY, name)
            intent.putExtra("android.intent.extra.focus", "vnd.android.cursor.item/audio")
            startActivity(intent)
            addToPlaylistAndSetCurrent(name)
            respond("\u0647\u0627\u0643\u0647\u0627 $name")
        } catch (e: Exception) {
            respond("\u0645\u0627 \u0644\u0642\u064A\u062A \u062A\u0637\u0628\u064A\u0642 \u0645\u0648\u0633\u064A\u0642\u0649 \u064A\u0641\u0647\u0645 \u0647\u0627\u0644\u0623\u0645\u0631 \u0639\u0646\u062F\u0643")
        }
    }

    private fun playNextInPlaylist() {
        val list = getPlaylist()
        if (list.isEmpty()) {
            respond("\u0645\u0627 \u0639\u0646\u062F\u0643 \u0623\u063A\u0627\u0646\u064A \u0628\u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0644\u0633\u0627")
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
            respond("\u0647\u0627\u0643\u0647\u0627 $song")
        } catch (e: Exception) {
            respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0634\u063A\u0644 \u0627\u0644\u0623\u063A\u0646\u064A\u0629")
        }
    }

    private fun addSongToPlaylist(name: String) {
        if (name.isBlank()) {
            respond("\u0642\u0644\u064A \u0627\u0633\u0645 \u0627\u0644\u0623\u063A\u0646\u064A\u0629 \u064A\u0644\u064A \u0628\u062F\u0643 \u062A\u0636\u064A\u0641\u0647\u0627")
            return
        }
        val list = getPlaylist()
        if (!list.contains(name)) {
            list.add(name)
            savePlaylist(list)
        }
        respond("\u0636\u0641\u062A $name \u0644\u0644\u0642\u0627\u0626\u0645\u0629")
    }

    private fun showPlaylist(): String {
        val list = getPlaylist()
        if (list.isEmpty()) return "\u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0641\u0627\u0636\u064A\u0629 \u0644\u0633\u0627"
        return "\u0642\u0627\u0626\u0645\u062A\u0643: " + list.joinToString("\u060C ")
    }

    private fun clearPlaylist() {
        savePlaylist(emptyList())
        setCurrentIndex(-1)
        respond("\u0645\u0633\u062D\u062A \u0627\u0644\u0642\u0627\u0626\u0645\u0629")
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
        statusText.text = "\uD83D\uDCDD \u0648\u0636\u0639 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629... \u0642\u0648\u0644 \"\u0648\u0642\u0641 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629\" \u0644\u0645\u0627 \u062A\u062E\u0644\u0635"
        respond("\u062A\u0645\u0627\u0645\u060C \u0628\u0644\u0634\u062A \u0623\u0633\u0645\u0639 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629\u060C \u0642\u0644\u064A \u0648\u0642\u0641 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629 \u0644\u0645\u0627 \u062A\u062E\u0644\u0635")
    }

    private fun stopLectureModeAndSummarize() {
        lectureMode = false
        statusText.text = if (continuousMode) "\uD83D\uDD34 \u0628\u0633\u0645\u0639\u0643... \u0642\u0648\u0644 \"\u062C\u0627\u0631\u0641\u0633\"" else "\u26AA \u0645\u062A\u0648\u0642\u0641\u060C \u062F\u0648\u0633 \u0644\u062A\u0634\u063A\u0651\u0644 \u0627\u0644\u0627\u0633\u062A\u0645\u0627\u0639"
        val fullText = lectureBuffer.toString().trim()
        if (fullText.isBlank()) {
            respond("\u0645\u0627 \u0633\u062C\u0644\u062A \u0634\u064A\u060C \u062C\u0631\u0628 \u062A\u0627\u0646\u064A")
            return
        }
        respond("\u062E\u0644\u0635\u062A\u060C \u0639\u0645 \u0644\u062E\u0635\u0644\u0643 \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629...")
        if (GEMINI_API_KEY.isNotBlank()) {
            summarizeLecture(fullText)
        } else {
            saveNote("\u0645\u062D\u0627\u0636\u0631\u0629: $fullText")
            respond("\u0633\u062C\u0644\u062A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629 \u0643\u0627\u0645\u0644\u0629 \u0643\u0645\u0644\u0627\u062D\u0638\u0629\u060C \u0628\u0633 \u0645\u062D\u062A\u0627\u062C \u0645\u0641\u062A\u0627\u062D Gemini \u0639\u0634\u0627\u0646 \u0623\u0644\u062E\u0635\u0644\u0643 \u064A\u0627\u0647\u0627")
        }
    }

    private fun summarizeLecture(text: String) {
        val prompt = "\u0644\u062E\u0635\u0644\u064A \u0647\u0627\u064A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629 \u0628\u0646\u0642\u0627\u0637 \u0645\u0646\u0638\u0645\u0629 \u0648\u0628\u0633\u064A\u0637\u0629\u060C \u0648\u0641\u0633\u0631\u0644\u064A \u0623\u0647\u0645 \u0627\u0644\u0623\u0641\u0643\u0627\u0631 \u0628\u0623\u0633\u0644\u0648\u0628 \u0633\u0647\u0644: $text"
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
                saveNote("\u0645\u062D\u0627\u0636\u0631\u0629 (\u0628\u062F\u0648\u0646 \u062A\u0644\u062E\u064A\u0635): $text")
                runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0648\u0635\u0644 \u0644\u0644\u0646\u062A\u060C \u0628\u0633 \u062D\u0641\u0638\u062A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629 \u062E\u0627\u0645 \u0643\u0645\u0644\u0627\u062D\u0638\u0629") }
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
                    saveNote("\u0645\u0644\u062E\u0635 \u0645\u062D\u0627\u0636\u0631\u0629: ${summary.trim()}")
                    runOnUiThread { respond(summary.trim()) }
                } catch (e: Exception) {
                    saveNote("\u0645\u062D\u0627\u0636\u0631\u0629 (\u0628\u062F\u0648\u0646 \u062A\u0644\u062E\u064A\u0635): $text")
                    runOnUiThread { respond("\u0633\u062C\u0644\u062A \u0627\u0644\u0645\u062D\u0627\u0636\u0631\u0629 \u0628\u0633 \u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0644\u062E\u0635\u0647\u0627") }
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
            cmd.contains("\u0639\u0631\u0628\u064A") || cmd.contains("arabic") || cmd.contains("arabe") -> {
                currentLangCode = "ar"
                tts.language = Locale("ar")
                respond("\u062A\u0645\u0627\u0645\u060C \u0631\u062D \u0623\u0633\u0645\u0639\u0643 \u0628\u0627\u0644\u0639\u0631\u0628\u064A \u0647\u0644\u0642\u060C \u0623\u0646\u0627 \u0644\u0633\u0627 \u062C\u0627\u0631\u0641\u0633")
            }
            cmd.contains("\u0641\u0631\u0646\u0633") || cmd.contains("french") || cmd.contains("fran\u00E7ais") -> {
                currentLangCode = "fr"
                tts.language = Locale.FRENCH
                respond("D'accord, je t'\u00E9coute en fran\u00E7ais maintenant, je suis toujours Jarvis")
            }
            cmd.contains("\u0627\u0646\u062C\u0644\u064A\u0632") || cmd.contains("english") || cmd.contains("anglais") -> {
                currentLangCode = "en"
                tts.language = Locale.ENGLISH
                respond("Okay, I'm listening in English now, still Jarvis")
            }
            cmd.contains("\u0627\u0633\u0628\u0627\u0646") || cmd.contains("spanish") || cmd.contains("espa\u00F1ol") -> {
                currentLangCode = "es"
                tts.language = Locale("es")
                respond("Vale, ahora te escucho en espa\u00F1ol, sigo siendo Jarvis")
            }
            cmd.contains("\u0631\u0648\u0633") || cmd.contains("russian") || cmd.contains("\u0440\u0443\u0441\u0441\u043A") -> {
                currentLangCode = "ru"
                tts.language = Locale("ru")
                respond("\u0425\u043E\u0440\u043E\u0448\u043E, \u0442\u0435\u043F\u0435\u0440\u044C \u044F \u0441\u043B\u0443\u0448\u0430\u044E \u043F\u043E-\u0440\u0443\u0441\u0441\u043A\u0438, \u044F \u0432\u0441\u0451 \u0442\u043E\u0442 \u0436\u0435 \u0414\u0436\u0430\u0440\u0432\u0438\u0441")
            }
            cmd.contains("\u0645\u0627\u0646\u062F\u0631\u064A\u0646") || cmd.contains("\u0635\u064A\u0646\u064A") || cmd.contains("mandarin") ||
                    cmd.contains("chinese") || cmd.contains("\u4E2D\u6587") -> {
                currentLangCode = "zh"
                tts.language = Locale.SIMPLIFIED_CHINESE
                respond("\u597D\u7684\uFF0C\u73B0\u5728\u6211\u542C\u4E2D\u6587\u4E86\uFF0C\u6211\u8FD8\u662F\u8D3E\u7EF4\u65AF")
            }
            else -> {
                respond("\u0642\u0644\u064A \u0639\u0631\u0628\u064A\u060C \u0641\u0631\u0646\u0633\u064A\u060C \u0627\u0646\u062C\u0644\u064A\u0632\u064A\u060C \u0627\u0633\u0628\u0627\u0646\u064A\u060C \u0631\u0648\u0633\u064A\u060C \u0623\u0648 \u0645\u0627\u0646\u062F\u0631\u064A\u0646")
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
            respond("\u062A\u0645 \u062A\u063A\u064A\u064A\u0631 \u0648\u0636\u0639 \u0627\u0644\u0635\u0648\u062A")
        } catch (e: SecurityException) {
            respond("\u0628\u062F\u064A \u0625\u0630\u0646 \u0627\u0644\u0648\u0635\u0648\u0644 \u0644\u0625\u0639\u062F\u0627\u062F\u0627\u062A \u0639\u062F\u0645 \u0627\u0644\u0625\u0632\u0639\u0627\u062C \u0623\u0648\u0644 \u0645\u0646 \u0625\u0639\u062F\u0627\u062F\u0627\u062A \u0627\u0644\u0647\u0627\u062A\u0641")
        }
    }

    // ---------------- Alarm ----------------

    private fun handleSetAlarm(cmd: String) {
        val regex = Regex("""(\d{1,2})(?:[:\u0648]\s*(\d{1,2}))?""")
        val match = regex.find(cmd)
        if (match == null) {
            respond("\u0642\u0644\u064A \u0627\u0644\u0648\u0642\u062A \u0647\u064A\u0643: \u0645\u0646\u0628\u0647 \u0627\u0644\u0633\u0627\u0639\u0629 7")
            return
        }
        val hour = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "\u0645\u0646\u0628\u0647 \u0645\u0646 \u062C\u0627\u0631\u0641\u0633")
        }
        try {
            startActivity(intent)
            respond("\u062A\u0645\u0627\u0645\u060C \u062D\u0637\u064A\u062A \u0645\u0646\u0628\u0647 \u0627\u0644\u0633\u0627\u0639\u0629 $hour \u0648 $minute")
        } catch (e: Exception) {
            respond("\u0645\u0627 \u0644\u0642\u064A\u062A \u062A\u0637\u0628\u064A\u0642 \u0645\u0646\u0628\u0647 \u0639\u0644\u0649 \u0647\u0627\u062A\u0641\u0643")
        }
    }

    // ---------------- Search & navigation ----------------

    private fun extractSearchQuery(cmd: String): String {
        val marker = if (cmd.contains("\u0627\u0628\u062D\u062B \u0639\u0646")) "\u0627\u0628\u062D\u062B \u0639\u0646" else "\u062F\u0648\u0631 \u0644\u064A \u0639\u0644\u0649"
        return extractNameAfter(cmd, marker)
    }

    private fun searchGoogle(query: String) {
        if (query.isBlank()) {
            respond("\u0642\u0644\u064A \u0634\u0648 \u0628\u062F\u0643 \u0623\u0628\u062D\u062B \u0639\u0646\u0647")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            startActivity(intent)
            respond("\u0628\u062F\u0648\u0631 \u0644\u0643 \u0639\u0646 $query")
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
                )
                startActivity(browserIntent)
                respond("\u0628\u062F\u0648\u0631 \u0644\u0643 \u0639\u0646 $query")
            } catch (e2: Exception) {
                respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u062A\u062D \u0627\u0644\u0628\u062D\u062B")
            }
        }
    }

    private fun navigateTo(place: String, mode: String = "driving") {
        if (place.isBlank()) {
            respond("\u0642\u0644\u064A \u0648\u064A\u0646 \u0628\u062F\u0643 \u062A\u0631\u0648\u062D")
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
                respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u062A\u062D \u0627\u0644\u062E\u0631\u0627\u0626\u0637")
            }
        }
    }

    private fun respondNavigation(place: String, mode: String) {
        if (mode == "walking") {
            respond("\u0647\u0627\u0643 \u0637\u0631\u064A\u0642 \u0627\u0644\u0645\u0634\u064A \u0627\u0644\u0649 $place")
        } else {
            respond("\u062C\u0627\u0631\u064A \u0641\u062A\u062D \u0627\u0644\u0637\u0631\u064A\u0642 \u0627\u0644\u0649 $place")
        }
    }

    // ---------------- Jokes ----------------

    private val jokes = listOf(
        "\u0648\u0627\u062D\u062F \u0633\u0623\u0644 \u0635\u0627\u062D\u0628\u0648: \u0639\u0644\u0627\u0634 \u0627\u0644\u062F\u064A\u0643 \u064A\u0635\u064A\u062D \u0627\u0644\u0635\u0628\u0627\u062D\u061F \u0642\u0627\u0644\u0647: \u0628\u0627\u0634 \u064A\u0641\u0648\u0642\u0643 \u0642\u0628\u0644 \u0645\u0627 \u062A\u0641\u0648\u062A\u0647 \u0628\u0627\u0644\u0646\u0648\u0645.",
        "\u0637\u0641\u0644 \u0633\u0623\u0644 \u0628\u0627\u0628\u0627\u0647: \u0628\u0627\u0628\u0627 \u0648\u064A\u0646 \u062A\u062D\u0628 \u062A\u0643\u0648\u0646 \u0644\u0645\u0627 \u062A\u0643\u0628\u0631\u061F \u0642\u0627\u0644\u0647: \u0647\u0627\u062F\u064A \u0647\u064A \u0627\u0644\u0645\u0634\u0643\u0644\u0629\u060C \u0623\u0646\u0627 \u0643\u0628\u0631\u062A \u0648\u0645\u0627 \u0632\u0644\u062A \u0645\u0627 \u0639\u0631\u0641\u062A\u0634.",
        "\u0648\u0627\u062D\u062F \u062F\u062E\u0644 \u064A\u0634\u062A\u0631\u064A \u0633\u0627\u0639\u0629\u060C \u0642\u0627\u0644\u0647 \u0627\u0644\u0628\u064A\u0627\u0639: \u0647\u0627\u064A \u0627\u0644\u0633\u0627\u0639\u0629 \u0628\u062A\u0639\u064A\u0634 \u0645\u0639\u0627\u0643 \u0644\u0644\u0623\u0628\u062F. \u0642\u0627\u0644\u0647: \u0637\u064A\u0628 \u0623\u0639\u0637\u064A\u0646\u064A \u0648\u062D\u062F\u0629 \u062A\u0639\u064A\u0634 \u0623\u0633\u0628\u0648\u0639 \u0628\u0633\u060C \u062E\u0627\u064A\u0641 \u0646\u0636\u064A\u0639\u0647\u0627.",
        "\u0639\u0644\u0627\u0634 \u0627\u0644\u0643\u0645\u0628\u064A\u0648\u062A\u0631 \u0645\u0627 \u0628\u064A\u062D\u0633 \u0628\u0627\u0644\u0628\u0631\u062F\u061F \u0644\u0623\u0646\u0647 \u0639\u0646\u062F\u0647 Windows \u0645\u0633\u0643\u0631\u0629 \u0632\u064A\u0646."
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
        if (notes.isEmpty()) return "\u0645\u0627 \u0639\u0646\u062F\u0643 \u0645\u0644\u0627\u062D\u0638\u0627\u062A \u0645\u062D\u0641\u0648\u0638\u0629"
        return "\u0645\u0644\u0627\u062D\u0638\u0627\u062A\u0643: " + notes.joinToString("\u060C ")
    }

    // ---------------- Natural response variety ----------------

    private val flashOnPhrases = listOf(
        "\u062F\u0627\u064A\u0631\u0644\u0643 \u0627\u0644\u0641\u0644\u0627\u0634", "\u062A\u0645\u0627\u0645\u060C \u0648\u0644\u0651\u0649 \u0627\u0644\u0641\u0644\u0627\u0634 \u0634\u0627\u0639\u0644", "\u0647\u0627\u0643 \u0627\u0644\u0641\u0644\u0627\u0634 \u0634\u0627\u0639\u0644"
    )
    private val flashOffPhrases = listOf(
        "\u0637\u0641\u064A\u062A \u0627\u0644\u0641\u0644\u0627\u0634", "\u062A\u0645\u0627\u0645\u060C \u0627\u0644\u0641\u0644\u0627\u0634 \u0637\u0627\u0641\u064A \u0647\u0644\u0642", "\u062E\u0644\u0627\u0635 \u0637\u0641\u0627\u0647"
    )
    private val musicOnPhrases = listOf(
        "\u0647\u0627\u0643\u0647\u0627 \u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649 \u0628\u062F\u0627\u062A", "\u062A\u0645\u0627\u0645\u060C \u0646\u062F\u064A\u0631\u0644\u0643 \u0645\u0648\u0633\u064A\u0642\u0649", "\u0627\u0633\u062A\u0645\u062A\u0639 \u0628\u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649"
    )
    private val musicOffPhrases = listOf(
        "\u0648\u0642\u0641\u062A \u0627\u0644\u0645\u0648\u0633\u064A\u0642\u0649", "\u062A\u0645\u0627\u0645\u060C \u0633\u0643\u062A\u0647\u0627"
    )

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            respond("\u062C\u0627\u0631\u064A \u0641\u062A\u062D $appName")
        } else {
            respond("$appName \u0645\u0634 \u0645\u062B\u0628\u062A \u0639\u0644\u0649 \u062C\u0647\u0627\u0632\u0643")
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
            respond("\u0642\u0644\u064A \u0645\u064A\u0646 \u0628\u062F\u0643 \u0623\u062A\u0635\u0644 \u0641\u064A\u0647")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            respond("\u0628\u062F\u064A \u0625\u0630\u0646 \u0642\u0631\u0627\u0621\u0629 \u062C\u0647\u0627\u062A \u0627\u0644\u0627\u062A\u0635\u0627\u0644 \u0623\u0648\u0644\u060C \u062C\u0631\u0628 \u0645\u0631\u0629 \u062A\u0627\u0646\u064A\u0629")
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
                        respond("\u062C\u0627\u0631\u064A \u0627\u0644\u0627\u062A\u0635\u0627\u0644 \u0628\u0640 $name")
                    } else {
                        respond("\u0645\u0627 \u0644\u0642\u064A\u062A \u0631\u0642\u0645 \u0647\u0627\u062A\u0641 \u0644\u0640 $name")
                    }
                }
            } else {
                respond("\u0645\u0627 \u0644\u0642\u064A\u062A \u062C\u0647\u0629 \u0627\u062A\u0635\u0627\u0644 \u0628\u0627\u0633\u0645 $name")
            }
        }
    }

    private fun writeCode(topic: String) {
        if (topic.isBlank()) {
            respond("\u0642\u0644\u064A \u0634\u0648 \u0627\u0644\u0643\u0648\u062F \u064A\u0644\u064A \u0628\u062F\u0643 \u0627\u064A\u0627\u0647")
            return
        }
        if (GEMINI_API_KEY.isBlank()) {
            respond("\u0644\u0627\u0632\u0645 \u062A\u062D\u0637 \u0645\u0641\u062A\u0627\u062D Gemini \u0627\u0644\u0623\u0648\u0644 \u0639\u0634\u0627\u0646 \u0623\u0642\u062F\u0631 \u0623\u0643\u062A\u0628\u0644\u0643 \u0643\u0648\u062F")
            return
        }
        respond("\u062E\u0644\u064A\u0646\u064A \u0623\u0643\u062A\u0628\u0644\u0643 \u0627\u0644\u0643\u0648\u062F...")
        val prompt = "\u0627\u0643\u062A\u0628 \u0643\u0648\u062F \u0628\u0631\u0645\u062C\u064A \u0648\u0627\u0636\u062D \u0648\u0645\u0631\u062A\u0628 \u0644\u0640: $topic. \u0627\u0634\u0631\u062D \u0628\u062C\u0645\u0644\u0629 \u0642\u0635\u064A\u0631\u0629 \u0634\u0648 \u0628\u064A\u0633\u0648\u064A \u0627\u0644\u0643\u0648\u062F."
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
                runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0648\u0635\u0644 \u0644\u0644\u0646\u062A") }
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
                        log("\u062C\u0627\u0631\u0641\u0633:\n${code.trim()}")
                        tts.speak("\u0643\u062A\u0628\u062A\u0644\u0643 \u0627\u0644\u0643\u0648\u062F \u0628\u0627\u0644\u0634\u0627\u0634\u0629\u060C \u0634\u0648\u0641\u0647", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                } catch (e: Exception) {
                    runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0641\u0647\u0645 \u0631\u062F Gemini") }
                }
            }
        })
    }

    // ---------------- Holographic-style product design ----------------

    private fun designHologram(description: String) {
        if (description.isBlank()) {
            respond("\u0642\u0644\u064A \u0648\u0635\u0641 \u0627\u0644\u0645\u0646\u062A\u062C \u064A\u0644\u064A \u0628\u062F\u0643 \u062A\u0635\u0645\u0645\u0647")
            return
        }
        if (GEMINI_API_KEY.isBlank()) {
            respond("\u0644\u0627\u0632\u0645 \u062A\u062D\u0637 \u0645\u0641\u062A\u0627\u062D Gemini \u0627\u0644\u0623\u0648\u0644 \u0639\u0634\u0627\u0646 \u0623\u0642\u062F\u0631 \u0623\u0635\u0645\u0645\u0644\u0643")
            return
        }
        respond("\u0628\u0635\u0645\u0645\u0644\u0643...")
        val prompt = "\u0628\u0646\u0627\u0621\u064B \u0639\u0644\u0649 \u0647\u0627\u062F \u0627\u0644\u0648\u0635\u0641: \"$description\"\u060C \u0627\u0643\u062A\u0628\u0644\u064A \u0645\u0648\u0627\u0635\u0641\u0627\u062A \u062A\u0635\u0645\u064A\u0645 \u0645\u0646\u0638\u0645\u0629 \u0648\u0642\u0635\u064A\u0631\u0629 " +
                "(\u0627\u0633\u0645 \u0627\u0644\u0645\u0646\u062A\u062C\u060C \u0627\u0644\u0642\u064A\u0627\u0633\u0627\u062A \u0644\u0648 \u0645\u0648\u062C\u0648\u062F\u0629\u060C \u0627\u0644\u0645\u0648\u0627\u062F \u0627\u0644\u0645\u0642\u062A\u0631\u062D\u0629\u060C \u0648\u0635\u0641 \u0645\u062E\u062A\u0635\u0631 \u0628\u062C\u0645\u0644\u062A\u064A\u0646)\u060C " +
                "\u0628\u0634\u0643\u0644 \u0646\u0642\u0627\u0637 \u0642\u0635\u064A\u0631\u0629 \u062A\u0635\u0644\u062D \u062A\u0646\u0639\u0631\u0636 \u0628\u0634\u0627\u0634\u0629 \u0647\u0648\u0644\u0648\u062C\u0631\u0627\u0645\u064A\u0629"
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
                runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u0648\u0635\u0644 \u0644\u0644\u0646\u062A") }
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
                        respond("\u0647\u0627\u0643 \u0627\u0644\u062A\u0635\u0645\u064A\u0645")
                    }
                } catch (e: Exception) {
                    runOnUiThread { respond("\u0645\u0627 \u0642\u062F\u0631\u062A \u0623\u062C\u0647\u0632 \u0627\u0644\u062A\u0635\u0645\u064A\u0645") }
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
            text = "\u2715 \u0625\u063A\u0644\u0627\u0642"
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
            cmd.contains("\u0627\u0634\u0631\u062D\u0644\u064A") -> "\u0627\u0634\u0631\u062D\u0644\u064A"
            cmd.contains("\u0627\u0634\u0631\u062D \u0644\u064A") -> "\u0627\u0634\u0631\u062D \u0644\u064A"
            cmd.contains("\u0641\u0647\u0645\u0646\u064A") -> "\u0641\u0647\u0645\u0646\u064A"
            cmd.contains("\u0627\u0641\u062A\u062D \u0645\u0648\u0636\u0648\u0639") -> "\u0645\u0648\u0636\u0648\u0639"
            cmd.contains("\u0634\u0648 \u0647\u0648") -> "\u0634\u0648 \u0647\u0648"
            else -> "\u0634\u0648 \u0647\u064A"
        }
        return extractNameAfter(cmd, marker)
    }

    private val offlineKnowledge = mapOf(
        "\u0627\u0644\u0627\u0633\u0644\u0627\u0645" to "\u0627\u0644\u0625\u0633\u0644\u0627\u0645 \u062F\u064A\u0646 \u062A\u0648\u062D\u064A\u062F\u064A\u060C \u0646\u0632\u0644 \u0639\u0644\u0649 \u0627\u0644\u0646\u0628\u064A \u0645\u062D\u0645\u062F \u0635\u0644\u0649 \u0627\u0644\u0644\u0647 \u0639\u0644\u064A\u0647 \u0648\u0633\u0644\u0645 \u0628\u0627\u0644\u0642\u0631\u0622\u0646 \u0627\u0644\u0643\u0631\u064A\u0645. \u0645\u0646 \u0623\u0631\u0643\u0627\u0646\u0647 \u0627\u0644\u062E\u0645\u0633\u0629: \u0627\u0644\u0634\u0647\u0627\u062F\u062A\u064A\u0646\u060C \u0627\u0644\u0635\u0644\u0627\u0629\u060C \u0627\u0644\u0632\u0643\u0627\u0629\u060C \u0627\u0644\u0635\u064A\u0627\u0645 \u0628\u0631\u0645\u0636\u0627\u0646\u060C \u0648\u0627\u0644\u062D\u062C \u0644\u0645\u0646 \u0627\u0633\u062A\u0637\u0627\u0639. \u064A\u0624\u0645\u0646 \u0627\u0644\u0645\u0633\u0644\u0645\u0648\u0646 \u0628\u0627\u0644\u0644\u0647 \u0627\u0644\u0648\u0627\u062D\u062F\u060C \u0648\u0628\u0627\u0644\u0623\u0646\u0628\u064A\u0627\u0621 \u0648\u0627\u0644\u0631\u0633\u0644 \u0645\u0646 \u0642\u0628\u0644 \u0645\u062D\u0645\u062F \u0645\u062A\u0644 \u0645\u0648\u0633\u0649 \u0648\u0639\u064A\u0633\u0649 \u0639\u0644\u064A\u0647\u0645 \u0627\u0644\u0633\u0644\u0627\u0645.",
        "\u0627\u0644\u0645\u0633\u064A\u062D\u064A\u0629" to "\u0627\u0644\u0645\u0633\u064A\u062D\u064A\u0629 \u062F\u064A\u0646 \u062A\u0648\u062D\u064A\u062F\u064A \u064A\u0642\u0648\u0645 \u0639\u0644\u0649 \u062A\u0639\u0627\u0644\u064A\u0645 \u0627\u0644\u0633\u064A\u062F \u0627\u0644\u0645\u0633\u064A\u062D \u0639\u064A\u0633\u0649 \u0628\u0646 \u0645\u0631\u064A\u0645 \u0643\u0645\u0627 \u0648\u0631\u062F\u062A \u0628\u0627\u0644\u0625\u0646\u062C\u064A\u0644. \u0645\u0646 \u0623\u0647\u0645 \u0645\u0639\u062A\u0642\u062F\u0627\u062A\u0647\u0627 \u0641\u0643\u0631\u0629 \u0627\u0644\u062B\u0627\u0644\u0648\u062B \u0627\u0644\u0623\u0642\u062F\u0633 (\u0627\u0644\u0622\u0628 \u0648\u0627\u0644\u0627\u0628\u0646 \u0648\u0627\u0644\u0631\u0648\u062D \u0627\u0644\u0642\u062F\u0633)\u060C \u0648\u0637\u0642\u0648\u0633\u0647\u0627 \u0627\u0644\u0623\u0633\u0627\u0633\u064A\u0629 \u062A\u0634\u0645\u0644 \u0627\u0644\u0645\u0639\u0645\u0648\u062F\u064A\u0629 \u0648\u0627\u0644\u0642\u0631\u0628\u0627\u0646 \u0627\u0644\u0645\u0642\u062F\u0633\u060C \u0648\u0641\u064A\u0647\u0627 \u0637\u0648\u0627\u0626\u0641 \u0643\u0628\u0631\u0649 \u0645\u062A\u0644 \u0627\u0644\u0643\u0627\u062B\u0648\u0644\u064A\u0643 \u0648\u0627\u0644\u0623\u0631\u062B\u0648\u0630\u0643\u0633 \u0648\u0627\u0644\u0628\u0631\u0648\u062A\u0633\u062A\u0627\u0646\u062A.",
        "\u0627\u0644\u064A\u0647\u0648\u062F\u064A\u0629" to "\u0627\u0644\u064A\u0647\u0648\u062F\u064A\u0629 \u0645\u0646 \u0623\u0642\u062F\u0645 \u0627\u0644\u062F\u064A\u0627\u0646\u0627\u062A \u0627\u0644\u062A\u0648\u062D\u064A\u062F\u064A\u0629\u060C \u0643\u062A\u0627\u0628\u0647\u0627 \u0627\u0644\u0645\u0642\u062F\u0633 \u0627\u0644\u062A\u0648\u0631\u0627\u0629 (\u0627\u0644\u0639\u0647\u062F \u0627\u0644\u0642\u062F\u064A\u0645). \u062A\u0624\u0645\u0646 \u0628\u0646\u0628\u0648\u0629 \u0645\u0648\u0633\u0649 \u0639\u0644\u064A\u0647 \u0627\u0644\u0633\u0644\u0627\u0645 \u0648\u0627\u0633\u062A\u0644\u0627\u0645\u0647 \u0627\u0644\u0648\u0635\u0627\u064A\u0627 \u0627\u0644\u0639\u0634\u0631\u060C \u0648\u0645\u0646 \u0634\u0639\u0627\u0626\u0631\u0647\u0627 \u0627\u0644\u0623\u0633\u0627\u0633\u064A\u0629 \u0627\u0644\u0633\u0628\u062A (\u064A\u0648\u0645 \u0627\u0644\u0631\u0627\u062D\u0629) \u0648\u0642\u0648\u0627\u0639\u062F \u0627\u0644\u0637\u0639\u0627\u0645 \u0627\u0644\u062D\u0644\u0627\u0644 \u062D\u0633\u0628 \u0627\u0644\u0634\u0631\u064A\u0639\u0629 \u0627\u0644\u064A\u0647\u0648\u062F\u064A\u0629 (\u0643\u0648\u0634\u064A\u0631).",
        "\u0627\u0644\u0628\u0648\u0630\u064A\u0629" to "\u0627\u0644\u0628\u0648\u0630\u064A\u0629 \u062F\u064A\u0627\u0646\u0629 \u0648\u0641\u0644\u0633\u0641\u0629 \u0631\u0648\u062D\u064A\u0629 \u0623\u0633\u0633\u0647\u0627 \u0633\u064A\u062F\u0647\u0627\u0631\u062A\u0627 \u063A\u0648\u062A\u0627\u0645\u0627 (\u0628\u0648\u0630\u0627) \u0628\u0627\u0644\u0647\u0646\u062F. \u062A\u0631\u0643\u0632 \u0639\u0644\u0649 \u062A\u062D\u0642\u064A\u0642 \u0627\u0644\u062A\u0646\u0648\u064A\u0631 \u0648\u0627\u0644\u062A\u062D\u0631\u0631 \u0645\u0646 \u0627\u0644\u0645\u0639\u0627\u0646\u0627\u0629 \u0639\u0646 \u0637\u0631\u064A\u0642 \u0627\u062A\u0628\u0627\u0639 \u0627\u0644\u0637\u0631\u064A\u0642 \u0627\u0644\u062B\u0645\u0627\u0646\u064A \u0627\u0644\u0646\u0628\u064A\u0644\u060C \u0648\u062A\u0624\u0645\u0646 \u0628\u0645\u0628\u062F\u0623 \u0625\u0639\u0627\u062F\u0629 \u0627\u0644\u062A\u062C\u0633\u062F (\u0627\u0644\u0643\u0627\u0631\u0645\u0627).",
        "\u0627\u0644\u0647\u0646\u062F\u0648\u0633\u064A\u0629" to "\u0627\u0644\u0647\u0646\u062F\u0648\u0633\u064A\u0629 \u0645\u0646 \u0623\u0642\u062F\u0645 \u0627\u0644\u062F\u064A\u0627\u0646\u0627\u062A \u0628\u0627\u0644\u0639\u0627\u0644\u0645\u060C \u0645\u062A\u0639\u062F\u062F\u0629 \u0627\u0644\u0622\u0644\u0647\u0629 \u0648\u0641\u064A\u0647\u0627 \u0641\u0644\u0633\u0641\u0627\u062A \u0645\u062A\u0646\u0648\u0639\u0629. \u062A\u0624\u0645\u0646 \u0628\u0645\u0628\u062F\u0623 \u0627\u0644\u0643\u0627\u0631\u0645\u0627 \u0648\u0625\u0639\u0627\u062F\u0629 \u0627\u0644\u062A\u062C\u0633\u062F (\u0627\u0644\u062A\u0646\u0627\u0633\u062E)\u060C \u0648\u0643\u062A\u0628\u0647\u0627 \u0627\u0644\u0645\u0642\u062F\u0633\u0629 \u062A\u0634\u0645\u0644 \u0627\u0644\u0641\u064A\u062F\u0627 \u0648\u0627\u0644\u0628\u0647\u0627\u063A\u0627\u0641\u0627\u062F\u063A\u064A\u062A\u0627\u060C \u0648\u0623\u0647\u0645 \u0622\u0644\u0647\u062A\u0647\u0627 \u0628\u0631\u0627\u0647\u0645\u0627 \u0648\u0641\u064A\u0634\u0646\u0648 \u0648\u0634\u064A\u0641\u0627."
    )

    private fun explainTopic(topic: String) {
        if (topic.isBlank()) {
            respond("\u0642\u0644\u064A \u0634\u0648 \u0627\u0644\u0645\u0648\u0636\u0648\u0639 \u064A\u0644\u064A \u0628\u062F\u0643 \u0623\u0634\u0631\u062D\u0644\u0643 \u064A\u0627\u0647")
            return
        }

        val offlineMatch = offlineKnowledge.entries.firstOrNull { topic.contains(it.key) }
        if (offlineMatch != null) {
            respond(offlineMatch.value)
            return
        }

        if (GEMINI_API_KEY.isBlank()) {
            respond("\u0644\u0627\u0632\u0645 \u062A\u062D\u0637 \u0645\u0641\u062A\u0627\u062D Gemini \u0627\u0644\u0623\u0648\u0644 \u0639\u0634\u0627\u0646 \u0623\u0642\u062F\u0631 \u0623\u0634\u0631\u062D\u0644\u0643 \u0645\u0648\u0627\u0636\u064A\u0639 \u0632\u064A\u0627\u062F\u0629")
            return
        }
        respond("\u062E\u0644\u064A\u0646\u064A \u0623\u0634\u0631\u062D\u0644\u0643...")
        val prompt = "\u0627\u0634\u0631\u062D\u0644\u064A \u0645\u0648\u0636\u0648\u0639 \"$topic\" \u0628\u0637\u0631\u064A\u0642\u0629 \u0633\u0647\u0644\u0629 \u0648\u0645\u0628\u0633\u0637\u0629 \u0645\u0639 \u0645\u062B\u0627\u0644 \u0625\u0630\u0627 \u0623\u0645\u0643\u0646\u060C \u0628\u0623\u0633\u0644\u0648\u0628 \u0642\u0631\u064A\u0628 \u0648\u0645\u0641\u0647\u0648\u0645"
        askGemini(prompt)
    }

    // ---------------- Unit converter ----------------

    private fun convertUnits(cmd: String): String {
        val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
        val match = numberRegex.find(cmd) ?: return "\u0642\u0644\u064A \u0627\u0644\u0631\u0642\u0645 \u064A\u0644\u064A \u0628\u062F\u0643 \u062A\u062D\u0648\u0644\u0647"
        val value = match.groupValues[1].toDouble()

        return when {
            cmd.contains("\u0643\u064A\u0644\u0648\u0645\u062A\u0631") && cmd.contains("\u0645\u064A\u0644") -> {
                val miles = value * 0.621371
                "${value} \u0643\u0645 \u064A\u0633\u0627\u0648\u064A \u062A\u0642\u0631\u064A\u0628\u064B\u0627 ${"%.2f".format(miles)} \u0645\u064A\u0644"
            }
            cmd.contains("\u0643\u064A\u0644\u0648") && cmd.contains("\u0628\u0627\u0648\u0646\u062F") -> {
                val pounds = value * 2.20462
                "${value} \u0643\u064A\u0644\u0648 \u064A\u0633\u0627\u0648\u064A \u062A\u0642\u0631\u064A\u0628\u064B\u0627 ${"%.2f".format(pounds)} \u0628\u0627\u0648\u0646\u062F"
            }
            cmd.contains("\u0645\u0626\u0648\u064A\u0629") && cmd.contains("\u0641\u0647\u0631\u0646\u0647\u0627\u064A\u062A") -> {
                val fahrenheit = (value * 9 / 5) + 32
                "${value} \u062F\u0631\u062C\u0629 \u0645\u0626\u0648\u064A\u0629 \u064A\u0633\u0627\u0648\u064A ${"%.1f".format(fahrenheit)} \u0641\u0647\u0631\u0646\u0647\u0627\u064A\u062A"
            }
            else -> "\u0642\u0644\u064A \u0627\u0644\u062A\u062D\u0648\u064A\u0644 \u0628\u0647\u0627\u0644\u0635\u064A\u063A\u0629: \u062D\u0648\u0644 10 \u0643\u064A\u0644\u0648\u0645\u062A\u0631 \u0627\u0644\u0649 \u0645\u064A\u0644"
        }
    }

    // ---------------- Fun facts ----------------

    private val funFacts = listOf(
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u0635\u062D\u0631\u0627\u0621 \u0627\u0644\u062C\u0632\u0627\u0626\u0631 (\u0627\u0644\u0635\u062D\u0631\u0627\u0621 \u0627\u0644\u0643\u0628\u0631\u0649) \u062A\u063A\u0637\u064A \u0623\u0643\u062A\u0631 \u0645\u0646 80% \u0645\u0646 \u0645\u0633\u0627\u062D\u0629 \u0627\u0644\u0628\u0644\u0627\u062F.",
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u0627\u0644\u0639\u0633\u0644 \u0645\u0627 \u064A\u0641\u0633\u062F\u0634 \u0623\u0628\u062F\u064B\u0627\u060C \u062D\u062A\u0649 \u0628\u0639\u062F \u0622\u0644\u0627\u0641 \u0627\u0644\u0633\u0646\u064A\u0646.",
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u0627\u0644\u0642\u0644\u0628 \u0627\u0644\u0628\u0634\u0631\u064A \u064A\u062F\u0642 \u062D\u0648\u0627\u0644\u064A 100 \u0623\u0644\u0641 \u0645\u0631\u0629 \u0628\u0627\u0644\u064A\u0648\u0645 \u0627\u0644\u0648\u0627\u062D\u062F.",
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u0627\u0644\u0623\u062E\u0637\u0628\u0648\u0637 \u0639\u0646\u062F\u0647 \u062B\u0644\u0627\u062B\u0629 \u0642\u0644\u0648\u0628 \u0648\u062F\u0645\u0647 \u0644\u0648\u0646\u0647 \u0623\u0632\u0631\u0642.",
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u0627\u0644\u0636\u0648\u0621 \u0645\u0646 \u0627\u0644\u0634\u0645\u0633 \u064A\u0648\u0635\u0644 \u0644\u0644\u0623\u0631\u0636 \u0628\u062D\u0648\u0627\u0644\u064A 8 \u062F\u0642\u0627\u064A\u0642 \u0628\u0633.",
        "\u0647\u0644 \u062A\u0639\u0631\u0641\u061F \u062C\u0628\u0644 \u0637\u0648\u0628\u0642\u0627\u0644 \u0628\u0627\u0644\u0645\u063A\u0631\u0628 \u0647\u0648 \u0623\u0639\u0644\u0649 \u0642\u0645\u0629 \u0628\u0634\u0645\u0627\u0644 \u0623\u0641\u0631\u064A\u0642\u064A\u0627."
    )

    // ---------------- Suggestions ----------------

    private fun suggestDrawing(): String {
        val ideas = listOf(
            "\u0627\u0631\u0633\u0645 \u0645\u0646\u0638\u0631 \u0637\u0628\u064A\u0639\u064A \u0641\u064A\u0647 \u062C\u0628\u0627\u0644 \u0648\u0628\u062D\u0631",
            "\u062C\u0631\u0628 \u062A\u0631\u0633\u0645 \u0628\u0648\u0631\u062A\u0631\u064A\u0647 \u0644\u0634\u062E\u0635 \u0642\u0631\u064A\u0628 \u0645\u0646\u0643",
            "\u0627\u0631\u0633\u0645 \u062D\u064A\u0648\u0627\u0646 \u0623\u0644\u064A\u0641 \u0628\u0623\u0633\u0644\u0648\u0628 \u0643\u0631\u062A\u0648\u0646\u064A",
            "\u062C\u0631\u0628 \u0631\u0633\u0645 \u0645\u062F\u064A\u0646\u0629 \u062E\u064A\u0627\u0644\u064A\u0629 \u0645\u0646 \u062E\u064A\u0627\u0644\u0643",
            "\u0627\u0631\u0633\u0645 \u0644\u0648\u062D\u0629 \u062A\u062C\u0631\u064A\u062F\u064A\u0629 \u0628\u0627\u0644\u0623\u0644\u0648\u0627\u0646 \u064A\u0644\u064A \u0628\u062A\u062D\u0628\u0647\u0627"
        )
        return "\u0641\u0643\u0631\u0629 \u0631\u0633\u0645\u0629 \u0627\u0644\u064A\u0648\u0645: ${ideas.random()}"
    }

    private fun suggestBreakfast(): String {
        val ideas = listOf(
            "\u0628\u064A\u0636 \u0645\u0639 \u0632\u0639\u062A\u0631 \u0648\u0632\u064A\u062A \u0632\u064A\u062A\u0648\u0646 \u0648\u062E\u0628\u0632 \u0637\u0627\u0632\u0629",
            "\u0641\u0648\u0644 \u0645\u062F\u0645\u0633 \u0645\u0639 \u062E\u0636\u0631\u0629 \u0648\u0644\u064A\u0645\u0648\u0646",
            "\u0644\u0628\u0646\u0629 \u0645\u0639 \u062E\u064A\u0627\u0631 \u0648\u0637\u0645\u0627\u0637\u0645",
            "\u0645\u0646\u0627\u0642\u064A\u0634 \u0632\u0639\u062A\u0631 \u0623\u0648 \u062C\u0628\u0646\u0629",
            "\u0634\u0643\u0634\u0648\u0643\u0629 \u0628\u0627\u0644\u0628\u064A\u0636 \u0648\u0627\u0644\u0628\u0646\u062F\u0648\u0631\u0629"
        )
        return "\u0627\u0642\u062A\u0631\u0627\u062D \u0641\u0637\u0648\u0631 \u0627\u0644\u064A\u0648\u0645: ${ideas.random()}"
    }

    // ---------------- Distance between cities ----------------

    private val cityCoordinates = mapOf(
        "\u062F\u0645\u0634\u0642" to Pair(33.5138, 36.2765),
        "\u062D\u0644\u0628" to Pair(36.2021, 37.1343),
        "\u062D\u0645\u0635" to Pair(34.7324, 36.7137),
        "\u062D\u0645\u0627\u0629" to Pair(35.1318, 36.7578),
        "\u0627\u0644\u0644\u0627\u0630\u0642\u064A\u0629" to Pair(35.5317, 35.7911),
        "\u0637\u0631\u0637\u0648\u0633" to Pair(34.8890, 35.8866),
        "\u0625\u062F\u0644\u0628" to Pair(35.9306, 36.6339),
        "\u062F\u0631\u0639\u0627" to Pair(32.6189, 36.1021),
        "\u0628\u064A\u0631\u0648\u062A" to Pair(33.8938, 35.5018),
        "\u0639\u0645\u0627\u0646" to Pair(31.9454, 35.9284),
        "\u0627\u0644\u0642\u062F\u0633" to Pair(31.7683, 35.2137),
        "\u0627\u0644\u0642\u0627\u0647\u0631\u0629" to Pair(30.0444, 31.2357),
        "\u0628\u063A\u062F\u0627\u062F" to Pair(33.3152, 44.3661),
        "\u0627\u0644\u0631\u064A\u0627\u0636" to Pair(24.7136, 46.6753),
        "\u0627\u0633\u0637\u0646\u0628\u0648\u0644" to Pair(41.0082, 28.9784),
        "\u0628\u0627\u0631\u064A\u0633" to Pair(48.8566, 2.3522),
        "\u0644\u0646\u062F\u0646" to Pair(51.5074, -0.1278),
        "\u0627\u0644\u062C\u0632\u0627\u0626\u0631" to Pair(36.7538, 3.0588),
        "\u062A\u0648\u0646\u0633" to Pair(36.8065, 10.1815),
        "\u0627\u0644\u0631\u0628\u0627\u0637" to Pair(34.0209, -6.8416),
        "\u0627\u0644\u062F\u0627\u0631 \u0627\u0644\u0628\u064A\u0636\u0627\u0621" to Pair(33.5731, -7.5898),
        "\u0637\u0631\u0627\u0628\u0644\u0633" to Pair(32.8872, 13.1913),
        // \u0648\u0644\u0627\u064A\u0627\u062A \u0627\u0644\u062C\u0632\u0627\u0626\u0631 (58 \u0648\u0644\u0627\u064A\u0629)
        "\u0623\u062F\u0631\u0627\u0631" to Pair(27.8702, -0.2911),
        "\u0627\u0644\u0634\u0644\u0641" to Pair(36.1650, 1.3350),
        "\u0627\u0644\u0623\u063A\u0648\u0627\u0637" to Pair(33.8000, 2.8650),
        "\u0623\u0645 \u0627\u0644\u0628\u0648\u0627\u0642\u064A" to Pair(35.8770, 7.1170),
        "\u0628\u0627\u062A\u0646\u0629" to Pair(35.5560, 6.1740),
        "\u0628\u062C\u0627\u064A\u0629" to Pair(36.7530, 5.0840),
        "\u0628\u0633\u0643\u0631\u0629" to Pair(34.8500, 5.7280),
        "\u0628\u0634\u0627\u0631" to Pair(31.6150, -2.2180),
        "\u0627\u0644\u0628\u0644\u064A\u062F\u0629" to Pair(36.4700, 2.8280),
        "\u0627\u0644\u0628\u0648\u064A\u0631\u0629" to Pair(36.3730, 3.9020),
        "\u062A\u0645\u0646\u0631\u0627\u0633\u062A" to Pair(22.7850, 5.5220),
        "\u062A\u0628\u0633\u0629" to Pair(35.4040, 8.1240),
        "\u062A\u0644\u0645\u0633\u0627\u0646" to Pair(34.8780, -1.3150),
        "\u062A\u064A\u0627\u0631\u062A" to Pair(35.3710, 1.3170),
        "\u062A\u064A\u0632\u064A \u0648\u0632\u0648" to Pair(36.7120, 4.0450),
        "\u0627\u0644\u062C\u0644\u0641\u0629" to Pair(34.6730, 3.2630),
        "\u062C\u064A\u062C\u0644" to Pair(36.8220, 5.7660),
        "\u0633\u0637\u064A\u0641" to Pair(36.1910, 5.4080),
        "\u0633\u0639\u064A\u062F\u0629" to Pair(34.8300, 0.1510),
        "\u0633\u0643\u064A\u0643\u062F\u0629" to Pair(36.8760, 6.9090),
        "\u0633\u064A\u062F\u064A \u0628\u0644\u0639\u0628\u0627\u0633" to Pair(35.1900, -0.6300),
        "\u0639\u0646\u0627\u0628\u0629" to Pair(36.9000, 7.7670),
        "\u0642\u0627\u0644\u0645\u0629" to Pair(36.4620, 7.4270),
        "\u0642\u0633\u0646\u0637\u064A\u0646\u0629" to Pair(36.3650, 6.6150),
        "\u0627\u0644\u0645\u062F\u064A\u0629" to Pair(36.2640, 2.7540),
        "\u0645\u0633\u062A\u063A\u0627\u0646\u0645" to Pair(35.9350, 0.0890),
        "\u0627\u0644\u0645\u0633\u064A\u0644\u0629" to Pair(35.7050, 4.5410),
        "\u0645\u0639\u0633\u0643\u0631" to Pair(35.3970, 0.1400),
        "\u0648\u0631\u0642\u0644\u0629" to Pair(31.9490, 5.3250),
        "\u0648\u0647\u0631\u0627\u0646" to Pair(35.6970, -0.6330),
        "\u0627\u0644\u0628\u064A\u0636" to Pair(33.6860, 1.0190),
        "\u0625\u0644\u064A\u0632\u064A" to Pair(26.4830, 8.4670),
        "\u0628\u0631\u062C \u0628\u0648\u0639\u0631\u064A\u0631\u064A\u062C" to Pair(36.0730, 4.7610),
        "\u0628\u0648\u0645\u0631\u062F\u0627\u0633" to Pair(36.7660, 3.4770),
        "\u0627\u0644\u0637\u0627\u0631\u0641" to Pair(36.7670, 8.3130),
        "\u062A\u0646\u062F\u0648\u0641" to Pair(27.6710, -8.1470),
        "\u062A\u064A\u0633\u0645\u0633\u064A\u0644\u062A" to Pair(35.6070, 1.8110),
        "\u0627\u0644\u0648\u0627\u062F\u064A" to Pair(33.3680, 6.8670),
        "\u062E\u0646\u0634\u0644\u0629" to Pair(35.4360, 7.1430),
        "\u0633\u0648\u0642 \u0623\u0647\u0631\u0627\u0633" to Pair(36.2860, 7.9510),
        "\u062A\u064A\u0628\u0627\u0632\u0629" to Pair(36.5890, 2.4480),
        "\u0645\u064A\u0644\u0629" to Pair(36.4500, 6.2640),
        "\u0639\u064A\u0646 \u0627\u0644\u062F\u0641\u0644\u0649" to Pair(36.2640, 1.9660),
        "\u0627\u0644\u0646\u0639\u0627\u0645\u0629" to Pair(33.2660, -0.3170),
        "\u0639\u064A\u0646 \u062A\u0645\u0648\u0634\u0646\u062A" to Pair(35.2980, -1.1400),
        "\u063A\u0631\u062F\u0627\u064A\u0629" to Pair(32.4910, 3.6730),
        "\u063A\u0644\u064A\u0632\u0627\u0646" to Pair(35.7370, 0.5560),
        "\u062A\u064A\u0645\u064A\u0645\u0648\u0646" to Pair(29.2630, 0.2310),
        "\u0628\u0631\u062C \u0628\u0627\u062C\u064A \u0645\u062E\u062A\u0627\u0631" to Pair(21.3280, 0.9560),
        "\u0623\u0648\u0644\u0627\u062F \u062C\u0644\u0627\u0644" to Pair(34.4120, 5.0680),
        "\u0628\u0646\u064A \u0639\u0628\u0627\u0633" to Pair(30.1300, -2.1640),
        "\u0639\u064A\u0646 \u0635\u0627\u0644\u062D" to Pair(27.1940, 2.4780),
        "\u0639\u064A\u0646 \u0642\u0632\u0627\u0645" to Pair(19.5730, 5.7710),
        "\u062A\u0642\u0631\u062A" to Pair(33.1060, 6.0580),
        "\u062C\u0627\u0646\u062A" to Pair(24.5540, 9.4830),
        "\u0627\u0644\u0645\u063A\u064A\u0631" to Pair(33.9450, 5.9270),
        "\u0627\u0644\u0645\u0646\u064A\u0639\u0629" to Pair(30.5790, 2.8820)
    )

    private fun handleDistanceQuery(cmd: String) {
        val regex = Regex("""\u0645\u0646\s+(\S+)\s+(?:\u0627\u0644\u0649|\u0625\u0644\u0649)\s+(\S+)""")
        val match = regex.find(cmd)
        if (match == null) {
            respond("\u0642\u0644\u064A \u0627\u0644\u0645\u0633\u0627\u0641\u0629 \u0628\u0647\u0627\u0644\u0635\u064A\u063A\u0629: \u0643\u0645 \u0627\u0644\u0645\u0633\u0627\u0641\u0629 \u0645\u0646 \u062F\u0645\u0634\u0642 \u0627\u0644\u0649 \u062D\u0644\u0628")
            return
        }
        val cityA = match.groupValues[1]
        val cityB = match.groupValues[2]

        if (GOOGLE_MAPS_API_KEY.isNotBlank()) {
            respond("\u0628\u062D\u0633\u0628...")
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
                        respond("\u0627\u0644\u0645\u0633\u0627\u0641\u0629 \u0645\u0646 $cityA \u0627\u0644\u0649 $cityB \u062D\u0648\u0627\u0644\u064A $distanceText \u0628\u0627\u0644\u0633\u064A\u0627\u0631\u0629\u060C \u0648\u0648\u0642\u062A \u0627\u0644\u0631\u062D\u0644\u0629 \u062A\u0642\u0631\u064A\u0628\u064B\u0627 $durationText")
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
            return "\u0644\u0644\u0623\u0633\u0641 \u0645\u0627 \u0639\u0646\u062F\u064A \u0625\u062D\u062F\u0627\u062B\u064A\u0627\u062A \u0644\u0647\u0627\u064A \u0627\u0644\u0645\u062F\u064A\u0646\u0629 \u062D\u0627\u0644\u064A\u064B\u0627"
        }
        val distanceKm = haversine(coordA.first, coordA.second, coordB.first, coordB.second)
        return "\u0627\u0644\u0645\u0633\u0627\u0641\u0629 \u0645\u0646 $cityA \u0627\u0644\u0649 $cityB \u062D\u0648\u0627\u0644\u064A ${distanceKm.toInt()} \u0643\u0645 (\u062E\u0637 \u0645\u0633\u062A\u0642\u064A\u0645 \u062A\u0642\u0631\u064A\u0628\u064A)"
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
        log("\u062C\u0627\u0631\u0641\u0633: $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun log(text: String) {
        logText.append("\n\n$t
