package com.byteflow.www.models

data class ClashConfig(
    val proxies: List<ClashProxy>,
    val `proxy-groups`: List<ClashProxyGroup>? = null,
    val rules: List<String>? = null
)

data class ClashProxy(
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val cipher: String? = null,
    val password: String? = null,
    val udp: Boolean? = true,
    // SSR specific fields
    val protocol: String? = null,
    val `protocol-param`: String? = null,
    val obfs: String? = null,
    val `obfs-param`: String? = null,
    // 延迟测试相关
    var latency: Int = -1, // -1表示未测试，0表示超时，>0表示延迟毫秒数
    var isTestingLatency: Boolean = false
)

data class ClashProxyGroup(
    val name: String,
    val type: String,
    val proxies: List<String>
)

data class LeafConfig(
    val inbounds: List<LeafInbound>,
    val outbounds: List<LeafOutbound>,
    val router: LeafRouter? = null,
    val log: LeafLog? = null
)

data class LeafInbound(
    val protocol: String,
    val address: String? = null,
    val port: Int? = null,
    val settings: Map<String, Any>
)

data class LeafOutbound(
    val protocol: String,
    val tag: String,
    val settings: Map<String, Any>
)

data class LeafRouter(
    val rules: List<LeafRule>
)

data class LeafRule(
    val ip: List<String>? = null,
    val target: String
)

data class LeafLog(
    val level: String,
    val output: String
)