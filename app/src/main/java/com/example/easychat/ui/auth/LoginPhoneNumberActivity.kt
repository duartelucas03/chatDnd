package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.databinding.ActivityLoginPhoneNumberBinding

class LoginPhoneNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginPhoneNumberBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginPhoneNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginProgressBar.visibility = View.GONE
        binding.loginCountrycode.registerCarrierNumberEditText(binding.loginMobileNumber)

        binding.sendOtpBtn.setOnClickListener {
            if (!binding.loginCountrycode.isValidFullNumber) {
                binding.loginMobileNumber.error = "Número de telefone inválido"
                return@setOnClickListener
            }
            val phone = binding.loginCountrycode.fullNumberWithPlus
            startActivity(Intent(this, LoginOtpActivity::class.java).apply {
                putExtra("phone", phone)
            })
        }
    }
}
