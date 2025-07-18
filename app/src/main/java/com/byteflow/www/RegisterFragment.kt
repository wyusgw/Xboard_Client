package com.byteflow.www

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var emailCodeEditText: EditText
    private lateinit var inviteCodeEditText: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var termsTextView: TextView
    
    private var codeCountdown = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupListeners()
        loadConfig()
    }
    
    private fun loadConfig() {
        lifecycleScope.launch {
            try {
                val result = ApiClient.getConfig()
                if (result.isSuccess) {
                    val config = result.getOrNull()
                    config?.let { 
                        // 根据配置决定是否显示邮箱验证码输入
                        val needEmailVerify = it.isEmailVerify == 1
                        if (!needEmailVerify) {
                            // 隐藏邮箱验证码相关UI
                            view?.findViewById<android.widget.LinearLayout>(R.id.email_code_layout)?.visibility = android.view.View.GONE
                        }
                        
                        // 根据配置决定是否强制邀请码
                        val needInviteCode = it.isInviteForce == 1
                        if (needInviteCode) {
                            inviteCodeEditText.hint = "邀请码（必填）"
                        }
                    }
                }
            } catch (e: Exception) {
                // 配置加载失败，使用默认设置
            }
        }
    }
    
    private fun setupViews(view: View) {
        emailEditText = view.findViewById(R.id.email_edit_text)
        passwordEditText = view.findViewById(R.id.password_edit_text)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password_edit_text)
        emailCodeEditText = view.findViewById(R.id.email_code_edit_text)
        inviteCodeEditText = view.findViewById(R.id.invite_code_edit_text)
        sendCodeButton = view.findViewById(R.id.send_code_button)
        registerButton = view.findViewById(R.id.register_button)
        progressBar = view.findViewById(R.id.progress_bar)
        termsTextView = view.findViewById(R.id.terms_text_view)
    }
    
    private fun setupListeners() {
        sendCodeButton.setOnClickListener {
            sendEmailCode()
        }
        
        registerButton.setOnClickListener {
            performRegister()
        }
        
        termsTextView.setOnClickListener {
            // TODO: 打开服务条款
            Toast.makeText(context, "服务条款功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendEmailCode() {
        val email = emailEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            emailEditText.error = "请输入邮箱"
            return
        }
        
        sendCodeButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val result = ApiClient.sendEmailVerify(email)
                
                if (result.isSuccess) {
                    Toast.makeText(context, "验证码发送成功", Toast.LENGTH_SHORT).show()
                    startCountdown()
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "发送失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    sendCodeButton.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                sendCodeButton.isEnabled = true
            }
        }
    }
    
    private fun performRegister() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()
        val emailCode = emailCodeEditText.text.toString().trim()
        val inviteCode = inviteCodeEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            emailEditText.error = "请输入邮箱"
            return
        }
        
        if (password.isEmpty()) {
            passwordEditText.error = "请输入密码"
            return
        }
        
        if (password.length < 6) {
            passwordEditText.error = "密码长度至少6位"
            return
        }
        
        if (password != confirmPassword) {
            confirmPasswordEditText.error = "两次输入的密码不一致"
            return
        }
        
        if (emailCode.isEmpty()) {
            emailCodeEditText.error = "请输入验证码"
            return
        }
        
        setLoading(true)
        
        lifecycleScope.launch {
            try {
                val result = ApiClient.register(email, password, emailCode, inviteCode)
                
                setLoading(false)
                
                if (result.isSuccess) {
                    val loginData = result.getOrNull()
                    if (loginData != null) {
                        // 保存登录信息
                        AuthManager.saveLoginData(requireContext(), loginData)
                        
                        // 跳转到主界面
                        (activity as? AuthActivity)?.onAuthSuccess()
                    }
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "注册失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startCountdown() {
        codeCountdown = 60
        updateCountdownButton()
        
        lifecycleScope.launch {
            while (codeCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                codeCountdown--
                updateCountdownButton()
            }
            
            sendCodeButton.isEnabled = true
            sendCodeButton.text = "发送验证码"
        }
    }
    
    private fun updateCountdownButton() {
        if (codeCountdown > 0) {
            sendCodeButton.text = "${codeCountdown}s"
        }
    }
    
    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        registerButton.isEnabled = !loading
        emailEditText.isEnabled = !loading
        passwordEditText.isEnabled = !loading
        confirmPasswordEditText.isEnabled = !loading
        emailCodeEditText.isEnabled = !loading
        inviteCodeEditText.isEnabled = !loading
    }
}