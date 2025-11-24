package com.example.mininotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.ByteArrayOutputStream

// 从 SettingsViewModel 导入枚举类而不是重新定义
import com.example.mininotification.PopupPosition
import com.example.mininotification.PopupStyle

class MyNotificationListenerService : NotificationListenerService() {

    private val TAG = "MyNotificationListener"
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification Posted: ${sbn?.packageName}")

        if (sbn == null || sbn.packageName == packageName) return

        // 将最近通知的应用包名保存到 SharedPreferences
        val recentApps = sharedPreferences.getStringSet("recent_notification_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        recentApps.add(sbn.packageName)
        sharedPreferences.edit().putStringSet("recent_notification_packages", recentApps).apply()

        // 尝试通过 Application 实例更新 ViewModel (如果应用正在运行)
        val appContext = applicationContext as? MyApplication
        appContext?.settingsViewModel?.addRecentNotificationApp(sbn.packageName)

        if (shouldShowPopup(sbn)) {
            showPopupNotification(sbn)
        } else {
            Log.d(TAG, "Notification filtered out for package: ${sbn.packageName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification Removed: ${sbn?.packageName}")
    }

    private fun shouldShowPopup(sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName

        val selectedPackages = sharedPreferences.getStringSet("selected_packages", emptySet())
        if (selectedPackages?.contains(packageName) != true) {
            return false
        }

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val selectedHours = sharedPreferences.getStringSet("selected_hours", null)
            ?.map { it.toInt() }?.toSet() ?: (6..20).toSet()
        if (currentHour !in selectedHours) {
            return false
        }

        if (sbn.isOngoing) {
            return false
        }

        return true
    }

    private fun showPopupNotification(sbn: StatusBarNotification) {
        // 统一使用 OverlayService 来显示所有样式的弹窗通知
        showCustomOverlayNotification(sbn)
    }
    private fun getNotificationContent(sbn: StatusBarNotification): Pair<String?, String?> {
        val extras = sbn.notification.extras
        var title = extras.getString(Notification.EXTRA_TITLE)
        var text: CharSequence? = null

        // 1. Try EXTRA_TEXT_LINES (for messaging apps)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) {
            text = lines.joinToString("\n")
        }

        // 2. If no lines, try EXTRA_BIG_TEXT
        if (text.isNullOrEmpty()) {
            text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        }

        // 3. If still no text, try EXTRA_TEXT (the standard one)
        if (text.isNullOrEmpty()) {
            text = extras.getCharSequence(Notification.EXTRA_TEXT)
        }

        // 4. If still no text, try EXTRA_SUB_TEXT
        if (text.isNullOrEmpty()) {
            text = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        }

        // 5. As a last resort for text, use tickerText
        if (text.isNullOrEmpty()) {
            text = sbn.notification.tickerText
        }

        title = if(title.isNullOrEmpty()) "注意" else title
        text = if(text.isNullOrEmpty()) "收到一条非标消息" else text.toString()

        return Pair(title, text)
    }

    private fun showBannerNotification(sbn: StatusBarNotification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mini_notification_banner_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Banner Style Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val originalNotification = sbn.notification

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setSmallIcon(originalNotification.smallIcon)
            .setContentTitle(originalNotification.extras.getString(Notification.EXTRA_TITLE))
            .setContentText(originalNotification.extras.getString(Notification.EXTRA_TEXT))
            .setAutoCancel(true)

        val largeIcon: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            originalNotification.extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            originalNotification.extras.getParcelable(Notification.EXTRA_LARGE_ICON)
        }
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_HIGH)
        }

        builder.setContentIntent(originalNotification.contentIntent)

        val newNotificationId = (sbn.packageName + sbn.id).hashCode()
        notificationManager.notify(newNotificationId, builder.build())
    }

    private fun showCustomOverlayNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification

        Log.d(TAG, "Processing notification for package: ${sbn.packageName}")
        Log.d(TAG, "Notification contentIntent: ${notification.contentIntent}")
        Log.d(TAG, "Notification contentIntent creator: ${notification.contentIntent?.creatorPackage}")

        val (title, text) = getNotificationContent(sbn)

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("title", title)
            putExtra("text", text)
            putExtra("isOngoing", sbn.isOngoing)
            putExtra("packageName", sbn.packageName) // 添加包名

            // 直接传递PendingIntent
            putExtra("contentIntent", notification.contentIntent)

            val smallIcon = sbn.notification.smallIcon
            val iconBitmap: Bitmap? = smallIcon?.loadDrawable(this@MyNotificationListenerService)?.let { drawable ->
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }

            if (iconBitmap != null) {
                val stream = ByteArrayOutputStream()
                iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val iconByteArray = stream.toByteArray()
                putExtra("icon", iconByteArray)
            }

            putExtra("duration", sharedPreferences.getInt("notification_duration", 5))
            
            // 修复：确保正确获取和传递弹窗位置设置
            val positionName = sharedPreferences.getString("popup_position", "TOP")
            val styleName = sharedPreferences.getString("popup_style", "NARROW")

            // 确保即使 positionName 或 styleName 为空也能有默认值
            val position = if (positionName != null) {
                try {
                    PopupPosition.valueOf(positionName)
                } catch (e: IllegalArgumentException) {
                    PopupPosition.TOP
                }
            } else {
                PopupPosition.TOP
            }
            
            val style = if (styleName != null) {
                try {
                    PopupStyle.valueOf(styleName)
                } catch (e: IllegalArgumentException) {
                    PopupStyle.NARROW
                }
            } else {
                PopupStyle.NARROW
            }
            
            putExtra("position", position)
            putExtra("style", style)
            
            putExtra("backgroundColor", sharedPreferences.getInt("background_color", 0xFF333333.toInt()))
            putExtra("textColor", sharedPreferences.getInt("text_color", 0xFF000000.toInt()))
            putExtra("backgroundAlpha", sharedPreferences.getFloat("background_alpha", 0.5f))
            putExtra("textAlpha", sharedPreferences.getFloat("text_alpha", 1.0f))
            putExtra("fontSize", sharedPreferences.getInt("popup_font_size", 100))
            putExtra("scrollingSpeed", sharedPreferences.getInt("scrolling_speed", 10))
        }

        Log.d(TAG, "Starting OverlayService with contentIntent")
        startService(intent)
    }
}