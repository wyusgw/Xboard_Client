package com.byteflow.www.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.byteflow.www.R
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.byteflow.www.utils.PreferencesManager
import android.content.ClipData
import android.content.ClipboardManager

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backButton = view.findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val context = requireContext()
        // 深色模式
        val darkModeSwitch = view.findViewById<Switch>(R.id.darkModeSwitch)
        darkModeSwitch.isChecked = isNightMode(context)
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                saveNightMode(context, true)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                saveNightMode(context, false)
            }
        }
        // 自动连接
        val autoConnectSwitch = view.findViewById<Switch>(R.id.autoConnectSwitch)
        autoConnectSwitch.isChecked = PreferencesManager.isAutoConnect(context)
        autoConnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setAutoConnect(context, isChecked)
        }
        // 通知
        val notificationSwitch = view.findViewById<Switch>(R.id.notificationSwitch)
        notificationSwitch.isChecked = PreferencesManager.isNotificationEnabled(context)
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setNotificationEnabled(context, isChecked)
        }
        // 订阅链接
        val subscriptionUrlText = view.findViewById<android.widget.TextView>(R.id.subscriptionUrlText)
        val copySubscriptionButton = view.findViewById<android.widget.ImageButton>(R.id.copySubscriptionButton)
        val currentUrl = PreferencesManager.getSubscriptionUrl(context) ?: "未设置"
        subscriptionUrlText.text = currentUrl
        copySubscriptionButton.setOnClickListener {
            val url = PreferencesManager.getSubscriptionUrl(context) ?: ""
            if (url.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("订阅链接", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "暂无订阅链接", Toast.LENGTH_SHORT).show()
            }
        }
        // 重置设置
        val resetSettingsButton = view.findViewById<android.widget.Button>(R.id.resetSettingsButton)
        resetSettingsButton.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("重置所有设置")
                .setMessage("确定要重置所有设置吗？这将清除所有本地配置。")
                .setPositiveButton("确定") { _, _ ->
                    PreferencesManager.clearAll(context)
                    Toast.makeText(context, "已重置所有设置", Toast.LENGTH_SHORT).show()
                    // 重置UI
                    darkModeSwitch.isChecked = false
                    autoConnectSwitch.isChecked = false
                    notificationSwitch.isChecked = true
                    subscriptionUrlText.text = "未设置"
                }
                .setNegativeButton("取消", null)
                .show()
        }
        // 关于信息（可点击跳转）
        val aboutText = view.findViewById<android.widget.TextView>(R.id.aboutText)
        aboutText.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("关于")
                .setMessage("九霄云 Android 客户端\n版本 1.0.0\n\n项目地址：https://github.com/MagicNop/Xboard_Client")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun isNightMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("night_mode", false)
    }
    private fun saveNightMode(context: Context, night: Boolean) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("night_mode", night).apply()
    }
    private fun updateThemeToggleIcon(button: ImageButton) {
        val isNight = isNightMode(requireContext())
        val iconRes = if (isNight) R.drawable.ic_moon else R.drawable.ic_sun
        button.setImageResource(iconRes)
    }
} 