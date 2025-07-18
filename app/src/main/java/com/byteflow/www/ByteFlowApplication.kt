package com.byteflow.www

import android.app.Application

class ByteFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化认证管理器
        AuthManager.initializeAuth(this)
    }
}