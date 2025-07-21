package com.byteflow.www.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.Plan
import com.byteflow.www.databinding.ItemPlanBinding

class PlanAdapter(
    private val onPlanClick: (Plan) -> Unit
) : ListAdapter<Plan, PlanAdapter.PlanViewHolder>(PlanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemPlanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlanViewHolder(binding, onPlanClick)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlanViewHolder(
        private val binding: ItemPlanBinding,
        private val onPlanClick: (Plan) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plan: Plan) {
            binding.apply {
                planNameText.text = plan.name
                
                // 格式化价格
                val price = if (plan.monthPrice != null && plan.monthPrice > 0) {
                    val yuan = plan.monthPrice / 100.0
                    "¥${if (yuan % 1 == 0.0) yuan.toInt() else yuan}/月"
                } else {
                    "免费"
                }
                planPriceText.text = price
                
                // 隐藏原价显示（API中没有原价字段）
                planOriginalPriceText.visibility = android.view.View.GONE
                
                // 格式化流量
                val data = if (plan.transferEnable > 0) {
                    val gb = plan.transferEnable / (1024.0 * 1024.0 * 1024.0)
                    if (gb >= 1) {
                        "${String.format("%.1f", gb)}GB"
                    } else {
                        val mb = plan.transferEnable / (1024.0 * 1024.0)
                        "${String.format("%.0f", mb)}MB"
                    }
                } else {
                    "无限制"
                }
                planDataText.text = data
                
                // 隐藏热门标签（API中没有此字段）
                popularBadge.visibility = android.view.View.GONE
                
                // 隐藏当前套餐指示器（需要根据用户状态判断）
                currentPlanIndicator.visibility = android.view.View.GONE
                
                // Setup action button
                actionButton.text = "选择套餐"
                actionButton.isEnabled = true
                actionButton.setBackgroundResource(R.drawable.ios_button_background)
                actionButton.setTextColor(root.context.getColor(R.color.text_white))
                
                // 解析特性列表
                val features = mutableListOf<String>()
                plan.content.split("\n").forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("- ")) {
                        features.add(trimmed.substring(2))
                    }
                }
                
                // 如果没有找到特性，返回基本信息
                if (features.isEmpty()) {
                    features.add("基础功能")
                    features.add("稳定连接")
                    features.add("技术支持")
                }
                
                val featuresText = features.joinToString("\n") { "• $it" }
                planFeaturesText.text = featuresText
                
                // Set click listener
                actionButton.setOnClickListener {
                        onPlanClick(plan)
                }
            }
        }
    }

    class PlanDiffCallback : DiffUtil.ItemCallback<Plan>() {
        override fun areItemsTheSame(oldItem: Plan, newItem: Plan): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Plan, newItem: Plan): Boolean {
            return oldItem == newItem
        }
    }
}