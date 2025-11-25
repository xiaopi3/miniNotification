package com.example.mininotification

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val LOG_FILE_NAME = "app_log.txt"
    private const val MAX_LOG_LINES = 10000

    @Synchronized
    private fun getLogFile(context: Context): File {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return file
    }

    fun log(context: Context, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp $tag: $message\n"
        writeToFile(context, logMessage)
    }

    fun getLogContent(context: Context): String {
        return try {
            synchronized(this) {
                getLogFile(context).readText()
            }
        } catch (e: FileNotFoundException) {
            "" // 如果文件不存在，返回空字符串
        }
    }

    fun clearLog(context: Context) {
        try {
            synchronized(this) {
                getLogFile(context).writeText("")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun writeToFile(context: Context, logMessage: String) {
        try {
            val logFile = getLogFile(context)
            val lines = logFile.readLines().toMutableList()
            lines.add(logMessage.trimEnd())

            // 如果日志行数超过了限制，就只保留最新的 MAX_LOG_LINES 行
            while (lines.size > MAX_LOG_LINES) {
                lines.removeAt(0)
            }

            logFile.writeText(lines.joinToString("\n"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
