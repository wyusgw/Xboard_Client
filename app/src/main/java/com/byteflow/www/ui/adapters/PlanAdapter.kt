package com.byteflow.www.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.byteflow.www.R
import com.byteflow.www.data.models.Plan
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
                planPriceText.text = plan.price
                
                // 处理可空的originalPrice
                if (plan.originalPrice.isNullOrEmpty()) {
                    planOriginalPriceText.visibility = android.view.View.GONE
                } else {
                    planOriginalPriceText.visibility = android.view.View.VISIBLE
                    planOriginalPriceText.text = plan.originalPrice
                    planOriginalPriceText.paintFlags = 
                        planOriginalPriceText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                }
                
                planDataText.text = plan.data
                
                // Show popular badge
                popularBadge.visibility = if (plan.isPopular) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                
                // Show current plan indicator
                currentPlanIndicator.visibility = if (plan.isCurrent) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
                
                // Setup action button
                actionButton.text = when {
                    plan.isCurrent -> "当前套餐"
                    else -> "选择套餐"
                }
                
                actionButton.isEnabled = !plan.isCurrent
                
                if (plan.isCurrent) {
                    actionButton.setBackgroundResource(R.drawable.ios_button_outline_background)
                    actionButton.setTextColor(root.context.getColor(R.color.text_secondary))
                } else {
                    actionButton.setBackgroundResource(R.drawable.ios_button_background)
                    actionButton.setTextColor(root.context.getColor(R.color.text_white))
                }
                
                // Build features list
                val featuresText = plan.features.joinToString("\n") { "• $it" }
                planFeaturesText.text = featuresText
                
                // Set click listener
                actionButton.setOnClickListener {
                    if (!plan.isCurrent) {
                        onPlanClick(plan)
                    }
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