package com.example.mininotification

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * 有状态的 Composable，负责连接 ViewModel 和 UI
 */
@Composable
fun ManagedAppsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.managedAppsUiState.collectAsState()

    ManagedAppsContent(
        onBack = onBack,
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAppSelectionChanged = viewModel::onAppSelectionChanged,
        onSelectAll = viewModel::onSelectAll,
        onShowOnlySelectedChanged = viewModel::onShowOnlySelectedChanged,
        onApplyChanges = viewModel::restartNotificationListenerService // 新增：应用更改
    )
}

/**
 * 无状态的纯 UI Composable，只负责显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagedAppsContent(
    onBack: () -> Unit,
    uiState: ManagedAppsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onAppSelectionChanged: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onShowOnlySelectedChanged: (Boolean) -> Unit,
    onApplyChanges: () -> Unit // 新增
) {
    var showRestartDialog by remember { mutableStateOf(false) }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("提示") },
            text = { Text("服务已尝试重启，若依然没有托管成功建议手动重新开启通知权限。") },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyChanges() // 执行重启服务的逻辑
                        showRestartDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { showRestartDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("托管应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(16.dp))
                        Text("应用列表加载中...")
                    }
                }
            } else {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    label = { Text("搜索应用") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSelectAll(!uiState.areAllSelected) }
                    ) {
                        Checkbox(
                            checked = uiState.areAllSelected,
                            onCheckedChange = null // 点击Row来触发
                        )
                        Text("全选")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onShowOnlySelectedChanged(!uiState.showOnlySelected) }
                    ) {
                        Checkbox(
                            checked = uiState.showOnlySelected,
                            onCheckedChange = null // 点击Row来触发
                        )
                        Text("仅显示已勾选")
                    }

                    Button(onClick = { showRestartDialog = true }) {
                        Text("应用")
                    }
                }

                HorizontalDivider()

                LazyColumn {
                    // 如果设置了“仅显示已勾选”或正在搜索，则不显示分组标题
                    val showGroupHeaders = !uiState.showOnlySelected && uiState.searchQuery.isBlank()
                    val recentApps = uiState.filteredApps.filter { it.packageName in uiState.recentNotificationApps.map { r -> r.packageName } }
                    val otherApps = uiState.filteredApps.filter { it.packageName !in uiState.recentNotificationApps.map { r -> r.packageName } }

                    if (showGroupHeaders && recentApps.isNotEmpty()) {
                        item {
                            Text(
                                text = "最近通知的应用",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(recentApps, key = { "recent-${it.packageName}" }) { app ->
                            AppItem(
                                app = app,
                                isSelected = app.packageName in uiState.selectedPackages,
                                onAppSelectionChanged = onAppSelectionChanged
                            )
                        }

                        if (otherApps.isNotEmpty()) {
                            item {
                                Text(
                                    text = "其他应用",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else if (showGroupHeaders && otherApps.isEmpty()) {
                        // 仅当没有最近应用，也没有其他应用时，显示空状态（通常不会发生，因为至少有“其他应用”标题）
                    } else if (!showGroupHeaders && uiState.filteredApps.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("没有匹配的应用")
                            }
                        }
                    }

                    items(otherApps, key = { "other-${it.packageName}" }) { app ->
                        AppItem(
                            app = app,
                            isSelected = app.packageName in uiState.selectedPackages,
                            onAppSelectionChanged = onAppSelectionChanged
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun AppItem(
    app: AppInfo,
    isSelected: Boolean,
    onAppSelectionChanged: (String, Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAppSelectionChanged(app.packageName, !isSelected) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onAppSelectionChanged(app.packageName, it) }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Image(
            painter = rememberDrawablePainter(app.icon),
            contentDescription = "${app.name} 图标",
            modifier = Modifier.size(40.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManagedAppsScreenPreview() {
    val sampleApps = listOf(
        AppInfo("示例应用 1", "com.example.app1"),
        AppInfo("一个名字很长的示例应用", "com.example.long.package.name.app2"),
    )
    val uiState = ManagedAppsUiState(
        isLoading = false,
        searchQuery = "",
        filteredApps = sampleApps,
        selectedPackages = setOf("com.example.app1"),
        showOnlySelected = false
    )
    ManagedAppsContent(
        onBack = {},
        uiState = uiState,
        onSearchQueryChanged = {},
        onAppSelectionChanged = { _, _ -> },
        onSelectAll = {},
        onShowOnlySelectedChanged = {},
        onApplyChanges = {} // 新增
    )
}

@Preview(showBackground = true)
@Composable
private fun ManagedAppsScreenLoadingPreview() {
    val uiState = ManagedAppsUiState(isLoading = true)
    ManagedAppsContent(
        onBack = {},
        uiState = uiState,
        onSearchQueryChanged = {},
        onAppSelectionChanged = { _, _ -> },
        onSelectAll = {},
        onShowOnlySelectedChanged = {},
        onApplyChanges = {} // 新增
    )
}
