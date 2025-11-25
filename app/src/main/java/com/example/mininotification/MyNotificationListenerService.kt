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
import android.os.Bundle
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
        Logger.log(this, TAG, "服务已创建")
        Log.d(TAG, "onCreate: 服务已创建")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Logger.log(this, TAG, "监听器已连接")
        Log.d(TAG, "onListenerConnected: 监听器已连接")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Logger.log(this, TAG, "监听器已断开")
        Log.d(TAG, "onListenerDisconnected: 监听器已断开")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.log(this, TAG, "服务已销毁")
        Log.d(TAG, "onDestroy: 服务已销毁")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Logger.log(this, TAG, "收到新通知：${sbn?.packageName}")

        if (sbn == null || sbn.packageName == packageName) return

        // 将最近通知的应用包名保存到 SharedPreferences
        val recentApps = sharedPreferences.getStringSet("recent_notification_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        recentApps.add(sbn.packageName)
        sharedPreferences.edit().putStringSet("recent_notification_packages", recentApps).apply()

        // 尝试通过 Application 实例更新 ViewModel (如果应用正在运行)
        val appContext = applicationContext as? MyApplication
        appContext?.settingsViewModel?.addRecentNotificationApp(sbn.packageName)

        if (shouldShowPopup(sbn)) {
            Logger.log(this, TAG, "将为 ${sbn.packageName} 显示通知")
            showPopupNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Logger.log(this, TAG, "通知已移除：${sbn?.packageName}")
    }

    private fun shouldShowPopup(sbn: StatusBarNotification): Boolean {
        // 过滤掉分组摘要通知
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Logger.log(this, TAG, "已过滤：通知是分组摘要。")
            return false
        }

        val packageName = sbn.packageName

        val selectedPackages = sharedPreferences.getStringSet("selected_packages", emptySet())
        if (selectedPackages?.contains(packageName) != true) {
            Logger.log(this, TAG, "已过滤：${packageName} 不在指定应用列表中。")
            return false
        }

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val selectedHours = sharedPreferences.getStringSet("selected_hours", null)
            ?.map { it.toInt() }?.toSet() ?: (6..20).toSet()
        if (currentHour !in selectedHours) {
            Logger.log(this, TAG, "已过滤：当前时间 ${currentHour} 不在启用时段内。")
            return false
        }

//        if (sbn.isOngoing) {
//            Logger.log(this, TAG, "已过滤：通知是持续性通知。")
//            return false
//        }
//        if (sbn.isOngoing && sbn.notification.category != Notification.CATEGORY_CALL) {
//            Logger.log(this, TAG, "已过滤：通知是持续性通知且非电话类型。")
//            return false
//        }

        return true
    }

    private fun showPopupNotification(sbn: StatusBarNotification) {
        // 统一使用 OverlayService 来显示所有样式的弹窗通知
        showCustomOverlayNotification(sbn)
    }

    private fun getNotificationContent(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification.extras
        var extractedText: String? = null

        // 0. MessagingStyle
        (extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle)?.let { bundle ->
            val messageText = bundle.getCharSequence("text")?.toString()?.trim()
            if (!messageText.isNullOrBlank()) {
                val sender = bundle.getCharSequence("sender")?.toString()?.trim()
                val result = if (!sender.isNullOrBlank()) "$sender: $messageText" else messageText
                Logger.log(this, TAG, "获取到内容 (MessagingStyle): $result")
                extractedText = result
            }
        }

        // 1. EXTRA_TEXT_LINES
        if (extractedText.isNullOrBlank()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")?.trim()
            if (!lines.isNullOrBlank()) {
                Logger.log(this, TAG, "获取到内容 (EXTRA_TEXT_LINES): $lines")
                extractedText = lines
            }
        }

        // 2. EXTRA_BIG_TEXT
        if (extractedText.isNullOrBlank()) {
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            if (!bigText.isNullOrBlank()) {
                Logger.log(this, TAG, "获取到内容 (EXTRA_BIG_TEXT): $bigText")
                extractedText = bigText
            }
        }

        // 3. EXTRA_TEXT
        if (extractedText.isNullOrBlank()) {
            val standardText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            if (!standardText.isNullOrBlank()) {
                Logger.log(this, TAG, "获取到内容 (EXTRA_TEXT): $standardText")
                extractedText = standardText
            }
        }

        // 4. EXTRA_SUB_TEXT
        if (extractedText.isNullOrBlank()) {
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            if (!subText.isNullOrBlank()) {
                Logger.log(this, TAG, "获取到内容 (EXTRA_SUB_TEXT): $subText")
                extractedText = subText
            }
        }

        // 5. tickerText
        if (extractedText.isNullOrBlank()) {
            val ticker = sbn.notification.tickerText?.toString()?.trim()
            if (!ticker.isNullOrBlank()) {
                Logger.log(this, TAG, "获取到内容 (tickerText): $ticker")
                extractedText = ticker
            }
        }

        val title = extras.getString(Notification.EXTRA_TITLE).takeIf { !it.isNullOrBlank() } ?: "注意"
        val text = extractedText ?: "收到一条非标消息"

        Logger.log(this, TAG, "获取结束：最终标题: $title, 最终文本: $text")

        return Pair(title, text)
    }

    private fun showCustomOverlayNotification(sbn: StatusBarNotification) {
        val (title, text) = getNotificationContent(sbn)

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("title", title)
            putExtra("text", text)
            putExtra("isOngoing", sbn.isOngoing)
            putExtra("packageName", sbn.packageName)
            putExtra("contentIntent", sbn.notification.contentIntent)

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
                putExtra("icon", stream.toByteArray())
            }

            putExtra("duration", sharedPreferences.getInt("notification_duration", 5))
            val positionName = sharedPreferences.getString("popup_position", "TOP")
            val styleName = sharedPreferences.getString("popup_style", "NARROW")

            val position = try { PopupPosition.valueOf(positionName!!) } catch (e: Exception) { PopupPosition.TOP }
            val style = try { PopupStyle.valueOf(styleName!!) } catch (e: Exception) { PopupStyle.NARROW }

            putExtra("position", position)
            putExtra("style", style)
            putExtra("backgroundColor", sharedPreferences.getInt("background_color", 0xFF333333.toInt()))
            putExtra("textColor", sharedPreferences.getInt("text_color", 0xFF000000.toInt()))
            putExtra("backgroundAlpha", sharedPreferences.getFloat("background_alpha", 0.5f))
            putExtra("textAlpha", sharedPreferences.getFloat("text_alpha", 1.0f))
            putExtra("fontSize", sharedPreferences.getInt("popup_font_size", 100))
            putExtra("scrollingSpeed", sharedPreferences.getInt("scrolling_speed", 10))
        }

        startService(intent)
    }
}
