package id.absenku.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class AnnouncementService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_SPEAK = "id.absenku.action.SPEAK"
        const val EXTRA_TEXT = "text"
        const val EXTRA_LANG = "lang"
        const val EXTRA_RATE = "rate"
        const val EXTRA_PITCH = "pitch"
        private const val NOTIFICATION_ID = 6402
    }
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var rate = 1.0f
    private var pitch = 1.0f
    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Pengumuman AbsenKU sedang diputar")
        startForeground(NOTIFICATION_ID, notification)
        pendingText = intent?.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: "Pengingat jadwal AbsenKU."
        rate = intent?.getFloatExtra(EXTRA_RATE, 1.0f) ?: 1.0f
        pitch = intent?.getFloatExtra(EXTRA_PITCH, 1.0f) ?: 1.0f
        if (ready) playNotificationThenSpeak()
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale("id", "ID")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { stopSelf() }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { stopSelf() }
            })
            if (pendingText != null) playNotificationThenSpeak()
        } else stopSelf()
    }

    private fun playNotificationThenSpeak() {
        val text = pendingText ?: return
        pendingText = null
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AnnouncementService, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                setOnCompletionListener {
                    it.release(); player = null
                    android.os.Handler(mainLooper).postDelayed({
                        tts?.setSpeechRate(rate.coerceIn(0.7f, 1.3f))
                        tts?.setPitch(pitch.coerceIn(0.7f, 1.3f))
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "absenku-${System.currentTimeMillis()}")
                    }, 1500)
                }
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "absenku-fallback"); true }
                prepare(); start()
            }
        } catch (_: Exception) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "absenku-fallback")
        }
    }

    private fun buildNotification(message: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "absenku_reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AbsenKU")
            .setContentText(message)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        player?.release(); player = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
