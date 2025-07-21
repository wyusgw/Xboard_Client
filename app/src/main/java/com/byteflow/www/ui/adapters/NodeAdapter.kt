package com.byteflow.www.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.databinding.ItemNodeBinding
import com.byteflow.www.models.ClashProxy
import com.byteflow.www.utils.SubscriptionManager
import android.view.View

class NodeAdapter(
    private val onNodeClick: (ClashProxy) -> Unit,
    private val onTestLatency: (ClashProxy) -> Unit,
    private val showSelectedIcon: Boolean = false // 新增参数，默认false
) : RecyclerView.Adapter<NodeAdapter.NodeViewHolder>() {
    
    private var nodes = listOf<ClashProxy>()
    private var selectedNodeName = ""
    private val subscriptionManager = SubscriptionManager.getInstance()
    
    fun updateNodes(newNodes: List<ClashProxy>, selectedName: String?) {
        nodes = newNodes
        selectedNodeName = selectedName ?: ""
        notifyDataSetChanged()
    }
    
    fun updateSelectedNode(nodeName: String) {
        selectedNodeName = nodeName
        notifyDataSetChanged()
    }
    
    fun sortNodesByLatency() {
        nodes = nodes.sortedWith(compareBy<ClashProxy> { 
            when (it.latency) {
                -1 -> Int.MAX_VALUE // 未测试的排到最后
                0 -> Int.MAX_VALUE - 1 // 超时的排到倒数第二
                else -> it.latency // 正常延迟按数值排序
            }
        })
        notifyDataSetChanged()
    }
    
    fun notifyLatencyUpdated(nodeName: String) {
        val index = nodes.indexOfFirst { it.name == nodeName }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NodeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(nodes[position])
    }
    
    override fun getItemCount() = nodes.size
    
    inner class NodeViewHolder(
        private val binding: ItemNodeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(node: ClashProxy) {
            // 设置国旗和地区
            val region = parseRegion(node.name)
            val flagResId = getFlagResId(region)
            // 直接使用 flagResId，无需判断 != null
            binding.nodeRegionText.setCompoundDrawablesWithIntrinsicBounds(flagResId, 0, 0, 0)
            binding.nodeRegionText.compoundDrawablePadding = 6
            binding.nodeRegionText.text = region.removePrefix("🇭🇰 ").removePrefix("🇨🇳 ").removePrefix("🇺🇸 ").removePrefix("🇲🇾 ").removePrefix("🇫🇷 ")
            // 设置节点名加粗
            binding.nodeNameText.text = node.name
            binding.nodeNameText.setTypeface(null, android.graphics.Typeface.BOLD)
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
            // 已移除测速按钮相关逻辑
            // 选中高亮（只用描边，不显示勾勾）
            val cardView = binding.root
            if (showSelectedIcon && node.name == selectedNodeName) {
                cardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_blue)
                cardView.strokeWidth = 4
            } else {
                cardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.card_stroke)
                cardView.strokeWidth = 1
            }
            binding.root.setOnClickListener {
                onNodeClick(node)
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

        private fun getFlagResId(region: String): Int {
            return when {
                region.contains("香港") -> R.drawable.flag_hk
                region.contains("台湾") -> R.drawable.flag_tw
                region.contains("日本") -> R.drawable.flag_jp
                region.contains("美国") -> R.drawable.flag_us
                region.contains("韩国") -> R.drawable.flag_kr
                region.contains("中国") -> R.drawable.flag_cn
                // 其它常见国家名全部用默认国旗
                region.contains("马来西亚") -> R.drawable.ic_flag_default
                region.contains("新加坡") -> R.drawable.ic_flag_default
                region.contains("泰国") -> R.drawable.ic_flag_default
                region.contains("菲律宾") -> R.drawable.ic_flag_default
                region.contains("越南") -> R.drawable.ic_flag_default
                region.contains("印尼") -> R.drawable.ic_flag_default
                region.contains("英国") -> R.drawable.ic_flag_default
                region.contains("德国") -> R.drawable.ic_flag_default
                region.contains("法国") -> R.drawable.ic_flag_default
                region.contains("土耳其") -> R.drawable.ic_flag_default
                region.contains("巴西") -> R.drawable.ic_flag_default
                region.contains("阿根廷") -> R.drawable.ic_flag_default
                else -> R.drawable.ic_flag_default
            }
        }
    }
}