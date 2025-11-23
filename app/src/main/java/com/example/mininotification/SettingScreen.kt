package com.example.mininotification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToManagedApps: () -> Unit,
    onNavigateToEnableTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.settingsUiState.collectAsState()

    // 当屏幕显示时，重置对话框状态
    LaunchedEffect(Unit) {
        viewModel.onSettingsScreenShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "系统设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text("托管应用") },
                supportingContent = { Text("选择需要托管通知的应用") },
                modifier = Modifier.clickable { onNavigateToManagedApps() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("启用时间") },
                supportingContent = { Text("设置应用在哪些时间段内生效") },
                modifier = Modifier.clickable { onNavigateToEnableTime() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("通知显示时长") },
                supportingContent = { Text("当前: ${uiState.notificationDuration}秒") },
                modifier = Modifier.clickable { viewModel.toggleDialog("duration", true) }
            )
            HorizontalDivider()

            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text("弹窗位置") },
                supportingContent = { Text("当前：${if (uiState.popupPosition == PopupPosition.TOP) "顶部" else "底部"}") },
                modifier = Modifier.clickable { viewModel.toggleDialog("position", true) }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("弹窗样式") },
                supportingContent = {
                    val styleText = when (uiState.popupStyle) {
                        PopupStyle.NARROW -> "极窄 (滚动速度: ${uiState.scrollingSpeed})"
                        PopupStyle.BANNER -> "横幅"
                    }
                    Text("当前：$styleText")
                },
                modifier = Modifier.clickable { viewModel.toggleDialog("style", true) }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("弹窗颜色") },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = Color(uiState.backgroundColor).copy(alpha = uiState.backgroundAlpha),
                                shape = CircleShape
                            )
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "A",
                            color = Color(uiState.textColor).copy(alpha = uiState.textAlpha),
                            fontSize = 12.sp
                        )
                    }
                },
                modifier = Modifier.clickable { viewModel.toggleDialog("backgroundColor", true) }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("字体大小") },
                supportingContent = { Text("当前: ${uiState.popupFontSize}%") },
                modifier = Modifier.clickable { viewModel.toggleDialog("fontSize", true) }
            )
        }

        // --- Dialogs --- //
        if (uiState.showBackgroundColorDialog) {
            BackgroundColorDialog(viewModel = viewModel)
        }
        if (uiState.showDurationDialog) {
            NotificationDurationDialog(viewModel = viewModel)
        }
        if (uiState.showPositionDialog) {
            PopupPositionDialog(viewModel = viewModel)
        }
        if (uiState.showStyleDialog) {
            PopupStyleDialog(viewModel = viewModel)
        }
        if (uiState.showFontSizeDialog) {
            PopupFontSizeDialog(viewModel = viewModel)
        }
    }
}

@Composable
private fun BackgroundColorDialog(viewModel: SettingsViewModel) {
    val uiState by viewModel.settingsUiState.collectAsState()

    var selectedColorType by remember { mutableStateOf("background") } // "background" or "text"
    val tabIndex = if (selectedColorType == "background") 0 else 1

    var tempBackgroundColor by remember { mutableStateOf(Color(uiState.backgroundColor)) }
    var tempTextColor by remember { mutableStateOf(Color(uiState.textColor)) }
    var tempBackgroundAlpha by remember { mutableStateOf(uiState.backgroundAlpha) }
    var tempTextAlpha by remember { mutableStateOf(uiState.textAlpha) }

    fun Color.toHexString(): String = String.format("#%06X", (0xFFFFFF and this.toArgb()))

    var hexInput by remember {
        mutableStateOf(if (selectedColorType == "background") tempBackgroundColor.toHexString() else tempTextColor.toHexString())
    }

    LaunchedEffect(tempBackgroundColor, tempTextColor, selectedColorType) {
        hexInput = if (selectedColorType == "background") {
            tempBackgroundColor.toHexString()
        } else {
            tempTextColor.toHexString()
        }
    }

    val presetColors = listOf(
        Color.White, Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF212121), Color.Black,
        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
        Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548), Color(0xFF607D8B)
    )

    AlertDialog(
        onDismissRequest = { viewModel.toggleDialog("backgroundColor", false) },
        title = { Text("选择弹窗颜色") },
        text = {
            Column {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = Color.Transparent // 设置背景为透明
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { selectedColorType = "background" },
                        text = { Text("背景") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { selectedColorType = "text" },
                        text = { Text("文字") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    items(presetColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                                .clickable {
                                    if (selectedColorType == "background") {
                                        tempBackgroundColor = color
                                    } else {
                                        tempTextColor = color
                                    }
                                }
                        )
                    }
                }

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("背景不透明度: ${(tempBackgroundAlpha * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = tempBackgroundAlpha,
                        onValueChange = { tempBackgroundAlpha = it },
                        valueRange = 0f..1f,
                    )
                    
                    Text("文字不透明度: ${(tempTextAlpha * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = tempTextAlpha,
                        onValueChange = { tempTextAlpha = it },
                        valueRange = 0f..1f,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { newHex ->
                            hexInput = newHex
                            try {
                                val parsedColor = if (newHex.startsWith("#") && newHex.length == 7) {
                                    Color(android.graphics.Color.parseColor(newHex))
                                } else if (newHex.length == 6) {
                                    Color(android.graphics.Color.parseColor("#$newHex"))
                                } else { null }

                                if (parsedColor != null) {
                                    if (selectedColorType == "background") {
                                        tempBackgroundColor = parsedColor
                                    } else {
                                        tempTextColor = parsedColor
                                    }
                                }
                            } catch (e: IllegalArgumentException) { /* Ignore */ }
                        },
                        label = { Text("Hex") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(tempBackgroundColor.copy(alpha = tempBackgroundAlpha), CircleShape)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                         Text("A", color = tempTextColor.copy(alpha = tempTextAlpha))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.setBackgroundColor(tempBackgroundColor.toArgb())
                viewModel.setTextColor(tempTextColor.toArgb())
                viewModel.setBackgroundAlpha(tempBackgroundAlpha)
                viewModel.setTextAlpha(tempTextAlpha)
                viewModel.toggleDialog("backgroundColor", false)
            }) { Text("确定") }
        },
        dismissButton = {
            Button(onClick = { viewModel.toggleDialog("backgroundColor", false) }) { Text("取消") }
        }
    )
}

@Composable private fun PopupStyleDialog(viewModel: SettingsViewModel) { 
    val uiState by viewModel.settingsUiState.collectAsState()
    var scrollingSpeed by remember { mutableStateOf(uiState.scrollingSpeed) }
    
    AlertDialog(
        onDismissRequest = { viewModel.toggleDialog("style", false) },
        title = { Text("选择弹窗样式") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = (uiState.popupStyle == PopupStyle.NARROW)) { 
                            viewModel.setPopupStyle(PopupStyle.NARROW)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.popupStyle == PopupStyle.NARROW),
                        onClick = { viewModel.setPopupStyle(PopupStyle.NARROW) }
                    )
                    Text(
                        text = "极窄",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                
                // 滚动速度滑块，仅在选择极窄样式时可用
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "滚动速度: $scrollingSpeed",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (uiState.popupStyle == PopupStyle.NARROW) 1f else 0.5f)
                        )
                        Slider(
                            value = scrollingSpeed.toFloat(),
                            onValueChange = { scrollingSpeed = it.toInt() },
                            valueRange = 0f..50f,
                            steps = 49,
                            enabled = (uiState.popupStyle == PopupStyle.NARROW),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = (uiState.popupStyle == PopupStyle.BANNER)) { 
                            viewModel.setPopupStyle(PopupStyle.BANNER)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.popupStyle == PopupStyle.BANNER),
                        onClick = { viewModel.setPopupStyle(PopupStyle.BANNER) }
                    )
                    Text(
                        text = "横幅",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                viewModel.setPopupStyle(uiState.popupStyle)
                viewModel.setScrollingSpeed(scrollingSpeed)
                viewModel.toggleDialog("style", false)
            }) {
                Text("确定")
            }
        }
    )
}

@Composable private fun NotificationDurationDialog(viewModel: SettingsViewModel) { 
    val uiState by viewModel.settingsUiState.collectAsState()
    var duration by remember { mutableStateOf(uiState.notificationDuration) }
    
    AlertDialog(
        onDismissRequest = { viewModel.toggleDialog("duration", false) },
        title = { Text("设置通知显示时长") },
        text = {
            Column {
                Text("时长: ${duration}秒", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                viewModel.setNotificationDuration(duration)
                viewModel.toggleDialog("duration", false)
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            Button(onClick = { viewModel.toggleDialog("duration", false) }) {
                Text("取消")
            }
        }
    )
}

@Composable private fun PopupPositionDialog(viewModel: SettingsViewModel) { 
    val uiState by viewModel.settingsUiState.collectAsState()
    
    AlertDialog(
        onDismissRequest = { viewModel.toggleDialog("position", false) },
        title = { Text("选择弹窗位置") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = (uiState.popupPosition == PopupPosition.TOP)) { viewModel.setPopupPosition(PopupPosition.TOP) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.popupPosition == PopupPosition.TOP),
                        onClick = { viewModel.setPopupPosition(PopupPosition.TOP) }
                    )
                    Text(
                        text = "顶部",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = (uiState.popupPosition == PopupPosition.BOTTOM)) { viewModel.setPopupPosition(PopupPosition.BOTTOM) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.popupPosition == PopupPosition.BOTTOM),
                        onClick = { viewModel.setPopupPosition(PopupPosition.BOTTOM) }
                    )
                    Text(
                        text = "底部",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.toggleDialog("position", false) }) {
                Text("确定")
            }
        }
    )
}

@Composable private fun PopupFontSizeDialog(viewModel: SettingsViewModel) { 
    val uiState by viewModel.settingsUiState.collectAsState()
    var fontSize by remember { mutableStateOf(uiState.popupFontSize) }
    
    AlertDialog(
        onDismissRequest = { viewModel.toggleDialog("fontSize", false) },
        title = { Text("设置字体大小") },
        text = {
            Column {
                Text("大小: ${fontSize}%", style = MaterialTheme.typography.bodyMedium, fontSize = (16 * (fontSize / 100f)).sp)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    valueRange = 50f..200f,
                    steps = 29,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                viewModel.setPopupFontSize(fontSize)
                viewModel.toggleDialog("fontSize", false)
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            Button(onClick = { viewModel.toggleDialog("fontSize", false) }) {
                Text("取消")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingScreenPreview() {
    // Preview will likely not work correctly as it needs a ViewModel instance
    // SettingScreen(viewModel = viewModel(), onBack = {}, onNavigateToManagedApps = {}, onNavigateToEnableTime = {})
}
