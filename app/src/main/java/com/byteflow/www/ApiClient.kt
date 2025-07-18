package com.byteflow.www

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoginData(
    val token: String,
    val authData: String,
    val isAdmin: Boolean?
)

data class ConfigData(
    val tosUrl: String?,
    val isEmailVerify: Int,
    val isInviteForce: Int,
    val emailWhitelistSuffix: Int,
    val isCaptcha: Int,
    val captchaType: String,
    val recaptchaSiteKey: String?,
    val recaptchaV3SiteKey: String?,
    val recaptchaV3ScoreThreshold: Double,
    val turnstileSiteKey: String?,
    val appDescription: String?,
    val appUrl: String?,
    val logo: String?,
    val isRecaptcha: Int
)

data class UserInfo(
    val email: String,
    val transferEnable: Long,
    val lastLoginAt: Long?,
    val createdAt: Long,
    val banned: Int,
    val remindExpire: Int,
    val remindTraffic: Int,
    val expiredAt: Long,
    val balance: Long,
    val commissionBalance: Long,
    val planId: Int?,
    val discount: Int?,
    val commissionRate: Int?,
    val telegramId: Long?,
    val uuid: String,
    val avatarUrl: String?
)

data class SubscribeInfo(
    val planId: Int?,
    val token: String,
    val expiredAt: Long?,
    val u: Long,
    val d: Long,
    val transferEnable: Long,
    val email: String,
    val uuid: String,
    val subscribeUrl: String,
    val resetDay: Int?,
    val plan: Plan?
)

data class Plan(
    val id: Int,
    val groupId: Int?,
    val name: String,
    val tags: List<String>,
    val content: String,
    val monthPrice: Int?,
    val quarterPrice: Int?,
    val halfYearPrice: Int?,
    val yearPrice: Int?,
    val twoYearPrice: Int?,
    val threeYearPrice: Int?,
    val onetimePrice: Int?,
    val resetPrice: Int?,
    val capacityLimit: Int?,
    val transferEnable: Long,
    val speedLimit: Int?,
    val deviceLimit: Int?,
    val show: Boolean,
    val sell: Boolean,
    val renew: Boolean,
    val resetTrafficMethod: Int?,
    val sort: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

object ApiClient {
    // 请在此处替换为你的V2Board域名
    private const val BASE_URL = "https://shop.starpro.one/api/v1"
    
    private var authToken: String? = null
    
    fun setAuthToken(token: String?) {
        authToken = token
    }
    
    private fun createFormData(params: Map<String, String>): String {
        return params.map { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }
    
    private suspend fun makeRequest(
        endpoint: String,
        method: String = "GET",
        params: Map<String, String>? = null,
        requireAuth: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$BASE_URL$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Content-Language", "zh-CN")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
            
            // 设置超时
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (requireAuth && authToken != null) {
                connection.setRequestProperty("Authorization", authToken!!)
            }
            
            if (params != null && (method == "POST" || method == "PUT")) {
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                val formData = createFormData(params)
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(formData)
                writer.flush()
                writer.close()
            }
            
            val responseCode = connection.responseCode
            val reader = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream))
            } else {
                BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
            }
            
            val response = reader.readText()
            reader.close()
            connection.disconnect()
            
            if (responseCode in 200..299) {
                Result.success(response)
            } else {
                val errorMsg = try {
                    val jsonResponse = JSONObject(response)
                    jsonResponse.optString("message", "请求失败")
                } catch (e: Exception) {
                    "请求失败: HTTP $responseCode"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Request failed", e)
            Result.failure(e)
        }
    }
    
    private fun parseApiResponse(response: String): Result<JSONObject> {
        return try {
            val jsonResponse = JSONObject(response)
            val status = jsonResponse.optString("status")
            val message = jsonResponse.optString("message")
            val error = jsonResponse.opt("error")
            
            if (status == "success") {
                val data = jsonResponse.optJSONObject("data")
                Result.success(data ?: JSONObject())
            } else {
                val errorMessage = error?.toString() ?: message.ifEmpty { "操作失败" }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("解析响应失败: ${e.message}"))
        }
    }
    
    suspend fun getConfig(): Result<ConfigData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/guest/comm/config")
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val configData = ConfigData(
                        tosUrl = data.optString("tos_url").takeIf { !data.isNull("tos_url") },
                        isEmailVerify = data.optInt("is_email_verify", 0),
                        isInviteForce = data.optInt("is_invite_force", 0),
                        emailWhitelistSuffix = data.optInt("email_whitelist_suffix", 0),
                        isCaptcha = data.optInt("is_captcha", 0),
                        captchaType = data.optString("captcha_type", "recaptcha"),
                        recaptchaSiteKey = data.optString("recaptcha_site_key").takeIf { !data.isNull("recaptcha_site_key") },
                        recaptchaV3SiteKey = data.optString("recaptcha_v3_site_key").takeIf { !data.isNull("recaptcha_v3_site_key") },
                        recaptchaV3ScoreThreshold = data.optDouble("recaptcha_v3_score_threshold", 0.5),
                        turnstileSiteKey = data.optString("turnstile_site_key").takeIf { !data.isNull("turnstile_site_key") },
                        appDescription = data.optString("app_description").takeIf { !data.isNull("app_description") },
                        appUrl = data.optString("app_url").takeIf { !data.isNull("app_url") },
                        logo = data.optString("logo").takeIf { !data.isNull("logo") },
                        isRecaptcha = data.optInt("is_recaptcha", 0)
                    )
                    Result.success(configData)
                } else {
                    parseResult.map { throw Exception("解析响应失败") }
                }
            } else {
                result.map { throw Exception("获取配置失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkLogin(): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/passport/auth/check", requireAuth = true)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val isLogin = data.optBoolean("is_login", false)
                    Result.success(isLogin)
                } else {
                    parseResult.map { false }
                }
            } else {
                result.map { false }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(email: String, password: String): Result<LoginData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val params = mapOf(
                "email" to email,
                "password" to password
            )
            
            val result = makeRequest("/passport/auth/login", "POST", params)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val loginData = LoginData(
                        token = data.getString("token"),
                        authData = data.getString("auth_data"),
                        isAdmin = data.optBoolean("is_admin", false)
                    )
                    // 设置认证token
                    setAuthToken(loginData.authData)
                    Result.success(loginData)
                } else {
                    parseResult.map { throw Exception("解析响应失败") }
                }
            } else {
                result.map { throw Exception("登录失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun register(
        email: String,
        password: String,
        emailCode: String = "",
        inviteCode: String = ""
    ): Result<LoginData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val params = mapOf(
                "email" to email,
                "password" to password,
                "email_code" to emailCode,
                "invite_code" to inviteCode
            )
            
            val result = makeRequest("/passport/auth/register", "POST", params)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val loginData = LoginData(
                        token = data.getString("token"),
                        authData = data.getString("auth_data"),
                        isAdmin = data.optBoolean("is_admin")
                    )
                    // 设置认证token
                    setAuthToken(loginData.authData)
                    Result.success(loginData)
                } else {
                    parseResult.map { throw Exception("解析响应失败") }
                }
            } else {
                result.map { throw Exception("注册失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendEmailVerify(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val params = mapOf("email" to email)
            
            val result = makeRequest("/passport/comm/sendEmailVerify", "POST", params)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    Result.success(true)
                } else {
                    parseResult.map { false }
                }
            } else {
                result.map { false }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserInfo(): Result<UserInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/user/info", requireAuth = true)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val userInfo = UserInfo(
                        email = data.getString("email"),
                        transferEnable = data.optLong("transfer_enable", 0),
                        lastLoginAt = data.optLong("last_login_at", 0).takeIf { it != 0L },
                        createdAt = data.optLong("created_at", 0),
                        banned = data.optInt("banned", 0),
                        remindExpire = data.optInt("remind_expire", 0),
                        remindTraffic = data.optInt("remind_traffic", 0),
                        expiredAt = data.optLong("expired_at", 0),
                        balance = data.optLong("balance", 0),
                        commissionBalance = data.optLong("commission_balance", 0),
                        planId = data.optInt("plan_id").takeIf { !data.isNull("plan_id") },
                        discount = data.optInt("discount").takeIf { !data.isNull("discount") },
                        commissionRate = data.optInt("commission_rate").takeIf { !data.isNull("commission_rate") },
                        telegramId = data.optLong("telegram_id").takeIf { !data.isNull("telegram_id") },
                        uuid = data.optString("uuid", ""),
                        avatarUrl = data.optString("avatar_url").takeIf { it.isNotEmpty() }
                    )
                    Result.success(userInfo)
                } else {
                    parseResult.map { throw Exception("解析响应失败") }
                }
            } else {
                result.map { throw Exception("获取用户信息失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSubscribeInfo(): Result<SubscribeInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/user/getSubscribe", requireAuth = true)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    val data = parseResult.getOrThrow()
                    val subscribeInfo = SubscribeInfo(
                        planId = data.optInt("plan_id").takeIf { !data.isNull("plan_id") },
                        token = data.optString("token", ""),
                        expiredAt = data.optLong("expired_at").takeIf { !data.isNull("expired_at") },
                        u = data.optLong("u", 0),
                        d = data.optLong("d", 0),
                        transferEnable = data.optLong("transfer_enable", 0),
                        email = data.optString("email", ""),
                        uuid = data.optString("uuid", ""),
                        subscribeUrl = data.optString("subscribe_url", ""),
                        resetDay = data.optInt("reset_day").takeIf { !data.isNull("reset_day") },
                        plan = data.optJSONObject("plan")?.let { planJson ->
                            // 解析plan对象，如果存在的话
                            null // 这里可以根据实际需要解析
                        }
                    )
                    Result.success(subscribeInfo)
                } else {
                    parseResult.map { throw Exception("解析响应失败") }
                }
            } else {
                result.map { throw Exception("获取订阅信息失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getPlans(): Result<List<Plan>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/user/plan/fetch", requireAuth = true)
            if (result.isSuccess) {
                val jsonResponse = JSONObject(result.getOrThrow())
                val status = jsonResponse.optString("status")
                
                if (status == "success") {
                    val dataArray = jsonResponse.getJSONArray("data")
                    val plans = mutableListOf<Plan>()
                    
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val tagsArray = item.optJSONArray("tags")
                        val tags = mutableListOf<String>()
                        
                        if (tagsArray != null) {
                            for (j in 0 until tagsArray.length()) {
                                tags.add(tagsArray.getString(j))
                            }
                        }
                        
                        val plan = Plan(
                            id = item.getInt("id"),
                            groupId = item.optInt("group_id").takeIf { !item.isNull("group_id") },
                            name = item.getString("name"),
                            tags = tags,
                            content = item.getString("content"),
                            monthPrice = item.optInt("month_price").takeIf { !item.isNull("month_price") },
                            quarterPrice = item.optInt("quarter_price").takeIf { !item.isNull("quarter_price") },
                            halfYearPrice = item.optInt("half_year_price").takeIf { !item.isNull("half_year_price") },
                            yearPrice = item.optInt("year_price").takeIf { !item.isNull("year_price") },
                            twoYearPrice = item.optInt("two_year_price").takeIf { !item.isNull("two_year_price") },
                            threeYearPrice = item.optInt("three_year_price").takeIf { !item.isNull("three_year_price") },
                            onetimePrice = item.optInt("onetime_price").takeIf { !item.isNull("onetime_price") },
                            resetPrice = item.optInt("reset_price").takeIf { !item.isNull("reset_price") },
                            capacityLimit = item.optInt("capacity_limit").takeIf { !item.isNull("capacity_limit") },
                            transferEnable = item.optLong("transfer_enable", 0),
                            speedLimit = item.optInt("speed_limit").takeIf { !item.isNull("speed_limit") },
                            deviceLimit = item.optInt("device_limit").takeIf { !item.isNull("device_limit") },
                            show = item.optBoolean("show", true),
                            sell = item.optBoolean("sell", true),
                            renew = item.optBoolean("renew", true),
                            resetTrafficMethod = item.optInt("reset_traffic_method").takeIf { !item.isNull("reset_traffic_method") },
                            sort = item.optInt("sort").takeIf { !item.isNull("sort") },
                            createdAt = item.optLong("created_at", 0),
                            updatedAt = item.optLong("updated_at", 0)
                        )
                        plans.add(plan)
                    }
                    Result.success(plans)
                } else {
                    val message = jsonResponse.optString("message", "获取套餐失败")
                    Result.failure(Exception(message))
                }
            } else {
                result.map { throw Exception("获取套餐失败") }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = makeRequest("/user/logout", "POST", requireAuth = true)
            if (result.isSuccess) {
                val parseResult = parseApiResponse(result.getOrThrow())
                if (parseResult.isSuccess) {
                    setAuthToken(null)
                    Result.success(true)
                } else {
                    // 即使服务器返回错误，也清除本地token
                    setAuthToken(null)
                    Result.success(true)
                }
            } else {
                // 即使请求失败，也清除本地token
                setAuthToken(null)
                Result.success(true)
            }
        } catch (e: Exception) {
            // 即使网络异常，也清除本地token
            setAuthToken(null)
            Result.success(true)
        }
    }
}