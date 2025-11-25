package com.example.mininotification

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    logViewModel: LogViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Scroll states for the single text block
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    // State from ViewModel
    val logContent by logViewModel.logContent.collectAsState()
    val searchResults by logViewModel.searchResults.collectAsState()
    val currentHighlightIndex by logViewModel.currentHighlightIndex.collectAsState()
    val searchState by logViewModel.searchState.collectAsState()

    // Local UI state
    var searchText by remember { mutableStateOf("") }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // --- Effects ---

    // Effect for showing toasts for search status
    LaunchedEffect(searchState) {
        when (searchState) {
            SearchState.NO_RESULTS -> {
                Toast.makeText(context, "未找到关键字", Toast.LENGTH_SHORT).show()
            }
            SearchState.LOOPED_TO_START -> {
                Toast.makeText(context, "已返回顶部重新搜索", Toast.LENGTH_SHORT).show()
            }
            else -> {} // Do nothing for IDLE or NORMAL
        }
    }

    // Effect for scrolling to the highlighted item
    LaunchedEffect(currentHighlightIndex, searchResults, textLayoutResult) {
        val layoutResult = textLayoutResult ?: return@LaunchedEffect
        if (currentHighlightIndex in searchResults.indices) {
            val resultRange = searchResults[currentHighlightIndex].range
            val line = layoutResult.getLineForOffset(resultRange.first)
            val yPosition = layoutResult.getLineTop(line)

            coroutineScope.launch {
                // Scroll vertically to center the line
                vScrollState.animateScrollTo((yPosition - vScrollState.viewportSize / 2).toInt().coerceAtLeast(0))
            }
        }
    }

    // --- UI ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = {
                    IconButton(onClick = {
                        logViewModel.clearSearch() // Clear search state on exit
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { logViewModel.clearLog() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除日志")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        logViewModel.search(it)
                    },
                    label = { Text("搜索日志") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (searchResults.isNotEmpty()) {
                        logViewModel.navigateToNextResult()
                    } else {
                        logViewModel.search(searchText)
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索下一个")
                }
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))

            // Log Display Area
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScrollState)
                        .horizontalScroll(hScrollState)
                ) {
                    val annotatedString = buildAnnotatedString {
                        append(logContent)
                        if (searchText.isNotBlank()) {
                            searchResults.forEachIndexed { index, result ->
                                val color = if (index == currentHighlightIndex) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                                addStyle(
                                    style = SpanStyle(background = color),
                                    start = result.range.first,
                                    end = result.range.last + 1
                                )
                            }
                        }
                    }

                    Text(
                        text = annotatedString,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        softWrap = false, // CRITICAL: Disables wrapping
                        onTextLayout = {
                            textLayoutResult = it
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { logViewModel.refreshLog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("刷新")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { logViewModel.exportLog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出")
                }
            }
        }
    }
}
