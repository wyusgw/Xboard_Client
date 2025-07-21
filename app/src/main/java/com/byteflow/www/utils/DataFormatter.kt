package com.byteflow.www.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据格式化工具类
 * 统一管理应用中的所有数据格式化功能
 */
object DataFormatter {
    
    // ==================== 日期格式化 ====================
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 格式化日期
     */
    fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp * 1000))
    }
    
    /**
     * 格式化日期时间
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormatter.format(Date(timestamp * 1000))
    }
    
    /**
     * 格式化到期时间
     */
    fun formatExpiryDate(expiredAt: Long): String {
        return if (expiredAt > 0) {
            "到期时间: ${formatDate(expiredAt)}"
        } else {
            "到期时间: 永久"
        }
    }
    
    // ==================== 字节格式化 ====================
    /**
     * 格式化字节数为可读格式
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L * 1024L -> {
                val tb = bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0)
                "${String.format("%.2f", tb)}TB"
            }
            bytes >= 1024L * 1024L * 1024L -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                "${String.format("%.1f", gb)}GB"
            }
            bytes >= 1024L * 1024L -> {
                val mb = bytes / (1024.0 * 1024.0)
                "${String.format("%.0f", mb)}MB"
            }
            bytes >= 1024L -> {
                val kb = bytes / 1024.0
                "${String.format("%.0f", kb)}KB"
            }
            else -> "${bytes}B"
        }
    }
    
    /**
     * 格式化流量使用情况
     */
    fun formatDataUsage(usedBytes: Long, totalBytes: Long): DataUsageInfo {
        val usedFormatted = formatBytes(usedBytes)
        val totalFormatted = formatBytes(totalBytes)
        val remainingFormatted = formatBytes(totalBytes - usedBytes)
        
        val progress = if (totalBytes > 0) {
            ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()
        } else {
            0
        }
        
        return DataUsageInfo(
            used = usedFormatted,
            total = totalFormatted,
            remaining = remainingFormatted,
            progress = progress
        )
    }
    
    // ==================== 价格格式化 ====================
    /**
     * 格式化价格（分转元）
     */
    fun formatPrice(priceInCents: Int?): String {
        return if (priceInCents != null && priceInCents > 0) {
            val yuan = priceInCents / 100.0
            "¥${if (yuan % 1 == 0.0) yuan.toInt() else yuan}/月"
        } else {
            "免费"
        }
    }
    
    /**
     * 格式化价格（分转元，无单位）
     */
    fun formatPriceOnly(priceInCents: Int?): String {
        return if (priceInCents != null && priceInCents > 0) {
            val yuan = priceInCents / 100.0
            "¥${if (yuan % 1 == 0.0) yuan.toInt() else yuan}"
        } else {
            "免费"
        }
    }
    
    // ==================== 延迟格式化 ====================
    /**
     * 格式化延迟时间
     */
    fun formatLatency(latency: Int): String {
        return when {
            latency == -1 -> "未测试"
            latency == 0 -> "超时"
            else -> "${latency}ms"
        }
    }
    
    /**
     * 获取延迟状态文本
     */
    fun getLatencyStatusText(latency: Int, isTesting: Boolean): String {
        return when {
            isTesting -> "测试中..."
            latency == -1 -> "未测试"
            latency == 0 -> "超时"
            latency <= 100 -> "优秀"
            latency <= 300 -> "良好"
            else -> "较差"
        }
    }
    
    // ==================== 数据类 ====================
    data class DataUsageInfo(
        val used: String,
        val total: String,
        val remaining: String,
        val progress: Int
    )
} 