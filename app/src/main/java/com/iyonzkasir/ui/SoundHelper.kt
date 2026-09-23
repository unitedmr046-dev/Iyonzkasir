package com.iyonzkasir.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Bikin beep sound pakai ToneGenerator bawaan Android.
 * Nggak butuh file audio → hemat ukuran APK.
 */
object SoundHelper {

    /** Beep khas kasir: 2x pendek beruntun. */
    fun playSuccess() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                } catch (_: Exception) {}
                Handler(Looper.getMainLooper()).postDelayed({
                    try { tg.release() } catch (_: Exception) {}
                }, 250)
            }, 150)
        } catch (_: Exception) {}
    }

    /** Beep single pendek. */
    fun playBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 300)
        } catch (_: Exception) {}
    }

    /** Beep error (nada turun). */
    fun playError() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 400)
        } catch (_: Exception) {}
    }

    /** Beep klik tombol (sangat pendek). */
    fun playClick() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            Handler(Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 100)
        } catch (_: Exception) {}
    }
}
