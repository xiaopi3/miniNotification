package com.example.mininotification

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

// --- 数据类/枚举定义 ---
data class AppInfo(val name: String, val packageName: String, val icon: Drawable? = null)
enum class PopupPosition { TOP, BOTTOM }
enum class PopupStyle { NARROW, BANNER }

// --- ManagedApps UI 状态封装 ---
data class ManagedAppsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filteredApps: List<AppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val areAllSelected: Boolean = false,
    val showOnlySelected: Boolean = false, // 新增：是否仅显示已选择
    // 添加最近通知应用列表
    val recentNotificationApps: List<AppInfo> = emptyList()
)

// --- SettingScreen UI 状态封装 ---
data class SettingsUiState(
    // Values
    val notificationDuration: Int = 5,
    val popupPosition: PopupPosition = PopupPosition.TOP,
    val popupStyle: PopupStyle = PopupStyle.NARROW,
    val scrollingSpeed: Int = 10,
    val backgroundColor: Int = 0xFF333333.toInt(), // 修正：使用 Int 存储 ARGB 颜色
    val textColor: Int = 0xFF000000.toInt(), // 文字颜色
    val backgroundAlpha: Float = 0.5f, // 背景不透明度
    val textAlpha: Float = 1.0f, // 文字不透明度
    val popupFontSize: Int = 100,

    // Dialog Visibility
    val showDurationDialog: Boolean = false,
    val showPositionDialog: Boolean = false,
    val showStyleDialog: Boolean = false,
    val showBackgroundColorDialog: Boolean = false,
    val showFontSizeDialog: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- 1. Managed Apps State ---
    private val _appsIsLoading = MutableStateFlow(true)
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _appsSearchQuery = MutableStateFlow("")
    private val _appsSelectedPackages = MutableStateFlow(setOf<String>())
    private val _showOnlySelected = MutableStateFlow(false) // 新增
    // 添加最近通知应用的跟踪
    private val _recentNotificationApps = MutableStateFlow<Set<String>>(emptySet())

    val managedAppsUiState: StateFlow<ManagedAppsUiState> = combine(
        _appsIsLoading,
        _appsSearchQuery,
        _allApps,
        _appsSelectedPackages,
        _recentNotificationApps,
        _showOnlySelected
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val isLoading = flows[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val query = flows[1] as String
        @Suppress("UNCHECKED_CAST")
        val apps = flows[2] as List<AppInfo>
        @Suppress("UNCHECKED_CAST")
        val selected = flows[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val recentApps = flows[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val showOnlySelected = flows[5] as Boolean

        // 1. 根据搜索查询过滤
        val searchedApps = if (query.isBlank()) apps else apps.filter {
            it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }

        // 2. 如果勾选了“仅显示已选择”，则再次过滤
        val displayAppsSource = if (showOnlySelected) {
            searchedApps.filter { it.packageName in selected }
        } else {
            searchedApps
        }

        // 3. 决定是否显示分组（仅在未搜索且未筛选“仅显示已选择”时）
        val showGroups = query.isBlank() && !showOnlySelected

        val recentNotificationAppList = if (showGroups) displayAppsSource.filter { it.packageName in recentApps } else emptyList()
        val otherApps = if (showGroups) displayAppsSource.filter { it.packageName !in recentApps } else displayAppsSource
        
        val finalDisplayApps = if (showGroups) recentNotificationAppList + otherApps else otherApps

        ManagedAppsUiState(
            isLoading = isLoading,
            searchQuery = query,
            filteredApps = finalDisplayApps,
            selectedPackages = selected,
            areAllSelected = searchedApps.isNotEmpty() && searchedApps.all { it.packageName in selected },
            showOnlySelected = showOnlySelected,
            recentNotificationApps = recentNotificationAppList // 只在显示分组时才有值
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManagedAppsUiState())

    fun onSearchQueryChanged(query: String) { _appsSearchQuery.value = query }

    fun onShowOnlySelectedChanged(show: Boolean) { // 新增
        _showOnlySelected.value = show
    }

    fun onAppSelectionChanged(packageName: String, isSelected: Boolean) {
        val current = _appsSelectedPackages.value.toMutableSet()
        if (isSelected) current.add(packageName) else current.remove(packageName)
        _appsSelectedPackages.value = current
        
        // 保存到SharedPreferences
        sharedPreferences.edit().putStringSet("selected_packages", current).apply()
    }
    fun onSelectAll(shouldSelectAll: Boolean) {
        // 基于当前搜索结果进行全选/全不选，而不是UI上可能被“仅显示已选择”过滤后的列表
        val query = _appsSearchQuery.value
        val appsToConsider = if (query.isBlank()) _allApps.value else _allApps.value.filter {
            it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        val packageNamesToChange = appsToConsider.map { it.packageName }.toSet()

        val currentSelection = _appsSelectedPackages.value.toMutableSet()
        if (shouldSelectAll) {
            currentSelection.addAll(packageNamesToChange)
        } else {
            currentSelection.removeAll(packageNamesToChange)
        }
        _appsSelectedPackages.value = currentSelection
        sharedPreferences.edit().putStringSet("selected_packages", currentSelection).apply()
    }
    
    // 添加记录最近通知应用的方法
    fun addRecentNotificationApp(packageName: String) {
        val current = _recentNotificationApps.value.toMutableSet()
        current.add(packageName)
        _recentNotificationApps.value = current
    }

    // --- 2. Enable Time State ---
    private val _selectedHours = MutableStateFlow((6..20).toSet())
    val selectedHours: StateFlow<Set<Int>> = _selectedHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), (6..20).toSet())

    fun toggleHourSelection(hour: Int) {
        val currentSelection = _selectedHours.value.toMutableSet()
        if (currentSelection.contains(hour)) currentSelection.remove(hour) else currentSelection.add(hour)
        _selectedHours.value = currentSelection
        
        // 保存到SharedPreferences
        sharedPreferences.edit().putStringSet("selected_hours", currentSelection.map { it.toString() }.toSet()).apply()
    }

    // --- 3. Setting Screen State ---
    private val _settingsUiState = MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /**
     * 当设置屏幕显示时调用，重置所有对话框的可见性状态
     */
    fun onSettingsScreenShown() {
        _settingsUiState.value = _settingsUiState.value.copy(
            showDurationDialog = false,
            showPositionDialog = false,
            showStyleDialog = false,
            showBackgroundColorDialog = false,
            showFontSizeDialog = false
        )
    }

    fun toggleDialog(dialogType: String, show: Boolean) {
        _settingsUiState.value = when (dialogType) {
            "duration" -> _settingsUiState.value.copy(showDurationDialog = show)
            "position" -> _settingsUiState.value.copy(showPositionDialog = show)
            "style" -> _settingsUiState.value.copy(showStyleDialog = show)
            "backgroundColor" -> _settingsUiState.value.copy(showBackgroundColorDialog = show)
            "fontSize" -> _settingsUiState.value.copy(showFontSizeDialog = show)
            else -> _settingsUiState.value
        }
    }

    fun setNotificationDuration(duration: Int) { 
        _settingsUiState.value = _settingsUiState.value.copy(notificationDuration = duration)
        sharedPreferences.edit().putInt("notification_duration", duration).apply()
    }
    fun setPopupPosition(position: PopupPosition) { 
        _settingsUiState.value = _settingsUiState.value.copy(popupPosition = position)
        sharedPreferences.edit().putString("popup_position", position.name).apply()
    }
    fun setPopupStyle(style: PopupStyle) { 
        _settingsUiState.value = _settingsUiState.value.copy(popupStyle = style) 
        sharedPreferences.edit().putString("popup_style", style.name).apply()
    } 
    fun setScrollingSpeed(speed: Int) { 
        _settingsUiState.value = _settingsUiState.value.copy(scrollingSpeed = speed)
        sharedPreferences.edit().putInt("scrolling_speed", speed).apply()
    }
    fun setBackgroundColor(color: Int) { 
        _settingsUiState.value = _settingsUiState.value.copy(backgroundColor = color)
        sharedPreferences.edit().putInt("background_color", color).apply()
    }
    fun setTextColor(color: Int) {
        _settingsUiState.value = _settingsUiState.value.copy(textColor = color)
        sharedPreferences.edit().putInt("text_color", color).apply()
    }
    fun setBackgroundAlpha(alpha: Float) { 
        _settingsUiState.value = _settingsUiState.value.copy(backgroundAlpha = alpha)
        sharedPreferences.edit().putFloat("background_alpha", alpha).apply()
    }
    fun setTextAlpha(alpha: Float) { 
        _settingsUiState.value = _settingsUiState.value.copy(textAlpha = alpha)
        sharedPreferences.edit().putFloat("text_alpha", alpha).apply()
    }
    fun setPopupFontSize(fontSize: Int) { 
        _settingsUiState.value = _settingsUiState.value.copy(popupFontSize = fontSize)
        sharedPreferences.edit().putInt("popup_font_size", fontSize).apply()
    }
    
    /**
     * 显示测试通知
     */
    fun showTestNotification() {
        // 修复：在显示测试通知前，确保所有对话框都处于关闭状态
        onSettingsScreenShown()

        val uiState = _settingsUiState.value
        val intent = Intent(getApplication(), OverlayService::class.java).apply {
            putExtra("duration", uiState.notificationDuration)
            putExtra("position", uiState.popupPosition)
            putExtra("style", uiState.popupStyle)
            putExtra("backgroundColor", uiState.backgroundColor)
            putExtra("textColor", uiState.textColor)
            putExtra("backgroundAlpha", uiState.backgroundAlpha)
            putExtra("textAlpha", uiState.textAlpha)
            putExtra("fontSize", uiState.popupFontSize)
            putExtra("scrollingSpeed", uiState.scrollingSpeed)
            val calendar = java.util.Calendar.getInstance()
            val seconds = calendar.get(java.util.Calendar.SECOND).toString().padStart(2, '0')
            putExtra("title", "测试标题$seconds")
            putExtra("text", "这是一条非常长的、用于测试滚动效果的通知内容，它会一直滚动下去。")
        }
        getApplication<Application>().startService(intent)
    }

    /**
     * 重启通知监听服务
     */
    fun restartNotificationListenerService() {
        val context = getApplication<Application>()
        try {
            val componentName = ComponentName(context, MyNotificationListenerService::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Toast.makeText(context, "服务已尝试重启", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "重启失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    init { 
        loadInstalledApps()
        loadSettingsFromPreferences()
    }

    // --- 数据加载 ---
    private fun containsChinese(s: String): Boolean = s.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val myPackageName = getApplication<Application>().packageName

            val allPackages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            }

            val allApps = allPackages.mapNotNull { packageInfo ->
                if (packageInfo.packageName == myPackageName) return@mapNotNull null
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                AppInfo(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm)
                )
            }

            val collator = Collator.getInstance(Locale.CHINESE)
            val sortedApps = allApps.sortedWith(compareBy<AppInfo> { !containsChinese(it.name) }.thenComparator { a, b -> collator.compare(a.name, b.name) })

            _allApps.value = sortedApps
            _appsIsLoading.value = false
        }
    }
    
    /**
     * 从SharedPreferences加载设置
     */
    private fun loadSettingsFromPreferences() {
        // 加载托管应用
        val selectedPackages = sharedPreferences.getStringSet("selected_packages", emptySet()) ?: emptySet()
        _appsSelectedPackages.value = selectedPackages

        // 新增：加载最近通知的应用
        val recentPackages = sharedPreferences.getStringSet("recent_notification_packages", emptySet()) ?: emptySet()
        _recentNotificationApps.value = recentPackages
        
        // 加载启用时间
        val selectedHoursStrings = sharedPreferences.getStringSet("selected_hours", null)
        if (selectedHoursStrings != null) {
            val selectedHours = selectedHoursStrings.map { it.toInt() }.toSet()
            _selectedHours.value = selectedHours
        }
        
        // 加载其他设置
        val notificationDuration = sharedPreferences.getInt("notification_duration", 5)
        val popupPositionName = sharedPreferences.getString("popup_position", "TOP")
        val popupStyleName = sharedPreferences.getString("popup_style", "NARROW")
        val scrollingSpeed = sharedPreferences.getInt("scrolling_speed", 10)
        val backgroundColor = sharedPreferences.getInt("background_color", 0xFF333333.toInt())
        val textColor = sharedPreferences.getInt("text_color", 0xFF000000.toInt())
        val backgroundAlpha = sharedPreferences.getFloat("background_alpha", 0.5f)
        val textAlpha = sharedPreferences.getFloat("text_alpha", 1.0f)
        val popupFontSize = sharedPreferences.getInt("popup_font_size", 100)
        
        val popupPosition = try {
            PopupPosition.valueOf(popupPositionName ?: "TOP")
        } catch (e: IllegalArgumentException) {
            PopupPosition.TOP
        }
        
        val popupStyle = try {
            PopupStyle.valueOf(popupStyleName ?: "NARROW")
        } catch (e: IllegalArgumentException) {
            PopupStyle.NARROW
        }
        
        _settingsUiState.value = SettingsUiState(
            notificationDuration = notificationDuration,
            popupPosition = popupPosition,
            popupStyle = popupStyle,
            scrollingSpeed = scrollingSpeed,
            backgroundColor = backgroundColor,
            textColor = textColor,
            backgroundAlpha = backgroundAlpha,
            textAlpha = textAlpha,
            popupFontSize = popupFontSize
        )
    }
}
