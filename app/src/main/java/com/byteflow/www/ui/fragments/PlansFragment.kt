package com.byteflow.www.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.byteflow.www.ApiClient
import com.byteflow.www.Plan
import com.byteflow.www.databinding.FragmentPlansBinding
import com.byteflow.www.ui.adapters.PlanAdapter
import kotlinx.coroutines.launch

class PlansFragment : Fragment() {
    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var planAdapter: PlanAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadPlans()
    }

    private fun setupRecyclerView() {
        planAdapter = PlanAdapter { plan ->
            // Handle plan selection
            onPlanSelected(plan)
        }
        
        binding.plansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }
    }

    private fun loadPlans() {
        lifecycleScope.launch {
            try {
                val result = ApiClient.getPlans()
                if (result.isSuccess) {
                    val plans = result.getOrNull() ?: emptyList()
                    if (plans.isNotEmpty()) {
                        // 转换为适配器需要的数据格式
                        val displayPlans = plans.map { apiPlan ->
                            com.byteflow.www.data.models.Plan(
                                id = apiPlan.id.toString(),
                                name = apiPlan.name,
                                price = formatPrice(apiPlan.monthPrice),
                                originalPrice = null, // 现在支持null值
                                data = formatData(apiPlan.transferEnable),
                                features = parseFeatures(apiPlan.content),
                                isPopular = false, // 可以根据实际需求设置
                                isCurrent = false // 可以根据用户当前套餐设置
                            )
                        }
                        planAdapter.submitList(displayPlans)
                    } else {
                        Toast.makeText(context, "暂无套餐数据", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "加载套餐失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载套餐失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun formatPrice(price: Int?): String {
        return if (price != null && price > 0) {
            val yuan = price / 100.0
            "¥${if (yuan % 1 == 0.0) yuan.toInt() else yuan}/月"
        } else {
            "免费"
        }
    }
    
    private fun formatData(transferEnable: Long): String {
        return if (transferEnable > 0) {
            // 转换字节为GB，保留2位小数
            val gb = transferEnable / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1) {
                "${String.format("%.1f", gb)}GB"
            } else {
                val mb = transferEnable / (1024.0 * 1024.0)
                "${String.format("%.0f", mb)}MB"
            }
        } else {
            "无限制"
        }
    }
    
    private fun parseFeatures(content: String): List<String> {
        // 解析Markdown格式的内容，提取特性列表
        val features = mutableListOf<String>()
        
        // 简单解析，提取以 "- " 开头的行
        content.split("\n").forEach { line ->
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
        
        return features
    }

    private fun onPlanSelected(plan: com.byteflow.www.data.models.Plan) {
        // TODO: Handle plan upgrade/subscription
        Toast.makeText(context, "选择了套餐: ${plan.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}