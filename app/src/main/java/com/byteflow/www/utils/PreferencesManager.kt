package com.byteflow.www.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences管理器
 * 统一管理应用的所有SharedPreferences操作
 */
object PreferencesManager {
    
    // ==================== 常量定义 ====================
    private const val PREFS_NAME = "vpn_settings"
    private const val KEY_SUBSCRIPTION_URL = "subscription_url"
    private const val KEY_USE_SUBSCRIPTION = "use_subscription"
    private const val KEY_SELECTED_NODE = "selected_node"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
    private const val KEY_DARK_MODE = "dark_mode"
    
    // ==================== SharedPreferences实例 ====================
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ==================== 订阅相关方法 ====================
    fun getSubscriptionUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_SUBSCRIPTION_URL, null)
    }
    
    fun setSubscriptionUrl(context: Context, url: String?) {
        getPrefs(context).edit()
            .putString(KEY_SUBSCRIPTION_URL, url)
            .apply()
    }
    
    fun isUseSubscription(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_SUBSCRIPTION, false)
    }
    
    fun setUseSubscription(context: Context, use: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_USE_SUBSCRIPTION, use)
            .apply()
    }
    
    // ==================== 节点相关方法 ====================
    fun getSelectedNode(context: Context): String? {
        return getPrefs(context).getString(KEY_SELECTED_NODE, null)
    }
    
    fun setSelectedNode(context: Context, nodeName: String?) {
        getPrefs(context).edit()
            .putString(KEY_SELECTED_NODE, nodeName)
            .apply()
    }
    
    // ==================== 连接相关方法 ====================
    fun isAutoConnect(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    }
    
    fun setAutoConnect(context: Context, auto: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_AUTO_CONNECT, auto)
            .apply()
    }
    
    // ==================== 通知相关方法 ====================
    fun isNotificationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATION_ENABLED, true)
    }
    
    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_NOTIFICATION_ENABLED, enabled)
            .apply()
    }
    
    // ==================== 主题相关方法 ====================
    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
    }
    
    fun setDarkMode(context: Context, dark: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_DARK_MODE, dark)
            .apply()
    }
    
    // ==================== 通用方法 ====================
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    fun contains(context: Context, key: String): Boolean {
        return getPrefs(context).contains(key)
    }
} 