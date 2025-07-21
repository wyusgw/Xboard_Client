package com.byteflow.www.ui.fragments

import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.byteflow.www.R
import com.byteflow.www.databinding.FragmentNodeListBinding
import com.byteflow.www.models.ClashProxy
import com.byteflow.www.models.ClashConfig
import com.byteflow.www.service.LeafVpnService
import com.byteflow.www.ui.adapters.NodeAdapter
import com.byteflow.www.utils.SubscriptionManager
import kotlinx.coroutines.launch

/**
 * 节点列表Fragment - 重构版本
 * 保持所有原有功能，但采用更模块化的架构
 */
class NodeListFragment : Fragment() {
    
    // ==================== 视图绑定 ====================
    private var _binding: FragmentNodeListBinding? = null
    private val binding get() = _binding!!
    
    // ==================== 数据管理 ====================
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nodeAdapter: NodeAdapter
    private val subscriptionManager = SubscriptionManager.getInstance()
    private var currentClashConfig: ClashConfig? = null
    private var allNodes = listOf<ClashProxy>() // 保存所有节点，用于搜索
    
    // ==================== 常量定义 ====================
    companion object {
        private const val TAG = "NodeListFragment"
        private const val PREFS_NAME = "vpn_settings"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        private const val KEY_USE_SUBSCRIPTION = "use_subscription"
        private const val KEY_SELECTED_NODE = "selected_node"
    }
    
    // ==================== 生命周期方法 ====================
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNodeListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeFragment()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // ==================== 初始化方法 ====================
    private fun initializeFragment() {
        initializeSharedPreferences()
        initializeRecyclerView()
        setupClickListeners()
        setupSearchView()
        loadNodes()
    }
    
    private fun initializeSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun initializeRecyclerView() {
        nodeAdapter = NodeAdapter(
            onNodeClick = { node -> handleNodeSelection(node) },
            onTestLatency = { node -> handleSingleNodeLatencyTest(node) }
        )
        
        binding.nodesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nodeAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.testAllButton.setOnClickListener {
            handleTestAllNodesLatency()
        }
        
        binding.sortButton.setOnClickListener {
            handleSortNodesByLatency()
        }
    }
    
    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleNodeFiltering(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    // ==================== 节点管理方法 ====================
    private fun loadNodes() {
        val useSubscription = sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION, false)
        val subscriptionUrl = sharedPreferences.getString(KEY_SUBSCRIPTION_URL, "")
        
        if (useSubscription && !subscriptionUrl.isNullOrEmpty()) {
            loadNodesFromSubscription(subscriptionUrl)
        } else {
            loadHardcodedNodes()
        }
    }
    
    private fun loadNodesFromSubscription(subscriptionUrl: String) {
        showLoadingState()
        
        lifecycleScope.launch {
            try {
                val clashConfig = getClashConfig(subscriptionUrl)
                
                if (clashConfig != null) {
                    currentClashConfig = clashConfig
                    val nodes = filterValidNodes(clashConfig.proxies)
                    val selectedNodeName = sharedPreferences.getString(KEY_SELECTED_NODE, "")
                    
                    allNodes = nodes
                    nodeAdapter.updateNodes(nodes, selectedNodeName)
                    
                    hideLoadingState()
                    if (nodes.isEmpty()) {
                        showEmptyState("订阅中没有可用的节点")
                    }
                } else {
                    hideLoadingState()
                    showEmptyState("获取订阅配置失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载节点失败", e)
                hideLoadingState()
                showEmptyState("加载节点失败: ${e.message}")
            }
        }
    }
    
    private suspend fun getClashConfig(subscriptionUrl: String): ClashConfig? {
        // 首先尝试使用缓存的配置
        var clashConfig = subscriptionManager.getCachedConfig()
        
        // 如果没有缓存，才获取订阅
        if (clashConfig == null) {
            Log.d(TAG, "无缓存配置，获取订阅")
            clashConfig = subscriptionManager.fetchSubscription(subscriptionUrl)
        } else {
            Log.d(TAG, "使用缓存的配置")
        }
        
        return clashConfig
    }
    
    private fun filterValidNodes(proxies: List<ClashProxy>): List<ClashProxy> {
        return proxies.filter { 
            (it.type == "ss" || it.type == "ssr") && 
            !it.name.contains("剩余流量", ignoreCase = true) && 
            !it.name.contains("距离", ignoreCase = true) && 
            !it.name.contains("套餐", ignoreCase = true) &&
            !it.name.contains("官网", ignoreCase = true) &&
            !it.name.contains("❤️") &&
            !it.name.contains("特殊时期", ignoreCase = true) &&
            !it.name.contains("速度节点", ignoreCase = true) &&
            !it.name.contains("节点红了", ignoreCase = true) &&
            !it.name.contains("更新", ignoreCase = true) &&
            !it.name.contains("订阅", ignoreCase = true) &&
            !it.name.contains("到期", ignoreCase = true) &&
            !it.name.contains("流量", ignoreCase = true)
        }
    }
    
    private fun loadHardcodedNodes() {
        val hardcodedNode = createHardcodedNode()
        allNodes = listOf(hardcodedNode)
        nodeAdapter.updateNodes(listOf(hardcodedNode), "硬编码节点")
        hideLoadingState()
    }
    
    private fun createHardcodedNode(): ClashProxy {
        return ClashProxy(
            name = "硬编码节点",
            type = "ss",
            server = "154.219.115.154",
            port = 12355,
            cipher = "aes-128-gcm",
            password = "sVbqrHyiDCYq0xeAJW5jSRqmvAxHx7aENNGuX+V6ikM="
        )
    }
    
    // ==================== 节点操作方法 ====================
    private fun handleNodeSelection(node: ClashProxy) {
        saveSelectedNode(node.name)
        updateAdapterSelection(node.name)
        showNodeSelectionFeedback(node.name)
        
        // 如果VPN正在运行，切换节点
        if (LeafVpnService.isVpnRunning) {
            switchVpnNode(node.name)
        }
    }
    
    private fun saveSelectedNode(nodeName: String) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_NODE, nodeName)
            .apply()
    }
    
    private fun updateAdapterSelection(nodeName: String) {
        nodeAdapter.updateSelectedNode(nodeName)
    }
    
    private fun showNodeSelectionFeedback(nodeName: String) {
        Toast.makeText(context, "已选择节点: $nodeName", Toast.LENGTH_SHORT).show()
    }
    
    private fun switchVpnNode(nodeName: String) {
        try {
            val intent = Intent(requireContext(), LeafVpnService::class.java).apply {
                action = LeafVpnService.ACTION_SWITCH_NODE
                putExtra(LeafVpnService.EXTRA_NODE_NAME, nodeName)
            }
            requireContext().startService(intent)
            Toast.makeText(context, "正在切换到节点: $nodeName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "切换节点失败", e)
            Toast.makeText(context, "切换节点失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 延迟测试方法 ====================
    private fun handleSingleNodeLatencyTest(node: ClashProxy) {
        lifecycleScope.launch {
            try {
                subscriptionManager.testNodeLatency(node)
                nodeAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e(TAG, "测试节点延迟失败: ${node.name}", e)
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleTestAllNodesLatency() {
        currentClashConfig?.let { config ->
            // 直接移除 validNodes 赋值
            lifecycleScope.launch {
                try {
                    subscriptionManager.testAllNodesLatency(config) { current, total ->
                        // 可以在这里更新进度
                        Log.d(TAG, "延迟测试进度: $current/$total")
                    }
                    nodeAdapter.notifyDataSetChanged()
                    Toast.makeText(context, "延迟测试完成", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "批量测试延迟失败", e)
                    Toast.makeText(context, "批量测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(context, "没有可测试的节点", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 排序和过滤方法 ====================
    private fun handleSortNodesByLatency() {
        nodeAdapter.sortNodesByLatency()
        Toast.makeText(context, "已按延迟排序", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleNodeFiltering(query: String) {
        val filteredNodes = if (query.isBlank()) {
            allNodes
        } else {
            allNodes.filter { node ->
                node.name.contains(query, ignoreCase = true) ||
                node.server.contains(query, ignoreCase = true)
            }
        }
        
        val selectedNodeName = sharedPreferences.getString(KEY_SELECTED_NODE, "")
        nodeAdapter.updateNodes(filteredNodes, selectedNodeName)
    }
    
    // ==================== UI状态管理方法 ====================
    private fun showLoadingState() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
    }
    
    private fun hideLoadingState() {
        binding.loadingProgress.visibility = View.GONE
    }
    
    private fun showEmptyState(message: String) {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = message
    }
}