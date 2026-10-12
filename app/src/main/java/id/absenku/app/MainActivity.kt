package id.absenku.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val siteUrl = "https://absensi.rdmku.cloud/index.html"
    private val requestCode = 6021

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestAppPermissions()
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.allowFileAccess = false
        webView.webViewClient = object : WebViewClient() {}
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, true, false)
            }
        }
        webView.addJavascriptInterface(AbsenKuBridge(this), "AbsenKuNative")
        setContentView(webView)
        webView.loadUrl(siteUrl)
    }

    private fun requestAppPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_COARSE_LOCATION
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), requestCode)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel("absenku_reminders", "Pengingat AbsenKU", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pengingat jadwal pelajaran dan pengumuman AbsenKU"
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

class AbsenKuBridge(private val context: Context) {
    @JavascriptInterface fun speak(text: String, lang: String = "id-ID", rate: Float = 1.0f, pitch: Float = 1.0f) {
        val intent = Intent(context, AnnouncementService::class.java).apply {
            action = AnnouncementService.ACTION_SPEAK
            putExtra(AnnouncementService.EXTRA_TEXT, text)
            putExtra(AnnouncementService.EXTRA_LANG, lang)
            putExtra(AnnouncementService.EXTRA_RATE, rate)
            putExtra(AnnouncementService.EXTRA_PITCH, pitch)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    /** Called from page JavaScript: scheduleLesson(id, startEpochMillis, title, teacher). */
    @JavascriptInterface fun scheduleLesson(id: String, startEpochMillis: Long, title: String, teacher: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleOne(alarms, id.hashCode(), startEpochMillis - 5 * 60 * 1000L, "$title akan dimulai lima menit lagi.", teacher)
        scheduleOne(alarms, id.hashCode() + 100000, startEpochMillis, "$title telah dimulai.", teacher)
    }

    @JavascriptInterface fun cancelLesson(id: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(id.hashCode(), id.hashCode() + 100000).forEach { requestId ->
            val intent = Intent(context, AlarmReceiver::class.java).setAction("id.absenku.ALARM.$requestId")
            val pi = PendingIntent.getBroadcast(context, requestId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarms.cancel(pi)
        }
    }

    private fun scheduleOne(manager: AlarmManager, requestId: Int, time: Long, text: String, teacher: String) {
        if (time <= System.currentTimeMillis()) return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "id.absenku.ALARM.$requestId"
            putExtra(AnnouncementService.EXTRA_TEXT, "$text Pengajar: $teacher.")
        }
        val pi = PendingIntent.getBroadcast(context, requestId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else manager.setExact(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }
}
