package com.example.mininotification

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 这是应用内弹窗，用于在 Activity 中快速预览
 */
@Composable
fun TestNotificationPopup(
    settings: SettingsUiState,
    onDismissRequest: () -> Unit,
    notifications: List<Pair<String?, String?>>
) {
    val alignment = if (settings.popupPosition == PopupPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter

    Popup(
        alignment = alignment,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
    ) {
        MultiTestNotificationPopup(notifications = notifications, settings = settings)
    }
}

@Composable
fun MultiTestNotificationPopup(
    notifications: List<Pair<String?, String?>>,
    settings: SettingsUiState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 按照正向顺序显示通知，最新的在最下面
            notifications.forEachIndexed { index, notification ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (settings.popupStyle == PopupStyle.BANNER) {
                    BannerNotificationWithContent(settings, notification.first, notification.second)
                } else {
                    NarrowNotificationWithContent(settings, notification.first, notification.second)
                }
            }
        }
    }
}

@Composable
private fun BannerNotificationWithContent(settings: SettingsUiState, title: String?, text: String?) {
    val originalBackgroundColor = Color(settings.backgroundColor)
    val finalContentColor = Color(settings.textColor).copy(alpha = settings.textAlpha)

    val containerColor = if (originalBackgroundColor == Color.Transparent) {
        Color.Unspecified
    } else {
        originalBackgroundColor.copy(alpha = settings.backgroundAlpha)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Test Icon",
                modifier = Modifier.size(32.dp),
                tint = finalContentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title ?: "测试标题",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * (settings.popupFontSize / 100f)).sp,
                    color = finalContentColor
                )
                Text(
                    text ?: "这是一条测试通知内容。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * (settings.popupFontSize / 100f)).sp,
                    color = finalContentColor
                )
            }
        }
    }
}

@Composable
private fun NarrowNotificationWithContent(settings: SettingsUiState, title: String?, text: String?) {
    val velocity = (settings.scrollingSpeed * 10).dp
    val originalBackgroundColor = Color(settings.backgroundColor)
    val finalContentColor = Color(settings.textColor).copy(alpha = settings.textAlpha)

    val containerColor = if (originalBackgroundColor == Color.Transparent) {
        Color.Unspecified
    } else {
        originalBackgroundColor.copy(alpha = settings.backgroundAlpha)
    }

    Card(
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = velocity
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Test Icon",
                modifier = Modifier.size(16.dp),
                tint = finalContentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${title ?: "测试标题"}: ${text ?: "这是一条非常长的、用于测试滚动效果的通知内容，它会一直滚动下去。"}",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * (settings.popupFontSize / 100f)).sp,
                maxLines = 1,
                color = finalContentColor
            )
        }
    }
}

/**
 * 这是悬浮窗的实际 UI, 可被应用内 Popup 和系统级 Service 复用
 */
@Composable
fun TestNotificationContent(settings: SettingsUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (settings.popupStyle == PopupStyle.BANNER) {
            BannerNotificationWithContent(settings, "测试标题", "这是一条测试通知内容。")
        } else {
            NarrowNotificationWithContent(settings, "测试标题", "这是一条非常长的、用于测试滚动效果的通知内容，它会一直滚动下去。")
        }
    }
}

@Composable
private fun BannerNotification(settings: SettingsUiState) {
    BannerNotificationWithContent(settings, "测试标题", "这是一条测试通知内容。")
}

@Composable
private fun NarrowNotification(settings: SettingsUiState) {
    NarrowNotificationWithContent(settings, "测试标题", "这是一条非常长的、用于测试滚动效果的通知内容，它会一直滚动下去。")
}
