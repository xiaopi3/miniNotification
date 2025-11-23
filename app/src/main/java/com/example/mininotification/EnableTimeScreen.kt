package com.example.mininotification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 有状态的 Composable, 连接 ViewModel
 */
@Composable
fun EnableTimeScreen(
    viewModel: SettingsViewModel, // 接收新的统一 ViewModel
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 收集选中的小时状态
    val selectedHours by viewModel.selectedHours.collectAsState()

    EnableTimeContent(
        onBack = onBack,
        selectedHours = selectedHours,
        onHourClick = { hour -> viewModel.toggleHourSelection(hour) }, // 传递事件
        modifier = modifier
    )
}

/**
 * 无状态的纯 UI Composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnableTimeContent(
    onBack: () -> Unit,
    selectedHours: Set<Int>,
    onHourClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("启用时间") },
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("请选择启用托管的时间，0代表0点到1点前")
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = modifier
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(24) { hour ->
                    val isSelected = hour in selectedHours

                    val color =
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val textColor =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onHourClick(hour) } // 调用传递进来的事件
                    ) {
                        Text(text = hour.toString(), color = textColor)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EnableTimeScreenPreview() {
    // 预览现在可以正常工作
    EnableTimeContent(
        onBack = {},
        selectedHours = (6..20).toSet(), // 提供示例数据
        onHourClick = {}
    )
}
