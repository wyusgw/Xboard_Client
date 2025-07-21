package com.byteflow.www

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.byteflow.www.databinding.ActivityMainBinding
import com.byteflow.www.ui.fragments.HomeFragment
import com.byteflow.www.ui.fragments.NodeListFragment
import com.byteflow.www.ui.fragments.PlansFragment
import com.byteflow.www.ui.fragments.ProfileFragment
import com.byteflow.www.utils.SubscriptionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val subscriptionManager = SubscriptionManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化认证管理器
        AuthManager.initializeAuth(this)
        
        // 检查登录状态
        if (!AuthManager.isLoggedIn(this)) {
            // 未登录，跳转到登录界面
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemUI()
        setupBottomNavigation()
        setupFragmentListener()
        
        // Show home fragment by default
        if (savedInstanceState == null) {
            showFragment(HomeFragment(), "HOME")
        }
        
        // 应用启动时异步更新订阅（只有在登录状态下才更新）
        updateSubscriptionOnStartup()
    }
    
    override fun onResume() {
        super.onResume()
        updateBottomNavigationVisibility()
    }

    private fun setupSystemUI() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment(), "HOME")
                    true
                }
                R.id.nav_plans -> {
                    showFragment(PlansFragment(), "PLANS")
                    true
                }
                R.id.nav_profile -> {
                    showFragment(ProfileFragment(), "PROFILE")
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateSubscriptionOnStartup() {
        lifecycleScope.launch {
            try {
                // 自动获取订阅链接
                val subscribeResult = ApiClient.getSubscribeInfo()
                if (subscribeResult.isSuccess) {
                    val subscribeInfo = subscribeResult.getOrNull()
                    val subscriptionUrl = subscribeInfo?.subscribeUrl
                    
                    if (!subscriptionUrl.isNullOrEmpty()) {
                        // 保存订阅链接到SharedPreferences
                        val sharedPreferences = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putString("subscription_url", subscriptionUrl)
                            .putBoolean("use_subscription", true)
                            .apply()
                        
                        // 更新订阅数据
                        subscriptionManager.updateSubscriptionAsync(subscriptionUrl)
                    }
                }
            } catch (e: Exception) {
                // 静默处理，不影响应用启动
            }
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        if (existingFragment != null && existingFragment.isVisible) {
            return
        }

        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_NONE)
            .replace(R.id.fragment_container, fragment, tag)
            .commitNow()

        // 控制底部导航栏显示/隐藏
        updateBottomNavigationVisibility()
    }

    private fun setupFragmentListener() {
        // 监听Fragment变化来控制底部导航栏
        supportFragmentManager.addOnBackStackChangedListener {
            updateBottomNavigationVisibility()
        }
    }
    
    private fun updateBottomNavigationVisibility() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        when (currentFragment) {
            is com.byteflow.www.ui.fragments.SettingsFragment,
            is com.byteflow.www.ui.fragments.NodeListFragment -> {
                binding.bottomNavigation.visibility = View.GONE
            }
            else -> {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
        }
    }
}