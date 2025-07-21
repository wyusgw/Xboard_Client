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
import androidx.appcompat.app.AppCompatDelegate
import com.byteflow.www.ui.fragments.SettingsFragment
import com.byteflow.www.R
import com.bumptech.glide.Glide

/**
 * 个人资料Fragment - 重构版本
 * 保持所有原有功能，但采用更模块化的架构
 */
class ProfileFragment : Fragment() {
    
    // ==================== 视图绑定 ====================
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    // ==================== 数据管理 ====================
    private var userInfo: UserInfo? = null
    private var subscribeInfo: SubscribeInfo? = null
    
    // ==================== 常量定义 ====================
    companion object {
        // 移除GITHUB_URL常量
    }

    // ==================== 生命周期方法 ====================
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
        initializeFragment()
        // 移除主题切换按钮逻辑
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // ==================== 初始化方法 ====================
    private fun initializeFragment() {
        setupClickListeners()
        loadUserData()
    }
    
    private fun setupClickListeners() {
        binding.logoutCard.setOnClickListener {
            handleLogoutClick()
        }
        binding.settingsCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.byteflow.www.ui.fragments.SettingsFragment(), "SETTINGS")
                .addToBackStack(null)
                .commit()
        }
        // 移除supportCard和aboutCard相关逻辑
    }
    
    // ==================== 数据加载方法 ====================
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                loadUserInfo()
                loadSubscribeInfo()
                updateUserInfoDisplay()
            } catch (e: Exception) {
                handleDataLoadError(e.message ?: "未知错误")
            }
        }
    }
    
    private suspend fun loadUserInfo() {
        val userResult = ApiClient.getUserInfo()
        if (userResult.isSuccess) {
            userInfo = userResult.getOrNull()
        }
    }
    
    private suspend fun loadSubscribeInfo() {
        val subscribeResult = ApiClient.getSubscribeInfo()
        if (subscribeResult.isSuccess) {
            subscribeInfo = subscribeResult.getOrNull()
        }
    }
    
    private fun updateUserInfoDisplay() {
        updateUserBasicInfo()
        updateMembershipInfo()
        updateDataUsageInfo()
    }
    
    // ==================== UI更新方法 ====================
    private fun updateUserBasicInfo() {
        userInfo?.let { user ->
            binding.userNameText.text = extractUserName(user.email)
            binding.userEmailText.text = user.email
            // 头像加载
            val avatarUrl = user.avatarUrl
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_avatar)
                    .error(R.drawable.ic_profile_avatar)
                    .circleCrop()
                    .into(binding.userAvatarImage)
            } else {
                binding.userAvatarImage.setImageResource(R.drawable.ic_profile_avatar)
            }
        }
    }
    
    private fun updateMembershipInfo() {
        userInfo?.let { user ->
            val membershipType = determineMembershipType(user.planId)
            binding.membershipTypeText.text = membershipType
            
            val expiryDate = formatExpiryDate(user.expiredAt)
            binding.expiryDateText.text = expiryDate
        }
    }
    
    private fun updateDataUsageInfo() {
        subscribeInfo?.let { subscribe ->
            val totalBytes = subscribe.transferEnable
            val usedBytes = subscribe.u + subscribe.d
            val remainingBytes = totalBytes - usedBytes
            
            val totalGB = formatBytes(totalBytes)
            val usedGB = formatBytes(usedBytes)
            val remainingGB = formatBytes(remainingBytes)
            
            binding.totalDataText.text = totalGB // 只显示数值，无前缀
            binding.usedDataText.text = usedGB // 只显示数值，无前缀
            binding.remainingDataText.text = remainingGB // 只显示数值，无前缀

            updateDataUsageProgress(usedBytes, totalBytes)
        }
    }
    
    private fun updateDataUsageProgress(usedBytes: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) {
            ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()
        } else {
            0
        }
        binding.dataUsageProgress.progress = progress
    }
    
    // ==================== 数据处理方法 ====================
    private fun extractUserName(email: String): String {
        return email.substringBefore("@")
    }
    
    private fun determineMembershipType(planId: Int?): String {
        return if (planId != null) "会员用户" else "免费用户"
    }
    
    private fun formatExpiryDate(expiredAt: Long): String {
        return if (expiredAt > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(expiredAt * 1000))
        } else {
            "永久"
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
    
    // ==================== 点击处理方法 ====================
    private fun handleLogoutClick() {
        lifecycleScope.launch {
            try {
                performLogout()
            } catch (e: Exception) {
                handleLogoutError(e.message ?: "登出失败")
            }
        }
    }
    
    private suspend fun performLogout() {
        val result = ApiClient.logout()
        
        if (result.isSuccess) {
            handleLogoutSuccess()
        } else {
            handleLogoutError("登出失败，请重试")
        }
    }
    
    private fun handleLogoutSuccess() {
        // 清除本地登录状态
        AuthManager.logout(requireContext())
        
        // 跳转到登录界面
        navigateToAuthActivity()
    }
    
    private fun navigateToAuthActivity() {
        val intent = Intent(requireContext(), AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }
    
    // ==================== 错误处理方法 ====================
    private fun handleDataLoadError(message: String) {
        context?.let {
            Toast.makeText(it, "加载用户信息失败: $message", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleLogoutError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}