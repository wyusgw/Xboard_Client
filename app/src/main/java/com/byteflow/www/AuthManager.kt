package com.byteflow.www

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTH_DATA = "auth_data"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveLoginData(context: Context, loginData: LoginData) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_TOKEN, loginData.token)
            .putString(KEY_AUTH_DATA, loginData.authData)
            .putBoolean(KEY_IS_ADMIN, loginData.isAdmin ?: false)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
        
        // 设置API客户端的认证token (使用auth_data而不是token)
        ApiClient.setAuthToken(loginData.authData)
    }
    
    fun getLoginData(context: Context): LoginData? {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_TOKEN, null)
        val authData = prefs.getString(KEY_AUTH_DATA, null)
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        
        return if (token != null && authData != null) {
            LoginData(token, authData, isAdmin)
        } else {
            null
        }
    }
    
    fun isLoggedIn(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && 
               prefs.getString(KEY_AUTH_DATA, null) != null
    }
    
    fun logout(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_AUTH_DATA)
            .remove(KEY_IS_ADMIN)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
        
        // 清除API客户端的认证token
        ApiClient.setAuthToken(null)
    }
    
    fun initializeAuth(context: Context) {
        val loginData = getLoginData(context)
        if (loginData != null) {
            // 使用auth_data而不是token作为认证头
            ApiClient.setAuthToken(loginData.authData)
        }
    }
}