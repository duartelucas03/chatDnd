package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.databinding.ActivityLoginOtpBinding
import com.example.easychat.utils.AndroidUtil

class LoginOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginOtpBinding
    private lateinit var phoneNumber: String
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneNumber = intent.getStringExtra("phone") ?: ""
        viewModel.sendOtp(phoneNumber)

        binding.loginNextBtn.setOnClickListener {
            val otp = binding.loginOtp.text.toString()
            if (otp.length < 6) {
                binding.loginOtp.error = "OTP inválido"
                return@setOnClickListener
            }
            viewModel.verifyOtp(otp)
        }

        binding.resendOtpTextview.setOnClickListener {
            viewModel.sendOtp(phoneNumber)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> setInProgress(true)
                is AuthState.CodeSent -> {
                    setInProgress(false)
                    AndroidUtil.showToast(this, "OTP enviado com sucesso")
                }
                is AuthState.SignInSuccess -> {
                    setInProgress(false)
                    startActivity(
                        Intent(this, LoginUsernameActivity::class.java).apply {
                            putExtra("phone", phoneNumber)
                        }
                    )
                }
                is AuthState.Error -> {
                    setInProgress(false)
                    AndroidUtil.showToast(this, state.message)
                }
                else -> setInProgress(false)
            }
        }
    }

    private fun setInProgress(inProgress: Boolean) {
        binding.loginProgressBar.visibility = if (inProgress) View.VISIBLE else View.GONE
        binding.loginNextBtn.visibility = if (inProgress) View.GONE else View.VISIBLE
    }
}
