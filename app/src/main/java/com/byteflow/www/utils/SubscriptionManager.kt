package com.byteflow.www.utils

import android.util.Log
import com.byteflow.www.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.yaml.snakeyaml.Yaml
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionManager {
    private val tag = "SubscriptionManager"
    
    // 缓存域名解析结果
    private val dnsCache = mutableMapOf<String, String>()
    
    companion object {
        // 单例模式确保全局只有一个实例
        @Volatile
        private var INSTANCE: SubscriptionManager? = null
        
        fun getInstance(): SubscriptionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionManager().also { INSTANCE = it }
            }
        }
        
        // 订阅配置缓存（静态，全局共享）
        private var cachedConfig: ClashConfig? = null
        private var cachedConfigUrl: String? = null
        private var isUpdating = false
    }
    
    // 获取缓存的配置
    fun getCachedConfig(): ClashConfig? {
        return cachedConfig
    }
    
    suspend fun fetchSubscription(subscriptionUrl: String): ClashConfig? = withContext(Dispatchers.IO) {
        try {
            // 如果有缓存，直接返回缓存
            if (cachedConfig != null && cachedConfigUrl == subscriptionUrl) {
                Log.d(tag, "使用缓存的订阅配置")
                return@withContext cachedConfig
            }
            
            Log.d(tag, "正在获取订阅链接: $subscriptionUrl")
            
            val url = URL(subscriptionUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "clash")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(tag, "订阅内容获取成功，长度: ${response.length}")
                val config = parseClashConfig(response)
                
                // 缓存配置
                if (config != null) {
                    cachedConfig = config
                    cachedConfigUrl = subscriptionUrl
                    Log.d(tag, "配置已缓存")
                }
                
                config
            } else {
                Log.e(tag, "获取订阅失败，响应码: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "获取订阅异常", e)
            null
        }
    }
    
    // 异步更新订阅配置（后台更新，不阻塞UI）
    suspend fun updateSubscriptionAsync(subscriptionUrl: String) = withContext(Dispatchers.IO) {
        if (isUpdating) {
            Log.d(tag, "订阅更新中，跳过")
            return@withContext
        }
        
        isUpdating = true
        try {
            Log.d(tag, "异步更新订阅配置: $subscriptionUrl")
            
            val url = URL(subscriptionUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "clash")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val config = parseClashConfig(response)
                
                if (config != null) {
                    cachedConfig = config
                    cachedConfigUrl = subscriptionUrl
                    Log.d(tag, "订阅配置异步更新成功")
                }
            } else {
                Log.w(tag, "异步更新订阅失败，响应码: $responseCode")
            }
        } catch (e: Exception) {
            Log.w(tag, "异步更新订阅异常", e)
        } finally {
            isUpdating = false
        }
    }
    
    private fun parseClashConfig(yamlContent: String): ClashConfig? {
        return try {
            Log.d(tag, "开始解析 Clash 配置")
            val yaml = Yaml()
            val configMap = yaml.load<Map<String, Any>>(StringReader(yamlContent))
            
            val proxies = mutableListOf<ClashProxy>()
            val proxiesData = configMap["proxies"] as? List<Map<String, Any>>
            
            proxiesData?.forEach { proxyMap ->
                val proxy = ClashProxy(
                    name = proxyMap["name"] as String,
                    type = proxyMap["type"] as String,
                    server = proxyMap["server"] as String,
                    port = proxyMap["port"] as Int,
                    cipher = proxyMap["cipher"] as? String,
                    password = proxyMap["password"] as? String,
                    udp = proxyMap["udp"] as? Boolean ?: true,
                    // SSR specific fields - removed for SS-only support
                    protocol = null,
                    `protocol-param` = null,
                    obfs = null,
                    `obfs-param` = null
                )
                proxies.add(proxy)
            }
            
            Log.d(tag, "解析完成，共 ${proxies.size} 个代理节点")
            ClashConfig(proxies = proxies)
        } catch (e: Exception) {
            Log.e(tag, "解析 Clash 配置失败", e)
            null
        }
    }
    
    private suspend fun resolveHostname(hostname: String): String = withContext(Dispatchers.IO) {
        // 检查缓存
        dnsCache[hostname]?.let { return@withContext it }
        
        try {
            Log.d(tag, "正在解析域名: $hostname")
            
            // 添加超时控制
            val timeoutJob = async {
                delay(5000) // 5秒超时
                throw Exception("DNS解析超时")
            }
            
            val dnsJob = async {
                val inetAddress = InetAddress.getByName(hostname)
                inetAddress.hostAddress ?: hostname
            }
            
            val ip = select {
                timeoutJob.onAwait { throw Exception("DNS解析超时") }
                dnsJob.onAwait { it }
            }
            
            timeoutJob.cancel()
            
            // 缓存结果
            dnsCache[hostname] = ip
            Log.d(tag, "域名解析成功: $hostname -> $ip")
            ip
        } catch (e: Exception) {
            Log.w(tag, "域名解析失败: $hostname, ${e.message}")
            // 解析失败时返回原域名
            hostname
        }
    }
    
    suspend fun preResolveDomains(clashConfig: ClashConfig) = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "开始预解析域名")
            
            // 获取所有唯一的服务器域名
            val uniqueServers = clashConfig.proxies
                .map { it.server }
                .distinct()
                .filter { !it.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) } // 排除IP地址
            
            uniqueServers.forEach { server ->
                resolveHostname(server)
            }
            
            Log.d(tag, "域名预解析完成，共解析 ${uniqueServers.size} 个域名")
        } catch (e: Exception) {
            Log.e(tag, "域名预解析失败", e)
        }
    }
    
    fun convertToLeaf(clashConfig: ClashConfig, tunFd: Int, selectedNodeName: String? = null): LeafConfig {
        Log.d(tag, "开始转换为 Leaf 配置")
        
        // 选择节点：优先使用指定节点，否则选择第一个可用的 Shadowsocks 节点
        val selectedProxy = if (!selectedNodeName.isNullOrEmpty()) {
            clashConfig.proxies.find { it.name == selectedNodeName }
        } else {
            null
        } ?: clashConfig.proxies
            .filter { 
                it.type == "ss" && 
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
            .firstOrNull() ?: clashConfig.proxies.first()
            
        Log.d(tag, "选择节点: ${selectedProxy.name}")
        
        // 使用缓存的IP或直接使用域名
        val serverAddress = dnsCache[selectedProxy.server] ?: selectedProxy.server
        Log.d(tag, "服务器地址: ${selectedProxy.server} -> $serverAddress")
        
        val inbounds = listOf(
            LeafInbound(
                protocol = "tun",
                address = "127.0.0.1",
                port = 0,
                settings = mapOf(
                    "fd" to tunFd,
                    "auto" to false,
                    "mtu" to 1500
                )
            )
        )
        
        // 仅支持 Shadowsocks 协议
        val outbounds = listOf(
            LeafOutbound(
                protocol = "shadowsocks",
                tag = "ss-out",
                settings = mapOf(
                    "address" to serverAddress,
                    "port" to selectedProxy.port,
                    "method" to (selectedProxy.cipher ?: "aes-128-gcm"),
                    "password" to (selectedProxy.password ?: "")
                )
            ),
            LeafOutbound(
                protocol = "direct",
                tag = "direct-out",
                settings = emptyMap<String, Any>()
            )
        )
        
        // 需要直连的IP列表（代理服务器IP）
        val directIps = listOf(serverAddress)
        
        val router = LeafRouter(
            rules = listOf(
                LeafRule(
                    ip = directIps,
                    target = "direct-out"
                )
            )
        )
        
        val log = LeafLog(
            level = "debug",
            output = "console"
        )
        
        return LeafConfig(
            inbounds = inbounds,
            outbounds = outbounds,
            router = router,
            log = log
        )
    }
    
    // 测试单个节点延迟
    suspend fun testNodeLatency(proxy: ClashProxy): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(tag, "测试节点延迟: ${proxy.name}")
            proxy.isTestingLatency = true
            
            // 解析服务器地址
            val serverAddress = try {
                dnsCache[proxy.server] ?: resolveHostname(proxy.server)
            } catch (e: Exception) {
                Log.w(tag, "DNS解析失败: ${proxy.server}", e)
                proxy.server // 使用原始域名
            }
            
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            
            try {
                // 设置连接超时为3秒，减少等待时间
                socket = Socket()
                socket.connect(InetSocketAddress(serverAddress, proxy.port), 3000)
                val endTime = System.currentTimeMillis()
                val latency = (endTime - startTime).toInt()
                
                proxy.latency = latency
                Log.d(tag, "节点 ${proxy.name} 延迟: ${latency}ms")
                latency
            } catch (e: Exception) {
                Log.w(tag, "节点 ${proxy.name} 连接失败: ${e.message}")
                proxy.latency = 0 // 0表示超时或连接失败
                0
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // 忽略关闭异常
                }
                proxy.isTestingLatency = false
            }
        } catch (e: Exception) {
            Log.e(tag, "测试节点 ${proxy.name} 延迟异常: ${e.message}")
            proxy.latency = 0
            proxy.isTestingLatency = false
            0
        }
    }
    
    // 批量测试所有节点延迟
    suspend fun testAllNodesLatency(clashConfig: ClashConfig, onProgress: ((Int, Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        try {
            val validProxies = clashConfig.proxies.filter { 
                it.type == "ss" && 
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
            
            Log.d(tag, "开始并发测试 ${validProxies.size} 个节点延迟")
            
            val completed = AtomicInteger(0)
            val concurrentLimit = 10 // 减少并发数，避免资源耗尽
            
            // 使用并发测试，限制并发数
            validProxies.chunked(concurrentLimit).forEach { chunk ->
                try {
                    chunk.map { proxy ->
                        async {
                            try {
                                testNodeLatency(proxy)
                                val current = completed.incrementAndGet()
                                onProgress?.invoke(current, validProxies.size)
                            } catch (e: Exception) {
                                Log.w(tag, "测试节点 ${proxy.name} 失败: ${e.message}")
                                proxy.latency = 0
                                proxy.isTestingLatency = false
                            }
                        }
                    }.awaitAll()
                } catch (e: Exception) {
                    Log.w(tag, "批量测试异常: ${e.message}")
                }
                
                // 在批次之间添加小延迟，避免过度消耗资源
                delay(100)
            }
            
            Log.d(tag, "并发延迟测试完成")
        } catch (e: Exception) {
            Log.e(tag, "测试所有节点延迟异常: ${e.message}")
        }
    }
    
    // 获取延迟状态文本
    fun getLatencyText(proxy: ClashProxy): String {
        return when {
            proxy.isTestingLatency -> "测试中..."
            proxy.latency == -1 -> "未测试"
            proxy.latency == 0 -> "超时"
            else -> "${proxy.latency}ms"
        }
    }
    
    // 获取延迟颜色资源ID
    fun getLatencyColor(proxy: ClashProxy): Int {
        return when {
            proxy.isTestingLatency -> android.R.color.darker_gray
            proxy.latency == -1 -> android.R.color.darker_gray
            proxy.latency == 0 -> android.R.color.holo_red_light
            proxy.latency <= 100 -> android.R.color.holo_green_light
            proxy.latency <= 300 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_red_light
        }
    }
}