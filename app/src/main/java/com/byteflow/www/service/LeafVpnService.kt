package com.byteflow.www.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.byteflow.www.R
import com.byteflow.www.models.ClashConfig
import com.byteflow.www.models.LeafConfig
import com.byteflow.www.utils.SubscriptionManager
import com.google.gson.Gson
import kotlinx.coroutines.*

class LeafVpnService : VpnService() {
    
    companion object {
        private const val TAG = "LeafVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_channel"
        const val ACTION_START_VPN = "start_vpn"
        const val ACTION_STOP_VPN = "stop_vpn"
        const val ACTION_SWITCH_NODE = "switch_node"
        const val EXTRA_SUBSCRIPTION_URL = "subscription_url"
        const val EXTRA_NODE_NAME = "node_name"
        
        var isVpnRunning = false
            private set
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var leafInstanceId: Long = 0
    private val subscriptionManager = SubscriptionManager.getInstance()
    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentClashConfig: ClashConfig? = null
    
    // JNI 方法声明
    external fun startLeaf(configJson: String, tunFd: Int): Long
    external fun stopLeaf(instanceId: Long)
    external fun isLeafRunning(instanceId: Long): Boolean
    
    init {
        try {
            System.loadLibrary("leaf")
            System.loadLibrary("leaf_jni")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "加载库失败", e)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN服务已创建")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> {
                val subscriptionUrl = intent.getStringExtra(EXTRA_SUBSCRIPTION_URL)
                if (subscriptionUrl != null) {
                    startVpnWithSubscription(subscriptionUrl)
                } else {
                    startVpn()  // 使用硬编码配置
                }
            }
            ACTION_STOP_VPN -> {
                stopVpn()
            }
            ACTION_SWITCH_NODE -> {
                val nodeName = intent.getStringExtra(EXTRA_NODE_NAME)
                if (nodeName != null) {
                    switchNode(nodeName)
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun startVpnWithSubscription(subscriptionUrl: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "使用订阅链接启动VPN: $subscriptionUrl")
                
                val clashConfig = subscriptionManager.fetchSubscription(subscriptionUrl)
                if (clashConfig == null) {
                    Log.e(TAG, "获取订阅配置失败，使用硬编码配置")
                    startVpn() // 回退到硬编码配置
                    return@launch
                }
                
                // 保存当前配置
                currentClashConfig = clashConfig
                
                // 创建VPN接口
                val builder = Builder()
                    .setSession("Leaf VPN")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("8.8.4.4")
                    .setMtu(1500)
                    .setBlocking(false)
                    // 排除当前应用，防止VPN流量回环
                    .addDisallowedApplication(packageName)
                
                vpnInterface = builder.establish()
                
                if (vpnInterface != null) {
                    val fd = vpnInterface!!.fd
                    Log.d(TAG, "VPN接口创建成功，FD: $fd")
                    
                    // 转换为 Leaf 配置
                    val selectedNodeName = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                        .getString("selected_node", "")
                    
                    // 预解析域名
                    subscriptionManager.preResolveDomains(clashConfig)
                    
                    val leafConfig = subscriptionManager.convertToLeaf(clashConfig, fd, selectedNodeName)
                    val configJson = Gson().toJson(leafConfig)
                    
                    Log.i(TAG, "Leaf配置: $configJson")
                    
                    // 启动 Leaf 实例
                    val instanceId = startLeaf(configJson, fd)
                    
                    if (instanceId > 0) {
                        leafInstanceId = instanceId
                        isVpnRunning = true
                        startForeground(NOTIFICATION_ID, createNotification("VPN已连接 (订阅模式)"))
                        Log.d(TAG, "VPN启动成功，实例ID: $leafInstanceId")
                    } else {
                        Log.e(TAG, "Leaf启动失败")
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "VPN接口创建失败")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "VPN启动异常", e)
                stopSelf()
            }
        }
    }
    
    private fun startVpn() {
        try {
            // 创建VPN接口
            val builder = Builder()
                .setSession("Leaf VPN")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
                .setBlocking(false)
                // 排除当前应用，防止VPN流量回环
                .addDisallowedApplication(packageName)
            
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                Log.d(TAG, "VPN接口创建成功，FD: $fd")
                
                // 创建Leaf配置
                val config = createLeafConfig(fd)
                Log.i(TAG, "Leaf配置: $config")
                
                // 启动Leaf实例
                val instanceId = startLeaf(config, fd)
                
                if (instanceId > 0) {
                    leafInstanceId = instanceId
                    isVpnRunning = true
                    startForeground(NOTIFICATION_ID, createNotification("VPN已连接 (硬编码模式)"))
                    Log.d(TAG, "VPN启动成功，实例ID: $leafInstanceId")
                } else {
                    Log.e(TAG, "Leaf启动失败")
                    stopSelf()
                }
            } else {
                Log.e(TAG, "VPN接口创建失败")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN启动异常", e)
            stopSelf()
        }
    }
    
    private fun switchNode(nodeName: String) {
        if (!isVpnRunning) {
            Log.w(TAG, "VPN未运行，无法切换节点")
            return
        }
        
        val clashConfig = currentClashConfig
        if (clashConfig == null) {
            Log.w(TAG, "没有可用配置，无法切换节点")
            return
        }
        
        serviceScope.launch {
            try {
                Log.d(TAG, "正在切换到节点: $nodeName")
                
                if (vpnInterface != null) {
                    val fd = vpnInterface!!.fd
                    
                    // 停止当前Leaf实例
                    if (leafInstanceId > 0) {
                        Log.d(TAG, "停止当前Leaf实例: $leafInstanceId")
                        stopLeaf(leafInstanceId)
                        leafInstanceId = 0
                        
                        // 等待实例完全停止
                        var retryCount = 0
                        while (retryCount < 5) {
                            Thread.sleep(200)
                            try {
                                if (!isLeafRunning(leafInstanceId)) {
                                    break
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "检查实例状态失败，假设已停止", e)
                                break
                            }
                            retryCount++
                        }
                        Log.d(TAG, "实例停止完成，重试次数: $retryCount")
                    }
                    
                    // 使用新节点创建配置
                    subscriptionManager.preResolveDomains(clashConfig)
                    val leafConfig = subscriptionManager.convertToLeaf(clashConfig, fd, nodeName)
                    val configJson = Gson().toJson(leafConfig)
                    
                    Log.i(TAG, "切换节点Leaf配置: $configJson")
                    
                    // 启动新的Leaf实例
                    val instanceId = startLeaf(configJson, fd)
                    
                    if (instanceId > 0) {
                        leafInstanceId = instanceId
                        
                        // 更新通知
                        val notification = createNotification("VPN已连接 - $nodeName")
                        startForeground(NOTIFICATION_ID, notification)
                        
                        Log.d(TAG, "节点切换成功: $nodeName")
                    } else {
                        Log.e(TAG, "节点切换失败，Leaf启动失败")
                    }
                } else {
                    Log.e(TAG, "VPN接口无效，无法切换节点")
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换节点异常", e)
            }
        }
    }
    
    private fun stopVpn() {
        serviceScope.launch {
            try {
                Log.d(TAG, "正在停止VPN")
                
                // 先停止前台服务
                stopForeground(STOP_FOREGROUND_REMOVE)
                
                // 关键改变：先关闭VPN接口，再停止Leaf实例
                vpnInterface?.let { vpnInterface ->
                    try {
                        Log.d(TAG, "准备关闭VPN接口")
                        vpnInterface.close()
                        Log.d(TAG, "VPN接口已关闭")
                    } catch (e: Exception) {
                        Log.w(TAG, "关闭VPN接口时出错", e)
                    }
                }
                vpnInterface = null
                
                // 等待VPN接口完全关闭
                delay(500)
                
                // 然后停止Leaf实例
                if (leafInstanceId > 0) {
                    Log.d(TAG, "停止Leaf实例: $leafInstanceId")
                    try {
                        withContext(Dispatchers.IO) {
                            stopLeaf(leafInstanceId)
                        }
                        leafInstanceId = 0
                        Log.d(TAG, "Leaf实例已停止")
                    } catch (e: Exception) {
                        Log.w(TAG, "停止Leaf实例时出错", e)
                        leafInstanceId = 0
                    }
                }
                
                isVpnRunning = false
                stopSelf()
                
                Log.d(TAG, "VPN已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止VPN异常", e)
                // 强制重置状态
                isVpnRunning = false
                leafInstanceId = 0
                vpnInterface = null
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN服务销毁")
        serviceScope.cancel()
        
        if (isVpnRunning) {
            // 同步执行关闭操作，避免异步问题
            try {
                // 先关闭VPN接口
                vpnInterface?.close()
                vpnInterface = null
                
                // 再停止Leaf实例
                if (leafInstanceId > 0) {
                    try {
                        stopLeaf(leafInstanceId)
                        leafInstanceId = 0
                    } catch (e: Exception) {
                        Log.w(TAG, "onDestroy中停止Leaf实例失败", e)
                    }
                }
                
                isVpnRunning = false
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy中关闭VPN失败", e)
            }
        }
    }
    
    private fun createLeafConfig(tunFd: Int): String {
        // 硬编码配置（原来的配置）
        return """
        {
            "inbounds": [
                {
                    "protocol": "tun",
                    "address": "127.0.0.1",
                    "port": 0,
                    "settings": {
                        "fd": $tunFd,
                        "auto": false,
                        "mtu": 1500
                    }
                }
            ],
            "outbounds": [
                {
                    "protocol": "shadowsocks",
                    "tag": "ss-out",
                    "settings": {
                        "address": "154.219.115.154",
                        "port": 12355,
                        "method": "aes-128-gcm",
                        "password": "sVbqrHyiDCYq0xeAJW5jSRqmvAxHx7aENNGuX+V6ikM=",
                        "prefix": "51eotusb"
                    }
                },
                {
                    "protocol": "direct",
                    "tag": "direct-out"
                }
            ],
            "router": {
                "rules": [
                    {
                        "ip": ["154.219.115.154"],
                        "target": "direct-out"
                    }
                ]
            },
            "log": {
                "level": "debug",
                "output": "console"
            }
        }
        """.trimIndent()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN连接状态通知"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Leaf VPN")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_connected)
            .setOngoing(true)
            .build()
    }
}