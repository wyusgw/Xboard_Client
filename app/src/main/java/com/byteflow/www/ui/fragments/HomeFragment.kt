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
import androidx.lifecycle.lifecycleScope
import com.byteflow.www.ApiClient
import com.byteflow.www.R
import com.byteflow.www.SubscribeInfo
import com.byteflow.www.UserInfo
import com.byteflow.www.databinding.FragmentHomeBinding
import com.byteflow.www.service.LeafVpnService
import com.byteflow.www.utils.SubscriptionManager
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import com.byteflow.www.models.ClashProxy
import com.byteflow.www.ui.fragments.NodeSelectDialogFragment
import com.byteflow.www.utils.FlagUtils
import com.hbb20.CCPCountry
import androidx.appcompat.app.AppCompatDelegate
import com.byteflow.www.ui.fragments.SettingsFragment
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/**
 * 首页Fragment - 重构版本
 * 保持所有原有功能，但采用更模块化的架构
 */
class HomeFragment : Fragment() {
    
    // ==================== 视图绑定 ====================
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ==================== 状态管理 ====================
    private var isConnected = false
    private var isOperationInProgress = false
    private var isBound = false
    
    // ==================== 服务管理 ====================
    private var vpnService: LeafVpnService? = null
    
    // ==================== 数据管理 ====================
    private lateinit var sharedPreferences: SharedPreferences
    private val subscriptionManager = SubscriptionManager.getInstance()
    private var userInfo: UserInfo? = null
    private var subscribeInfo: SubscribeInfo? = null
    
    // ==================== 常量定义 ====================
    companion object {
        private const val TAG = "HomeFragment"
        private const val VPN_REQUEST_CODE = 100
        private const val PREFS_NAME = "vpn_settings"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        private const val KEY_USE_SUBSCRIPTION = "use_subscription"
    }
    
    // ==================== 权限管理 ====================
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleVpnPermissionResult(result.resultCode)
    }
    
    // ==================== 服务连接 ====================
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

    // ==================== 连接状态枚举 ====================
    private enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, FAILED
    }
    private var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    private var pendingAction: String? = null

    // ==================== 生命周期方法 ====================
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
        initializeFragment()
        initializeUI()
        loadUserData()
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        // 检查VPN是否在运行，并同步UI
        val isVpnRunning = com.byteflow.www.service.LeafVpnService.isVpnRunning
        connectionState = if (isVpnRunning) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        // 恢复VPN运行时长
        if (isVpnRunning) {
            val startTime = sharedPreferences.getLong("vpn_start_time", 0L)
            if (startTime > 0L) {
                vpnConnectedTime = startTime
            } else {
                vpnConnectedTime = System.currentTimeMillis()
                sharedPreferences.edit().putLong("vpn_start_time", vpnConnectedTime).apply()
            }
        } else {
            vpnConnectedTime = 0L
            sharedPreferences.edit().remove("vpn_start_time").apply()
        }
        updateVpnButtonUI(connectionState)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cleanupResources()
        stopGlowBreathAnimation() // 清理动画
        hideVpnUptime()
        _binding = null
    }
    
    // ==================== 初始化方法 ====================
    private fun initializeFragment() {
        initializeSharedPreferences()
        initializeViews()
        setupClickListeners()
        updateUI()
        loadUserData()
        // 自动拉取订阅节点
        autoFetchSubscriptionNodes()
    }
    
    private fun initializeSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun initializeViews() {
        // 初始显示加载中状态
        val userName = userInfo?.email?.substringBefore("@") ?: ""
        binding.dataUsedText.text = "加载中..."
        binding.dataRemainingText.text = "加载中..."
        binding.root.findViewById<TextView>(R.id.welcome_text)?.let { welcomeTextView ->
            welcomeTextView.text = if (userName.isNotEmpty()) "欢迎回来，$userName" else "欢迎回来"
        }
        updateServerSelection()
        // 已移除进度条相关代码
    }
    
    private fun initializeUI() {
        // 已移除进度条相关代码
        // 初始化服务器选择
        updateServerSelection()
        // 初始化VPN按钮状态
        binding.vpnButtonText.text = "启动VPN"
        binding.vpnStatusInfo.visibility = View.GONE
        binding.vpnButtonGlow.alpha = 0.0f
        binding.vpnButtonContainer.isEnabled = true
        binding.vpnButtonIcon.visibility = View.VISIBLE
        binding.vpnUptimeText.visibility = View.GONE
        // 已移除vpnConnectionIndicator相关代码
        // 设置Lottie动画初始状态
        binding.serverSelectionCard.isEnabled = true
    }
    
    private fun setupClickListeners() {
        // 服务器选择卡片点击监听器
        binding.serverSelectionCard.setOnClickListener {
            showNodeSelectionDialog()
        }
        
        // VPN按钮点击监听器
        binding.vpnButtonContainer.setOnClickListener {
            handleVpnButtonClick()
        }
        
        setupQuickActionListeners()
    }
    
    private fun setupQuickActionListeners() {
        // 速度测试按钮
        binding.root.findViewById<View>(R.id.speed_test_button)?.setOnClickListener {
            handleSpeedTest()
        }
        
        // 设置按钮
        binding.root.findViewById<View>(R.id.settings_button)?.setOnClickListener {
            handleSettings()
        }
        
        // 支持按钮
        binding.root.findViewById<View>(R.id.support_button)?.setOnClickListener {
            handleSupport()
        }
    }
    
    // ==================== UI更新方法 ====================
    private fun updateUI() {
        updateConnectionStatus()
        updateSubscriptionStatus()
        updateVpnButtonUI(connectionState) // 更新启动按钮UI
    }
    
    private fun updateConnectionStatus() {
        // 记录连接状态变化
        Log.d(TAG, "连接状态更新: $connectionState")
    }
    
    private fun updateSubscriptionStatus() {
        // 不再设置subscriptionStatusText，彻底去除模式字样
    }
    
    private fun updateServerSelection() {
        val selectedNodeName = sharedPreferences.getString("selected_node", null)
        binding.selectedServerText.text = if (selectedNodeName.isNullOrBlank()) "请选择节点" else selectedNodeName
        binding.subscriptionStatusText.text = ""
        val proxies = SubscriptionManager.getInstance().getCachedConfig()?.proxies ?: emptyList()
        val node = proxies.find { it.name == selectedNodeName }
        val countryCode = FlagUtils.guessCountryCode(node)
        val countryList = CCPCountry.getLibraryMasterCountriesEnglish()
        val country = countryList.find { it.nameCode.equals(countryCode, ignoreCase = true) }
        val flagResId = country?.getFlagID() ?: R.drawable.ic_flag_default
        if (flagResId != 0) {
            binding.nodeFlagImageView.setImageResource(flagResId)
        } else {
            binding.nodeFlagImageView.setImageResource(R.drawable.ic_flag_default)
        }
    }
    
    private fun parseCountryCode(node: ClashProxy?): String? {
        if (node == null) return null
        // 1. 通过节点名关键词
        val name = node.name.uppercase()
        return when {
            name.contains("香港") || name.contains("HK") -> "HK"
            name.contains("台湾") || name.contains("TW") -> "TW"
            name.contains("新加坡") || name.contains("SG") -> "SG"
            name.contains("日本") || name.contains("JP") -> "JP"
            name.contains("美国") || name.contains("US") -> "US"
            name.contains("韩国") || name.contains("KR") -> "KR"
            name.contains("马来西亚") || name.contains("MY") -> "MY"
            name.contains("泰国") || name.contains("TH") -> "TH"
            name.contains("菲律宾") || name.contains("PH") -> "PH"
            name.contains("越南") || name.contains("VN") -> "VN"
            name.contains("印尼") || name.contains("ID") -> "ID"
            name.contains("英国") || name.contains("UK") -> "GB"
            name.contains("德国") || name.contains("DE") -> "DE"
            name.contains("法国") || name.contains("FR") -> "FR"
            name.contains("土耳其") || name.contains("TR") -> "TR"
            name.contains("巴西") || name.contains("BR") -> "BR"
            name.contains("阿根廷") || name.contains("AR") -> "AR"
            name.contains("硬编码") -> null
            else -> null
        }
    }
    
    // ==================== 数据管理方法 ====================
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                loadUserInfo()
                loadSubscribeInfo()
                updateDataUsage()
                // 设置欢迎语（用户名）
                val userName = userInfo?.email?.substringBefore("@") ?: ""
                binding.welcomeText.text = if (userName.isNotEmpty()) "欢迎回来 $userName" else "欢迎回来"
            } catch (e: Exception) {
                Log.e(TAG, "加载用户数据失败", e)
                handleDataLoadError()
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
    
    private fun updateDataUsage() {
        subscribeInfo?.let { subscribe ->
            val totalBytes = subscribe.transferEnable
            val usedBytes = subscribe.u + subscribe.d
            val remainingBytes = totalBytes - usedBytes
            val usedGB = formatBytes(usedBytes)
            val remainingGB = formatBytes(remainingBytes)
            binding.dataUsedText.text = usedGB
            binding.dataRemainingText.text = remainingGB
        } ?: run {
            binding.dataUsedText.text = "暂无数据"
            binding.dataRemainingText.text = "暂无数据"
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                "${String.format("%.1f", gb)} GB"
            }
            bytes >= 1024L * 1024L -> {
                val mb = bytes / (1024.0 * 1024.0)
                "${String.format("%.0f", mb)} MB"
            }
            bytes >= 1024L -> {
                val kb = bytes / 1024.0
                "${String.format("%.0f", kb)} KB"
            }
            else -> "${bytes} B"
        }
    }
    
    // ==================== VPN按钮管理 ====================
    private fun handleVpnButtonClick() {
        if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.DISCONNECTING) {
            return
        }
        
        if (connectionState == ConnectionState.CONNECTED) {
            disconnectVpn()
        } else {
            connectVpn()
        }
    }
    
    // ==================== 盾牌呼吸动画管理 ====================
    private var glowAnimator: ValueAnimator? = null

    private fun startGlowBreathAnimation() {
        if (glowAnimator?.isRunning == true) return
        glowAnimator = ValueAnimator.ofFloat(0.3f, 0.8f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                binding.vpnButtonGlow.alpha = animation.animatedValue as Float
            }
        }
        glowAnimator?.start()
    }
    private fun stopGlowBreathAnimation() {
        glowAnimator?.cancel()
        binding.vpnButtonGlow.alpha = 0f
    }

    private var vpnUptimeHandler: Handler? = null
    private var vpnUptimeRunnable: Runnable? = null
    private var vpnConnectedTime: Long = 0L

    private fun showVpnUptime() {
        if (vpnConnectedTime == 0L) vpnConnectedTime = System.currentTimeMillis()
        binding.vpnUptimeText.visibility = View.VISIBLE
        vpnUptimeHandler = Handler(Looper.getMainLooper())
        vpnUptimeRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - vpnConnectedTime
                val hours = (elapsed / (1000 * 60 * 60))
                val minutes = (elapsed / (1000 * 60)) % 60
                val seconds = (elapsed / 1000) % 60
                binding.vpnUptimeText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                vpnUptimeHandler?.postDelayed(this, 1000)
            }
        }
        vpnUptimeHandler?.post(vpnUptimeRunnable!!)
    }

    private fun hideVpnUptime() {
        binding.vpnUptimeText.visibility = View.GONE
        vpnUptimeHandler?.removeCallbacks(vpnUptimeRunnable!!)
        vpnConnectedTime = 0L
    }

    // ==================== VPN按钮UI状态 ====================
    private fun updateVpnButtonUI(state: ConnectionState) {
        when (state) {
            ConnectionState.DISCONNECTED, ConnectionState.FAILED -> {
                binding.vpnButtonText.text = "启动VPN"
                binding.vpnStatusInfo.visibility = View.GONE
                binding.vpnButtonContainer.isEnabled = true
                binding.vpnButtonIcon.visibility = View.VISIBLE
                binding.vpnUptimeText.visibility = View.GONE
                stopGlowBreathAnimation()
                hideVpnUptime()
            }
            ConnectionState.CONNECTING -> {
                binding.vpnButtonText.text = "连接中..."
                binding.vpnStatusInfo.visibility = View.VISIBLE
                binding.vpnStatusText.text = "连接中..."
                binding.vpnStatusDot.isSelected = false
                binding.vpnButtonContainer.isEnabled = false
                binding.vpnButtonIcon.visibility = View.VISIBLE
                binding.vpnUptimeText.visibility = View.GONE
                startGlowBreathAnimation()
                hideVpnUptime()
            }
            ConnectionState.CONNECTED -> {
                binding.vpnButtonText.text = "断开连接"
                binding.vpnStatusInfo.visibility = View.VISIBLE
                binding.vpnStatusText.text = "已连接"
                binding.vpnStatusDot.isSelected = true
                binding.vpnButtonContainer.isEnabled = true
                binding.vpnButtonIcon.visibility = View.GONE
                binding.vpnUptimeText.visibility = View.VISIBLE
                binding.vpnConnectionInfo.text = "安全连接已建立"
                startGlowBreathAnimation()
                showVpnUptime()
            }
            ConnectionState.DISCONNECTING -> {
                binding.vpnButtonText.text = "断开中..."
                binding.vpnStatusInfo.visibility = View.VISIBLE
                binding.vpnStatusText.text = "断开中..."
                binding.vpnStatusDot.isSelected = false
                binding.vpnButtonContainer.isEnabled = false
                binding.vpnButtonIcon.visibility = View.VISIBLE
                binding.vpnUptimeText.visibility = View.GONE
                startGlowBreathAnimation()
                hideVpnUptime()
            }
        }
    }

    // ==================== 连接管理方法重构 ====================
    private fun handleConnectionToggle() {
        if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.DISCONNECTING) {
            return
        }
        if (connectionState == ConnectionState.CONNECTED) {
            disconnectVpn()
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        connectionState = ConnectionState.CONNECTING
        pendingAction = "connect"
        updateVpnButtonUI(connectionState)
        
        val subscriptionUrl = sharedPreferences.getString(KEY_SUBSCRIPTION_URL, null)
        val useSubscription = sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION, false)
        // 记录VPN启动时间
        vpnConnectedTime = System.currentTimeMillis()
        sharedPreferences.edit().putLong("vpn_start_time", vpnConnectedTime).apply()
        if (useSubscription && !subscriptionUrl.isNullOrEmpty()) {
            startVpnWithSubscription(subscriptionUrl)
        } else {
            startVpnWithHardcodedConfig()
        }
    }

    private fun disconnectVpn() {
        connectionState = ConnectionState.DISCONNECTING
        pendingAction = "disconnect"
        updateVpnButtonUI(connectionState)
        
        try {
            val intent = Intent(requireContext(), LeafVpnService::class.java).apply {
                action = LeafVpnService.ACTION_STOP_VPN
            }
            requireContext().startService(intent)
            connectionState = ConnectionState.DISCONNECTED
            vpnConnectedTime = 0L
            sharedPreferences.edit().remove("vpn_start_time").apply()
            updateVpnButtonUI(connectionState)
        } catch (e: Exception) {
            Log.e(TAG, "断开VPN连接失败", e)
            connectionState = ConnectionState.FAILED
            updateVpnButtonUI(connectionState)
            Toast.makeText(context, "断开连接失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVpnWithSubscription(subscriptionUrl: String) {
        lifecycleScope.launch {
            try {
                val clashConfig = subscriptionManager.fetchSubscription(subscriptionUrl)
                if (clashConfig != null) {
                    requestVpnPermission()
        } else {
                    Log.w(TAG, "获取订阅配置失败，使用硬编码配置")
                    startVpnWithHardcodedConfig()
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动订阅VPN失败", e)
                connectionState = ConnectionState.FAILED
                updateVpnButtonUI(connectionState)
                startVpnWithHardcodedConfig()
            }
        }
    }

    private fun startVpnWithHardcodedConfig() {
        requestVpnPermission()
    }
    
    private fun requestVpnPermission() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "VPN权限已授予")
            startVpnService()
        } else {
            Log.w(TAG, "VPN权限被拒绝")
            connectionState = ConnectionState.FAILED
            updateVpnButtonUI(connectionState)
            Toast.makeText(context, "需要VPN权限才能连接", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startVpnService() {
        try {
            val subscriptionUrl = sharedPreferences.getString(KEY_SUBSCRIPTION_URL, null)
            val useSubscription = sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION, false)
            val intent = Intent(requireContext(), LeafVpnService::class.java).apply {
                action = LeafVpnService.ACTION_START_VPN
                if (useSubscription && !subscriptionUrl.isNullOrEmpty()) {
                    putExtra(LeafVpnService.EXTRA_SUBSCRIPTION_URL, subscriptionUrl)
                }
            }
            requireContext().startService(intent)
            requireContext().bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            connectionState = ConnectionState.CONNECTED
            updateVpnButtonUI(connectionState)
        } catch (e: Exception) {
            Log.e(TAG, "启动VPN服务失败", e)
            connectionState = ConnectionState.FAILED
            updateVpnButtonUI(connectionState)
            Toast.makeText(context, "启动VPN失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetConnectionState() {
        connectionState = ConnectionState.DISCONNECTED
        updateVpnButtonUI(connectionState)
    }
    
    // ==================== 快速操作方法 ====================
    private fun handleSpeedTest() {
        try {
            Toast.makeText(context, "速度测试功能开发中...", Toast.LENGTH_SHORT).show()
            // TODO: 实现速度测试功能
        } catch (e: Exception) {
            Log.e(TAG, "速度测试失败", e)
            Toast.makeText(context, "速度测试失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }
    
    private fun handleSupport() {
        try {
            Toast.makeText(context, "支持功能开发中...", Toast.LENGTH_SHORT).show()
            // TODO: 跳转到支持页面
        } catch (e: Exception) {
            Log.e(TAG, "跳转支持失败", e)
            Toast.makeText(context, "跳转支持失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 导航方法 ====================
    // 已移除跳转到节点列表的相关方法和调用
    
    // ==================== 错误处理方法 ====================
    private fun handleDataLoadError() {
        _binding?.let {
            it.dataUsedText.text = "加载失败"
            it.dataRemainingText.text = "加载失败"
        }
    }

    private fun showNodeSelectionDialog() {
        val selectedNodeName = sharedPreferences.getString("selected_node", null)
        val dialog = NodeSelectDialogFragment(selectedNodeName) { node ->
            sharedPreferences.edit().putString("selected_node", node.name).apply()
            updateServerSelection() // 立即刷新国旗和节点
        }
        dialog.show(childFragmentManager, "NodeSelectDialog")
    }

    // 获取所有可用节点（可根据你的实际数据源实现）
    private fun getAllAvailableNodes(): List<ClashProxy> {
        // 这里假设你有一个订阅管理器或本地缓存
        return SubscriptionManager.getInstance().getCachedConfig()?.proxies ?: emptyList()
    }

    private fun saveSelectedNode(nodeName: String) {
        sharedPreferences.edit()
            .putString("selected_node", nodeName)
            .apply()
    }
    
    private fun getFlagResId(context: Context, countryCode: String?): Int {
        if (countryCode.isNullOrBlank()) return R.drawable.ic_flag_default
        val resName = "flag_${countryCode.lowercase()}"
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        return if (resId != 0) resId else R.drawable.ic_flag_default
    }
    
    // ==================== 资源清理方法 ====================
    private fun cleanupResources() {
        if (isBound) {
                requireContext().unbindService(serviceConnection)
                isBound = false
        }
    }

    private fun autoFetchSubscriptionNodes() {
        val useSubscription = sharedPreferences.getBoolean("use_subscription", false)
        val subscriptionUrl = sharedPreferences.getString("subscription_url", null)
        if (useSubscription && !subscriptionUrl.isNullOrEmpty()) {
            lifecycleScope.launch {
                SubscriptionManager.getInstance().fetchSubscription(subscriptionUrl)
                // 拉取后可选：通知UI刷新节点（如有需要）
            }
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
}