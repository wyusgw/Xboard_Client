package com.byteflow.www.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel基类
 * 提供通用的ViewModel功能和协程管理
 */
abstract class BaseViewModel : ViewModel() {
    
    // ==================== 协程作用域 ====================
    // 直接使用父类的viewModelScope，无需重新定义
    
    // ==================== 通用工具方法 ====================
    /**
     * 安全地执行协程任务
     */
    protected fun launchSafely(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                block()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
    
    /**
     * 在IO线程执行协程任务
     */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
    
    /**
     * 处理错误
     * 子类可以重写此方法来自定义错误处理
     */
    protected open fun handleError(error: Exception) {
        // 默认错误处理，子类可以重写
    }
    
    /**
     * 检查ViewModel是否仍然活跃
     */
    protected fun isViewModelActive(): Boolean {
        return true // 简化实现，ViewModel在onCleared之前都是活跃的
    }
} 