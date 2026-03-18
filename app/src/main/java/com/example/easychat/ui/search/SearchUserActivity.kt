package com.example.easychat.ui.search

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easychat.databinding.ActivitySearchUserBinding

class SearchUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchUserBinding
    private lateinit var adapter: SearchUserRecyclerAdapter
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // ID no XML é "seach_username_input" (typo original mantido por compatibilidade)
        binding.seachUsernameInput.requestFocus()
    }

    private fun setupRecyclerView() {
        adapter = SearchUserRecyclerAdapter(applicationContext)
        binding.searchUserRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchUserRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.searchUserBtn.setOnClickListener {
            val term = binding.seachUsernameInput.text.toString().trim()
            if (term.length < 3) {
                binding.seachUsernameInput.error = "Digite ao menos 3 caracteres"
                return@setOnClickListener
            }
            viewModel.searchUsers(term)
        }
    }

    private fun observeViewModel() {
        viewModel.results.observe(this) { users ->
            adapter.submitList(users)
        }

        // FIX: loading agora controla visibilidade do botão de busca
        viewModel.loading.observe(this) { isLoading ->
            binding.searchUserBtn.isEnabled = !isLoading
            binding.searchUserBtn.alpha     = if (isLoading) 0.5f else 1.0f
        }
    }
}
