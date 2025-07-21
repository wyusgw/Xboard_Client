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

/**
 * 套餐列表Fragment - 重构版本
 * 保持所有原有功能，但采用更模块化的架构
 */
class PlansFragment : Fragment() {
    
    // ==================== 视图绑定 ====================
    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!
    
    // ==================== 数据管理 ====================
    private lateinit var planAdapter: PlanAdapter
    private var plans = listOf<Plan>()

    // ==================== 生命周期方法 ====================
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
        initializeFragment()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // ==================== 初始化方法 ====================
    private fun initializeFragment() {
        initializeRecyclerView()
        loadPlans()
    }

    private fun initializeRecyclerView() {
        planAdapter = PlanAdapter { plan ->
            handlePlanSelection(plan)
        }
        
        binding.plansRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }
    }

    // ==================== 数据加载方法 ====================
    private fun loadPlans() {
        lifecycleScope.launch {
            try {
                val result = ApiClient.getPlans()
                if (result.isSuccess) {
                    handlePlansLoadSuccess(result.getOrNull() ?: emptyList())
                } else {
                    handlePlansLoadError("加载套餐失败")
                }
            } catch (e: Exception) {
                handlePlansLoadError("加载套餐失败: ${e.message}")
            }
        }
    }
    
    private fun handlePlansLoadSuccess(plans: List<Plan>) {
        this.plans = plans
        if (plans.isNotEmpty()) {
            planAdapter.submitList(plans)
        } else {
            showEmptyState("暂无套餐数据")
        }
    }
    
    private fun handlePlansLoadError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
        showEmptyState(message)
    }
    
    // ==================== 套餐操作方法 ====================
    private fun handlePlanSelection(plan: Plan) {
        showPlanSelectionFeedback(plan.name)
        // TODO: 实现套餐升级/订阅逻辑
        // 这里可以添加套餐购买、升级等业务逻辑
    }

    private fun showPlanSelectionFeedback(planName: String) {
        context?.let {
            Toast.makeText(it, "选择了套餐: $planName", Toast.LENGTH_SHORT).show()
    }
    }
    
    // ==================== UI状态管理方法 ====================
    @Suppress("UNUSED_PARAMETER")
    private fun showEmptyState(message: String) {
        // 可以在这里添加空状态UI显示
        // 例如显示一个提示文本或图标
    }
}