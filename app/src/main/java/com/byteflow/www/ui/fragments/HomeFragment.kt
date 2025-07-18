package com.byteflow.www.ui.fragments

import android.animation.AnimatorInflater
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.byteflow.www.R
import com.byteflow.www.databinding.FragmentHomeBinding
import com.byteflow.www.service.LeafVpnService
import com.byteflow.www.utils.SubscriptionManager

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var isConnected = false
    private var isOperationInProgress = false // 添加操作进行中标志
    private var vpnService: LeafVpnService? = null
    private var isBound = false
    private lateinit var sharedPreferences: SharedPreferences
    private val subscriptionManager = SubscriptionManager.getInstance()
    
    companion object {
        private const val TAG = "HomeFragment"
        private const val VPN_REQUEST_CODE = 100
        private const val PREFS_NAME = "vpn_settings"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        private const val KEY_USE_SUBSCRIPTION = "use_subscription"
    }
    
    // VPN权限请求launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "VPN权限已授予")
            startVpnService()
        } else {
            Log.w(TAG, "VPN权限被拒绝")
            Toast.makeText(context, "需要VPN权限才能连接", Toast.LENGTH_SHORT).show()
            // 重置连接状态
            isConnected = false
            updateConnectionStatus()
        }
    }
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "VPN服务已连接")
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "VPN服务连接断开")
            vpnService = null
            isBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化 SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupViews()
        setupClickListeners()
        updateConnectionStatus()
        updateSubscriptionStatus()
    }

    private fun setupViews() {
        // Set initial data
        binding.dataUsedText.text = "已使用: 2.5 GB"
        binding.dataRemainingText.text = "剩余: 97.5 GB"
        updateServerSelection()
    }

    private fun setupClickListeners() {
        binding.connectionButton.setOnClickListener {
            toggleConnection()
        }

        binding.serverSelectionCard.setOnClickListener {
            // 跳转到节点列表页面
            try {
                val nodeListFragment = NodeListFragment()
                parentFragmentManager.beginTransaction()
                    .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.fragment_container, nodeListFragment, "NODE_LIST")
                    .addToBackStack(null)
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG, "跳转到节点列表失败", e)
                Toast.makeText(context, "跳转失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateSubscriptionStatus() {
        binding.subscriptionStatusText?.text = "自动订阅模式"
    }
    
    private fun updateServerSelection() {
        val selectedNodeName = sharedPreferences.getString("selected_node", "")
        
        if (!selectedNodeName.isNullOrEmpty()) {
            binding.selectedServerText.text = selectedNodeName
        } else {
            binding.selectedServerText.text = "自动选择节点"
        }
    }

    private fun toggleConnection() {
        // 防止快速点击导致的竞态条件
        if (isOperationInProgress) {
            Log.d(TAG, "操作正在进行中，忽略重复点击")
            return
        }
        
        isOperationInProgress = true
        
        if (isConnected) {
            // 断开VPN
            stopVpnService()
            isConnected = false
        } else {
            // 连接VPN
            requestVpnPermission()
        }
        updateConnectionStatus()
    }
    
    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(requireContext())
        if (vpnIntent != null) {
            // 需要请求权限
            Log.d(TAG, "请求VPN权限")
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // 已有权限，直接启动
            Log.d(TAG, "已有VPN权限，直接启动")
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        try {
            val intent = Intent(requireContext(), LeafVpnService::class.java)
            intent.action = LeafVpnService.ACTION_START_VPN
            
            // 总是使用订阅模式
            val subscriptionUrl = sharedPreferences.getString(KEY_SUBSCRIPTION_URL, "")
            if (!subscriptionUrl.isNullOrEmpty()) {
                intent.putExtra(LeafVpnService.EXTRA_SUBSCRIPTION_URL, subscriptionUrl)
                Log.d(TAG, "使用自动订阅模式启动VPN: $subscriptionUrl")
            } else {
                Log.d(TAG, "订阅链接为空，使用默认配置")
            }
            
            requireContext().startForegroundService(intent)
            
            // 绑定服务
            val bindIntent = Intent(requireContext(), LeafVpnService::class.java)
            requireContext().bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            isConnected = true
            updateConnectionStatus()
            
            Toast.makeText(context, "VPN连接已启动", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "VPN服务启动成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动VPN服务失败", e)
            Toast.makeText(context, "启动VPN失败: ${e.message}", Toast.LENGTH_LONG).show()
            isConnected = false
            updateConnectionStatus()
        } finally {
            // 重置操作标志
            isOperationInProgress = false
        }
    }
    
    private fun stopVpnService() {
        try {
            // 解绑服务
            if (isBound) {
                requireContext().unbindService(serviceConnection)
                isBound = false
            }
            
            // 停止服务
            val intent = Intent(requireContext(), LeafVpnService::class.java)
            intent.action = LeafVpnService.ACTION_STOP_VPN
            requireContext().startService(intent)
            
            Toast.makeText(context, "VPN连接已断开", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "VPN服务已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止VPN服务失败", e)
            Toast.makeText(context, "停止VPN失败: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            // 重置操作标志
            isOperationInProgress = false
        }
    }

    private fun updateConnectionStatus() {
        if (isConnected) {
            binding.connectionStatusText.text = "已连接"
            binding.connectionStatusIcon.setImageResource(R.drawable.ic_connected)
            
            // 设置天蓝色阴影
            setShadowColor("#87CEEB")
            
            // 启动阴影呼吸灯动画
            val breathingAnimation = AnimatorInflater.loadAnimator(context, R.anim.breathing_glow)
            breathingAnimation.setTarget(binding.connectionButton)
            breathingAnimation.start()
        } else {
            binding.connectionStatusText.text = "未连接"
            binding.connectionStatusIcon.setImageResource(R.drawable.ic_power)
            
            // 设置灰色阴影
            setShadowColor("#CCCCCC")
            
            // 停止动画并重置elevation
            binding.connectionButton.clearAnimation()
            binding.connectionButton.elevation = 8f * resources.displayMetrics.density
        }
    }
    
    private fun setShadowColor(colorHex: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val color = Color.parseColor(colorHex)
            binding.connectionButton.outlineAmbientShadowColor = color
            binding.connectionButton.outlineSpotShadowColor = color
        }
    }

    override fun onDestroyView() {
        // 清理服务连接
        if (isBound) {
            try {
                requireContext().unbindService(serviceConnection)
                isBound = false
            } catch (e: Exception) {
                Log.e(TAG, "解绑服务失败", e)
            }
        }
        
        super.onDestroyView()
        _binding = null
    }
}