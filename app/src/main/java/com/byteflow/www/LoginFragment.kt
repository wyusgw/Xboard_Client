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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {
    
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var forgotPasswordTextView: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupListeners()
    }
    
    private fun setupViews(view: View) {
        emailEditText = view.findViewById(R.id.email_edit_text)
        passwordEditText = view.findViewById(R.id.password_edit_text)
        loginButton = view.findViewById(R.id.login_button)
        progressBar = view.findViewById(R.id.progress_bar)
        forgotPasswordTextView = view.findViewById(R.id.forgot_password_text_view)
    }
    
    private fun setupListeners() {
        loginButton.setOnClickListener {
            performLogin()
        }
        
        forgotPasswordTextView.setOnClickListener {
            // TODO: 实现忘记密码功能
            Toast.makeText(context, "忘记密码功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun performLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            emailEditText.error = "请输入邮箱"
            return
        }
        
        if (password.isEmpty()) {
            passwordEditText.error = "请输入密码"
            return
        }
        
        setLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = ApiClient.login(email, password)
                
                withContext(Dispatchers.Main) {
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
                            result.exceptionOrNull()?.message ?: "登录失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        emailEditText.isEnabled = !loading
        passwordEditText.isEnabled = !loading
    }
}