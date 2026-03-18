package com.example.easychat.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.databinding.FragmentProfileBinding
import com.example.easychat.ui.auth.SplashActivity
import com.example.easychat.utils.AndroidUtil
import com.example.easychat.utils.SupabaseClientProvider
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private val viewModel: ProfileViewModel by viewModels()

    private val imagePickLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                Glide.with(this)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.profileImageView)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        // ID no XML é "profle_update_btn" (typo original mantido)
        binding.profleUpdateBtn.setOnClickListener {
            val newUsername = binding.profileUsername.text.toString().trim()
            if (newUsername.length < 3) {
                binding.profileUsername.error = "Username deve ter ao menos 3 caracteres"
                return@setOnClickListener
            }
            viewModel.updateProfile(newUsername, selectedImageUri, requireContext().contentResolver)
        }

        binding.logoutBtn.setOnClickListener {
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClientProvider.auth.signOut()
                }
                startActivity(
                    Intent(requireContext(), SplashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }

        binding.profileImageView.setOnClickListener {
            ImagePicker.with(this)
                .cropSquare()
                .compress(512)
                .maxResultSize(512, 512)
                .createIntent { intent ->
                    try {
                        imagePickLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("IMAGE_PICKER", "Erro: ${e.message}")
                    }
                }
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.profileProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.profleUpdateBtn.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            binding.profileUsername.setText(user.username)
            binding.profilePhone.setText(user.phone)
            if (!user.avatarUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(user.avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.profileImageView)
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { success ->
            success ?: return@observe
            AndroidUtil.showToast(
                requireContext(),
                if (success) "Perfil atualizado!" else "Falha ao atualizar"
            )
            viewModel.clearUpdateResult()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
