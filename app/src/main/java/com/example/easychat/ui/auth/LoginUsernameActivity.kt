package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.databinding.ActivityLoginUsernameBinding
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil

class LoginUsernameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginUsernameBinding
    private lateinit var phoneNumber: String
    private val viewModel: LoginUsernameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneNumber = intent.getStringExtra("phone") ?: ""
        viewModel.loadExistingUser()

        binding.loginLetMeInBtn.setOnClickListener {
            val username = binding.loginUsername.text.toString().trim()
            if (username.length < 3) {
                binding.loginUsername.error = "Username deve ter ao menos 3 caracteres"
                return@setOnClickListener
            }
            viewModel.saveUsername(phoneNumber, username)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { isLoading -> setInProgress(isLoading) }

        viewModel.existingUsername.observe(this) { username ->
            if (!username.isNullOrEmpty()) binding.loginUsername.setText(username)
        }

        viewModel.saveResult.observe(this) { success ->
            if (success) {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            } else {
                AndroidUtil.showToast(this, "Erro ao salvar username. Tente novamente.")
            }
        }
    }

    private fun setInProgress(inProgress: Boolean) {
        binding.loginProgressBar.visibility = if (inProgress) View.VISIBLE else View.GONE
        binding.loginLetMeInBtn.visibility = if (inProgress) View.GONE else View.VISIBLE
    }
}
