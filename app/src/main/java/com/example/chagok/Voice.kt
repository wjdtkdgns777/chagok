package com.example.chagok

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

class Voice(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it < 0) tts.stop() }
        .build()

    private var ready = false

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish()
        override fun onStop() = finish()
        override fun onError(error: ErrorType) = finish()
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.setAudioAttributes(attributes)
                ready = true
            }

            override fun onFailure(error: ErrorType) {
                Log.w(TAG, "tts $error")
            }
        })
        tts.addEventListener(events)
    }

    fun speak(text: String) {
        if (!ready || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        tts.speak(text)
    }

    fun release() {
        tts.removeEventListener(events)
        tts.release()
    }

    private fun finish() {
        main.post { audio.abandonAudioFocusRequest(focus) }
    }

    private companion object {
        const val TAG = "Chagok"
    }
}
