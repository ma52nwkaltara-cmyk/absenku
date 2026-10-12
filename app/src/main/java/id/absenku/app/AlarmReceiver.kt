package id.absenku.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, AnnouncementService::class.java).apply {
            action = AnnouncementService.ACTION_SPEAK
            putExtra(AnnouncementService.EXTRA_TEXT, intent.getStringExtra(AnnouncementService.EXTRA_TEXT) ?: "Pengingat jadwal AbsenKU.")
            putExtra(AnnouncementService.EXTRA_LANG, "id-ID")
            putExtra(AnnouncementService.EXTRA_RATE, 1.0f)
            putExtra(AnnouncementService.EXTRA_PITCH, 1.0f)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, service) else context.startService(service)
        } catch (_: Exception) {
            // Android may block background playback on some device policies; notification channel still handles alerts.
        }
    }
}
