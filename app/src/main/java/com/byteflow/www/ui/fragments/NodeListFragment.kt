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

class NodeListFragment : Fragment() {
    private var _binding: FragmentNodeListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nodeAdapter: NodeAdapter
    private val subscriptionManager = SubscriptionManager.getInstance()
    private var currentClashConfig: ClashConfig? = null
    private var allNodes = listOf<ClashProxy>() // 保存所有节点，用于搜索
    
    companion object {
        private const val TAG = "NodeListFragment"
        private const val PREFS_NAME = "vpn_settings"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        private const val KEY_USE_SUBSCRIPTION = "use_subscription"
        private const val KEY_SELECTED_NODE = "selected_node"
    }
    
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
        
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupRecyclerView()
        setupTestAllButton()
        setupSortButton()
        setupSearchView()
        loadNodes()
    }
    
    private fun setupRecyclerView() {
        nodeAdapter = NodeAdapter(
            onNodeClick = { node -> selectNode(node) },
            onTestLatency = { node -> testSingleNodeLatency(node) }
        )
        
        binding.nodesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nodeAdapter
        }
    }
    
    private fun setupTestAllButton() {
        binding.testAllButton.setOnClickListener {
            testAllNodesLatency()
        }
    }
    
    private fun setupSortButton() {
        binding.sortButton.setOnClickListener {
            nodeAdapter.sortNodesByLatency()
            Toast.makeText(context, "已按延迟排序", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNodes(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun filterNodes(query: String) {
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
    
    private fun loadNodes() {
        val useSubscription = sharedPreferences.getBoolean(KEY_USE_SUBSCRIPTION, false)
        val subscriptionUrl = sharedPreferences.getString(KEY_SUBSCRIPTION_URL, "")
        
        if (useSubscription && !subscriptionUrl.isNullOrEmpty()) {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            
            lifecycleScope.launch {
                try {
                    // 首先尝试使用缓存的配置
                    var clashConfig = subscriptionManager.getCachedConfig()
                    
                    // 如果没有缓存，才获取订阅
                    if (clashConfig == null) {
                        Log.d(TAG, "无缓存配置，获取订阅")
                        clashConfig = subscriptionManager.fetchSubscription(subscriptionUrl)
                    } else {
                        Log.d(TAG, "使用缓存的配置")
                    }
                    
                    if (clashConfig != null) {
                        currentClashConfig = clashConfig // 保存配置引用
                        
                        val nodes = clashConfig.proxies.filter { 
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
                        
                        val selectedNodeName = sharedPreferences.getString(KEY_SELECTED_NODE, "")
                        
                        // 保存所有节点用于搜索
                        allNodes = nodes
                        nodeAdapter.updateNodes(nodes, selectedNodeName)
                        
                        binding.loadingProgress.visibility = View.GONE
                        if (nodes.isEmpty()) {
                            showEmptyState("订阅中没有可用的节点")
                        }
                    } else {
                        binding.loadingProgress.visibility = View.GONE
                        showEmptyState("获取订阅配置失败")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载节点失败", e)
                    binding.loadingProgress.visibility = View.GONE
                    showEmptyState("加载节点失败: ${e.message}")
                }
            }
        } else {
            // 硬编码模式，显示单个节点
            val hardcodedNode = ClashProxy(
                name = "硬编码节点",
                type = "ss",
                server = "154.219.115.154",
                port = 12355,
                cipher = "aes-128-gcm",
                password = "sVbqrHyiDCYq0xeAJW5jSRqmvAxHx7aENNGuX+V6ikM="
            )
            allNodes = listOf(hardcodedNode) // 保存硬编码节点用于搜索
            nodeAdapter.updateNodes(listOf(hardcodedNode), "硬编码节点")
            binding.loadingProgress.visibility = View.GONE
        }
    }
    
    private fun selectNode(node: ClashProxy) {
        // 保存选择的节点
        sharedPreferences.edit()
            .putString(KEY_SELECTED_NODE, node.name)
            .apply()
        
        // 更新适配器选择状态
        nodeAdapter.updateSelectedNode(node.name)
        
        Toast.makeText(context, "已选择: ${node.name}", Toast.LENGTH_SHORT).show()
        
        // 如果VPN正在运行，进行热切换
        if (LeafVpnService.isVpnRunning) {
            val intent = Intent(requireContext(), LeafVpnService::class.java)
            intent.action = LeafVpnService.ACTION_SWITCH_NODE
            intent.putExtra(LeafVpnService.EXTRA_NODE_NAME, node.name)
            requireContext().startService(intent)
            
            Toast.makeText(context, "正在切换到: ${node.name}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEmptyState(message: String) {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = message
    }
    
    private fun testSingleNodeLatency(node: ClashProxy) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始测试单个节点延迟: ${node.name}")
                
                // 更新UI显示测试状态
                nodeAdapter.notifyLatencyUpdated(node.name)
                
                // 执行延迟测试
                val latency = subscriptionManager.testNodeLatency(node)
                
                // 更新UI显示测试结果
                nodeAdapter.notifyLatencyUpdated(node.name)
                
                val resultMessage = when {
                    latency > 0 -> "节点 ${node.name} 延迟: ${latency}ms"
                    latency == 0 -> "节点 ${node.name} 连接超时"
                    else -> "节点 ${node.name} 测试失败"
                }
                
                Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "测试单个节点延迟失败", e)
                Toast.makeText(context, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun testAllNodesLatency() {
        val clashConfig = currentClashConfig
        if (clashConfig == null) {
            Toast.makeText(context, "没有可用的节点配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始批量测试节点延迟")
                
                // 禁用测试按钮
                binding.testAllButton.isEnabled = false
                binding.testAllText.text = "测试中..."
                
                // 执行批量测试
                subscriptionManager.testAllNodesLatency(clashConfig) { current, total ->
                    // 更新进度
                    lifecycleScope.launch {
                        binding.testAllText.text = "测试中 $current/$total"
                        nodeAdapter.notifyDataSetChanged()
                    }
                }
                
                // 恢复按钮状态
                binding.testAllButton.isEnabled = true
                binding.testAllText.text = "测试全部"
                
                // 更新所有节点显示
                nodeAdapter.notifyDataSetChanged()
                
                Toast.makeText(context, "延迟测试完成", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "批量测试节点延迟失败", e)
                
                // 恢复按钮状态
                binding.testAllButton.isEnabled = true
                binding.testAllText.text = "测试全部"
                
                Toast.makeText(context, "批量测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}