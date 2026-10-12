package id.absenku.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class AnnouncementService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_SPEAK = "id.absenku.action.SPEAK"
        const val ACTION_MONITOR = "id.absenku.action.MONITOR"
        const val ACTION_STOP_MONITOR = "id.absenku.action.STOP_MONITOR"
        const val EXTRA_TEXT = "text"
        const val EXTRA_LANG = "lang"
        const val EXTRA_RATE = "rate"
        const val EXTRA_PITCH = "pitch"
        private const val NOTIFICATION_ID = 6402
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var rate = 0.9f
    private var pitch = 1.0f
    private var monitoring = false

    override fun onCreate() {
        super.onCreate()
        monitoring = getSharedPreferences("absenku", MODE_PRIVATE).getBoolean("monitoring", false)
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITOR -> {
                monitoring = false
                getSharedPreferences("absenku", MODE_PRIVATE).edit().putBoolean("monitoring", false).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MONITOR -> {
                monitoring = true
                getSharedPreferences("absenku", MODE_PRIVATE).edit().putBoolean("monitoring", true).apply()
                startForeground(NOTIFICATION_ID, buildNotification("Aktif · Menunggu pengumuman"))
            }
            ACTION_SPEAK -> {
                if (getSharedPreferences("absenku", MODE_PRIVATE).getBoolean("monitoring", false)) monitoring = true
                startForeground(NOTIFICATION_ID, buildNotification(if (monitoring) "Aktif · Menunggu pengumuman" else "Pengumuman AbsenKU sedang diputar"))
                pendingText = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: "Pengingat jadwal AbsenKU."
                rate = intent.getFloatExtra(EXTRA_RATE, 0.9f)
                pitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
                if (ready) speakPending()
            }
            else -> {
                monitoring = getSharedPreferences("absenku", MODE_PRIVATE).getBoolean("monitoring", false)
                startForeground(NOTIFICATION_ID, buildNotification(if (monitoring) "Aktif · Menunggu pengumuman" else "Menyiapkan pengumuman"))
            }
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale("id", "ID")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        if (monitoring) startForeground(NOTIFICATION_ID, buildNotification("Aktif · Menunggu pengumuman"))
                        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                    }
                }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                    if (!monitoring) stopSelf()
                }
            })
            if (pendingText != null) speakPending()
        } else if (!monitoring) stopSelf()
    }

    private fun speakPending() {
        val text = pendingText ?: return
        pendingText = null
        Handler(Looper.getMainLooper()).postDelayed({
            tts?.setSpeechRate(rate.coerceIn(0.7f, 1.3f))
            tts?.setPitch(pitch.coerceIn(0.7f, 1.3f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "absenku-${System.currentTimeMillis()}")
        }, 1200) // Notifikasi muncul dahulu; tanpa bunyi ting-tung.
    }

    private fun buildNotification(message: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "absenku_reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AbsenKU")
            .setContentText(message)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
