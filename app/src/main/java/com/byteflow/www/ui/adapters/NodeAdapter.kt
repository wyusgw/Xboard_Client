package com.byteflow.www.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.databinding.ItemNodeBinding
import com.byteflow.www.models.ClashProxy
import com.byteflow.www.utils.SubscriptionManager

class NodeAdapter(
    private val onNodeClick: (ClashProxy) -> Unit,
    private val onTestLatency: (ClashProxy) -> Unit
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
            binding.nodeNameText.text = node.name
            binding.nodeInfoText.text = "${node.server}:${node.port}"
            
            // 设置加密方法显示
            binding.nodeMethodText.text = node.cipher ?: "aes-128-gcm"
            
            // 解析地区信息
            val region = parseRegion(node.name)
            binding.nodeRegionText.text = region
            
            // 设置延迟显示
            binding.nodeLatencyText.text = subscriptionManager.getLatencyText(node)
            val latencyColor = subscriptionManager.getLatencyColor(node)
            binding.nodeLatencyText.setTextColor(
                ContextCompat.getColor(binding.root.context, latencyColor)
            )
            
            // 设置测试按钮
            binding.nodeTestButton.setOnClickListener {
                onTestLatency(node)
            }
            
            // 设置选中状态
            val isSelected = node.name == selectedNodeName
            binding.nodeSelectedIcon.visibility = if (isSelected) 
                android.view.View.VISIBLE else android.view.View.GONE
                
            // 设置选中状态的背景色
            val backgroundColor = if (isSelected) {
                ContextCompat.getColor(binding.root.context, R.color.primary_blue_light)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.background_primary)
            }
            binding.nodeCard.setCardBackgroundColor(backgroundColor)
            
            // 点击事件
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
    }
}