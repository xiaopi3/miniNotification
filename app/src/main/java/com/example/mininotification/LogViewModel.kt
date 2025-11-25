package com.example.mininotification

import android.app.Application
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//用于封装搜索结果的数据类，包含在完整日志字符串中的范围
data class SearchResult(val range: IntRange)

//用于向UI传达搜索状态的枚举
enum class SearchState {
    IDLE, //初始状态或清除搜索时
    NO_RESULTS, //未找到结果
    NORMAL, //正常查找
    LOOPED_TO_START //已循环到顶部
}

class LogViewModel(application: Application) : AndroidViewModel(application) {

    // 完整日志内容
    private val _logContent = MutableStateFlow("")
    val logContent = _logContent.asStateFlow()

    // 查找到的所有结果
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    // 当前高亮结果的索引
    private val _currentHighlightIndex = MutableStateFlow(-1)
    val currentHighlightIndex = _currentHighlightIndex.asStateFlow()

    // 向UI报告当前搜索状态
    private val _searchState = MutableStateFlow(SearchState.IDLE)
    val searchState = _searchState.asStateFlow()

    init {
        refreshLog()
    }

    fun refreshLog() {
        _logContent.value = Logger.getLogContent(getApplication())
        clearSearch()
    }

    fun clearLog() {
        Logger.clearLog(getApplication())
        refreshLog()
    }

    // 在日志中搜索关键字
    fun search(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }

        val results = mutableListOf<SearchResult>()
        val content = _logContent.value
        var startIndex = 0
        while (startIndex < content.length) {
            val foundIndex = content.indexOf(query, startIndex, ignoreCase = true)
            if (foundIndex != -1) {
                results.add(SearchResult(foundIndex until (foundIndex + query.length)))
                startIndex = foundIndex + query.length
            } else {
                break
            }
        }

        _searchResults.value = results
        if (results.isEmpty()) {
            _currentHighlightIndex.value = -1
            _searchState.value = SearchState.NO_RESULTS
        } else {
            _currentHighlightIndex.value = 0
            _searchState.value = SearchState.NORMAL
        }
    }

    // 导航到下一个搜索结果
    fun navigateToNextResult() {
        if (_searchResults.value.isEmpty()) return

        var nextIndex = _currentHighlightIndex.value + 1
        if (nextIndex >= _searchResults.value.size) {
            nextIndex = 0 // 循环
            _searchState.value = SearchState.LOOPED_TO_START
        } else {
            _searchState.value = SearchState.NORMAL
        }
        _currentHighlightIndex.value = nextIndex
    }

    // 清除搜索状态和结果
    fun clearSearch() {
        _searchResults.value = emptyList()
        _currentHighlightIndex.value = -1
        _searchState.value = SearchState.IDLE
    }

    fun exportLog() {
        val context = getApplication<Application>().applicationContext
        val logText = _logContent.value

        if (logText.isBlank()) {
            Toast.makeText(context, "日志为空，无需导出", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "迷你通知-$timeStamp.txt"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val file = File(downloadsDir, fileName)
                file.writeText(logText)

                launch(Dispatchers.Main) {
                    Toast.makeText(context, "日志已导出到 Download 目录", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}