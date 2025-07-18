package com.byteflow.www.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.byteflow.www.ApiClient
import com.byteflow.www.AuthActivity
import com.byteflow.www.AuthManager
import com.byteflow.www.UserInfo
import com.byteflow.www.SubscribeInfo
import com.byteflow.www.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private var userInfo: UserInfo? = null
    private var subscribeInfo: SubscribeInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        loadUserData()
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                // 获取用户信息
                val userResult = ApiClient.getUserInfo()
                if (userResult.isSuccess) {
                    userInfo = userResult.getOrNull()
                }
                
                // 获取订阅信息
                val subscribeResult = ApiClient.getSubscribeInfo()
                if (subscribeResult.isSuccess) {
                    subscribeInfo = subscribeResult.getOrNull()
                }
                
                // 更新UI
                updateUserInfo()
                
            } catch (e: Exception) {
                Toast.makeText(context, "加载用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateUserInfo() {
        userInfo?.let { user ->
            // 设置用户基本信息
            binding.userNameText.text = user.email.substringBefore("@")
            binding.userEmailText.text = user.email
            
            // 设置会员信息
            val membershipType = if (user.planId != null) "会员用户" else "免费用户"
            binding.membershipTypeText.text = membershipType
            
            // 设置到期时间
            val expiryDate = if (user.expiredAt > 0) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                "到期时间: ${sdf.format(Date(user.expiredAt * 1000))}"
            } else {
                "到期时间: 永久"
            }
            binding.expiryDateText.text = expiryDate
        }
        
        subscribeInfo?.let { subscribe ->
            // 设置流量信息 - API返回的是字节，需要转换为GB
            val totalBytes = subscribe.transferEnable
            val usedBytes = subscribe.u + subscribe.d
            val remainingBytes = totalBytes - usedBytes
            
            // 转换为GB格式显示
            val totalGB = formatBytes(totalBytes)
            val usedGB = formatBytes(usedBytes)
            val remainingGB = formatBytes(remainingBytes)
            
            binding.totalDataText.text = "总流量: $totalGB"
            binding.usedDataText.text = "已使用: $usedGB" 
            binding.remainingDataText.text = "剩余: $remainingGB"
            
            // 更新进度条
            val progress = if (totalBytes > 0) {
                ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()
            } else {
                0
            }
            binding.dataUsageProgress.progress = progress
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
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

    private fun setupViews() {
        // 移除旧的静态数据设置，改为动态加载
    }

    private fun setupClickListeners() {
        binding.aboutCard.setOnClickListener {
            // 跳转到GitHub项目页面
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/MagicNop/Xboard_Client"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.logoutCard.setOnClickListener {
            handleLogout()
        }
    }
    
    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                // 调用登出API
                val result = ApiClient.logout()
                
                if (result.isSuccess) {
                    // 清除本地登录状态
                    AuthManager.logout(requireContext())
                    
                    // 跳转到登录界面
                    val intent = Intent(requireContext(), AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    
                    // 结束当前Activity
                    requireActivity().finish()
                } else {
                    Toast.makeText(context, "登出失败，请重试", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "登出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}