package com.byteflow.www.ui.base

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fragment基类
 * 提供通用的Fragment功能和生命周期管理
 */
abstract class BaseFragment : Fragment() {
    
    // ==================== 协程作用域 ====================
    protected val fragmentScope: CoroutineScope
        get() = lifecycleScope
    
    // ==================== 生命周期方法 ====================
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeFragment()
    }
    
    // ==================== 抽象方法 ====================
    /**
     * 初始化Fragment
     * 子类需要实现此方法来设置视图、监听器等
     */
    protected abstract fun initializeFragment()
    
    // ==================== 通用工具方法 ====================
    /**
     * 显示Toast消息
     */
    protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        context?.let {
            Toast.makeText(it, message, duration).show()
        }
    }
    
    /**
     * 显示长Toast消息
     */
    protected fun showLongToast(message: String) {
        showToast(message, Toast.LENGTH_LONG)
    }
    
    /**
     * 安全地执行协程任务
     */
    protected fun launchSafely(block: suspend CoroutineScope.() -> Unit) {
        fragmentScope.launch(Dispatchers.Main) {
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
        showToast("操作失败: ${error.message}")
    }
    
    /**
     * 获取安全的Context
     */
    protected fun getSafeContext(): Context? {
        return if (isAdded && context != null) context else null
    }
    
    /**
     * 检查Fragment是否仍然附加到Activity
     */
    protected fun isFragmentValid(): Boolean {
        return isAdded && !isDetached && !isRemoving
    }
} 