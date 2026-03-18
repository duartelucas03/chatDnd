package com.example.easychat.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easychat.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecentChatRecyclerAdapter

    // Compartilha a mesma instância do ViewModel da Activity
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = RecentChatRecyclerAdapter(requireContext())
        binding.recylerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recylerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.recentChats.observe(viewLifecycleOwner) { chats ->
            adapter.submitList(chats)
        }
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            // Opcional: exibir ProgressBar no fragment_chat.xml
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecentChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
