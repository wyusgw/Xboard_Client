package com.byteflow.www.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.databinding.ItemNodeBinding
import com.byteflow.www.models.ClashProxy
import com.byteflow.www.models.ClashConfig
import com.byteflow.www.utils.SubscriptionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NodeSelectDialogFragment(
    private val selectedNodeName: String?,
    private val onNodeSelected: (ClashProxy) -> Unit
) : DialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NodeAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var testAllButton: Button
    private lateinit var searchEdit: EditText
    private val subscriptionManager = SubscriptionManager.getInstance()
    private var isTestingAll = false
    private var nodes: List<ClashProxy> = emptyList()

    private fun getAllAvailableNodes(): List<ClashProxy> {
        return SubscriptionManager.getInstance().getCachedConfig()?.proxies ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.dialog_node_select, container, false) as FrameLayout
        val title = root.findViewById<TextView>(R.id.dialog_title)
        recyclerView = root.findViewById(R.id.node_recycler)
        progressBar = root.findViewById(R.id.test_progress_bar)
        progressText = root.findViewById(R.id.test_progress_text)
        testAllButton = root.findViewById(R.id.test_all_button)
        searchEdit = root.findViewById(R.id.node_search_edit)

        title.text = "选择节点"
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        refreshNodeList()

        testAllButton.setOnClickListener {
            if (!isTestingAll) {
                testAllNodesLatency()
            }
        }
        // 已移除重试失败节点按钮相关事件
        updateProgressUI(0, nodes.size)

        // 搜索框监听
        searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNodes(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        return root
    }

    private var allNodes: List<ClashProxy> = emptyList()
    private fun refreshNodeList() {
        allNodes = getAllAvailableNodes()
        nodes = allNodes
        adapter = NodeAdapter(
            onNodeClick = { node ->
                if (!isTestingAll) {
                    onNodeSelected(node)
                    dismiss()
                }
            },
            onTestLatency = { node ->
                lifecycleScope.launch {
                    // 标记为测速中并刷新UI
                    node.isTestingLatency = true
                    adapter.notifyLatencyUpdated(node.name)
                    subscriptionManager.testNodeLatency(node)
                    withContext(Dispatchers.Main) {
                        node.isTestingLatency = false
                        adapter.notifyLatencyUpdated(node.name)
                    }
                }
            },
            showSelectedIcon = true,
            selectedNodeName = selectedNodeName
        )
        adapter.updateNodes(nodes.sortedBy { it.latency.takeIf { l -> l > 0 } ?: Int.MAX_VALUE }, selectedNodeName)
        recyclerView.adapter = adapter
    }

    private fun filterNodes(keyword: String) {
        nodes = if (keyword.isBlank()) {
            allNodes
        } else {
            allNodes.filter {
                it.name.contains(keyword, ignoreCase = true)
            }
        }
        adapter.updateNodes(nodes.sortedBy { it.latency.takeIf { l -> l > 0 } ?: Int.MAX_VALUE }, selectedNodeName)
    }

    private fun testAllNodesLatency() {
        isTestingAll = true
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        testAllButton.isEnabled = false
        testAllButton.text = "正在测试..."
        val clashConfig = ClashConfig(nodes, null, null)
        lifecycleScope.launch {
            subscriptionManager.testAllNodesLatency(
                clashConfig,
                onProgress = { current, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateProgressUI(current, total)
                        adapter.notifyDataSetChanged()
                    }
                }
            )
            isTestingAll = false
            testAllButton.isEnabled = true
            testAllButton.text = "全部测试延迟"
            updateProgressUI(nodes.size, nodes.size)
            // 自动滚动到第一个高延迟或未测试节点
            scrollToFirstBadNode()
            // 进度条渐隐动画
            progressBar.animate().alpha(0f).setDuration(600).withEndAction {
                progressBar.visibility = View.GONE
                progressBar.alpha = 1f
            }.start()
        }
    }

    // 滚动到第一个高延迟或未测试节点
    private fun scrollToFirstBadNode() {
        val index = nodes.indexOfFirst { it.latency == -1 || it.latency == 0 || it.latency > 300 }
        if (index >= 0) {
            recyclerView.smoothScrollToPosition(index)
        }
    }

    // 批量重试失败节点
    private fun retryFailedNodes() {
        val failedNodes = nodes.filter { it.latency == -1 || it.latency == 0 || it.latency > 300 }
        if (failedNodes.isEmpty()) {
            Toast.makeText(requireContext(), "没有需要重试的节点", Toast.LENGTH_SHORT).show()
            return
        }
        isTestingAll = true
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        testAllButton.isEnabled = false
        testAllButton.text = "正在重试..."
        val clashConfig = ClashConfig(failedNodes, null, null)
        lifecycleScope.launch {
            subscriptionManager.testAllNodesLatency(
                clashConfig,
                onProgress = { current, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateProgressUI(current, total)
                        adapter.notifyDataSetChanged()
                    }
                }
            )
            isTestingAll = false
            testAllButton.isEnabled = true
            testAllButton.text = "全部测试延迟"
            updateProgressUI(nodes.size, nodes.size)
            scrollToFirstBadNode()
            progressBar.animate().alpha(0f).setDuration(600).withEndAction {
                progressBar.visibility = View.GONE
                progressBar.alpha = 1f
            }.start()
        }
    }

    private fun updateProgressUI(current: Int, total: Int) {
        progressBar.max = total
        progressBar.progress = current
        if (current < total) {
            progressText.text = "已完成 $current/$total"
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
        } else {
            progressText.text = "全部测试完成"
            progressBar.visibility = View.GONE
            progressText.visibility = View.VISIBLE
        }
    }

    class NodeAdapter(
        private val onNodeClick: (ClashProxy) -> Unit,
        private val onTestLatency: (ClashProxy) -> Unit,
        private val showSelectedIcon: Boolean,
        private var selectedNodeName: String?
    ) : RecyclerView.Adapter<NodeAdapter.NodeViewHolder>() {
        private var nodes = listOf<ClashProxy>()
        private val subscriptionManager = SubscriptionManager.getInstance()

        fun updateNodes(newNodes: List<ClashProxy>, selectedName: String?) {
            nodes = newNodes
            selectedNodeName = selectedName
            notifyDataSetChanged()
        }

        fun notifyLatencyUpdated(nodeName: String) {
            val index = nodes.indexOfFirst { it.name == nodeName }
            if (index != -1) notifyItemChanged(index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
            val binding = ItemNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return NodeViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
            holder.bind(nodes[position])
        }

        override fun getItemCount() = nodes.size

        inner class NodeViewHolder(private val binding: ItemNodeBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(node: ClashProxy) {
                binding.nodeNameText.text = node.name
                binding.nodeRegionText.text = parseRegion(node.name)
                // 设置信号格图标
                val signalRes = when {
                    node.isTestingLatency -> R.drawable.ic_signal_0
                    node.latency == -1 -> R.drawable.ic_signal_0
                    node.latency == 0 -> R.drawable.ic_signal_0
                    node.latency <= 100 -> R.drawable.ic_signal_4
                    node.latency <= 200 -> R.drawable.ic_signal_3
                    node.latency <= 300 -> R.drawable.ic_signal_2
                    else -> R.drawable.ic_signal_1
                }
                binding.nodeSignalIcon.setImageResource(signalRes)
                // 移除测试按钮
                binding.root.setOnClickListener {
                    onNodeClick(node)
                }
                // 选中高亮（只用描边，不显示勾勾）
                val cardView = binding.root
                if (showSelectedIcon && node.name == selectedNodeName) {
                    cardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_blue)
                    cardView.strokeWidth = 4
                } else {
                    cardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.card_stroke)
                    cardView.strokeWidth = 1
                }
            }
            private fun parseRegion(nodeName: String): String {
                return when {
                    nodeName.contains("香港") || nodeName.contains("HK") -> "🇭🇰 香港"
                    nodeName.contains("台湾") || nodeName.contains("TW") -> "🇹🇼 台湾"
                    nodeName.contains("新加坡") || nodeName.contains("SG") -> "🇸🇬 新加坡"
                    nodeName.contains("日本") || nodeName.contains("JP") -> "🇯🇵 日本"
                    nodeName.contains("美国") || nodeName.contains("US") -> "🇺🇸 美国"
                    nodeName.contains("韩国") || nodeName.contains("KR") -> "🇰🇷 韩国"
                    nodeName.contains("马来西亚") || nodeName.contains("MY") -> "🇲🇾 马来西亚"
                    nodeName.contains("泰国") || nodeName.contains("TH") -> "🇹🇭 泰国"
                    nodeName.contains("菲律宾") || nodeName.contains("PH") -> "🇵🇭 菲律宾"
                    nodeName.contains("越南") || nodeName.contains("VN") -> "🇻🇳 越南"
                    nodeName.contains("印尼") || nodeName.contains("ID") -> "🇮🇩 印尼"
                    nodeName.contains("英国") || nodeName.contains("UK") -> "🇬🇧 英国"
                    nodeName.contains("德国") || nodeName.contains("DE") -> "🇩🇪 德国"
                    nodeName.contains("法国") || nodeName.contains("FR") -> "🇫🇷 法国"
                    nodeName.contains("土耳其") || nodeName.contains("TR") -> "🇹🇷 土耳其"
                    nodeName.contains("巴西") || nodeName.contains("BR") -> "🇧🇷 巴西"
                    nodeName.contains("阿根廷") || nodeName.contains("AR") -> "🇦🇷 阿根廷"
                    nodeName.contains("硬编码") -> "🔧 硬编码"
                    else -> "🌐 其他"
                }
            }
        }
    }
} 