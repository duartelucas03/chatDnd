package com.example.easychat.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.databinding.FragmentProfileBinding
import com.example.easychat.ui.auth.SplashActivity
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.messaging.FirebaseMessaging

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private val viewModel: ProfileViewModel by viewModels()

    // SEM imagePickLauncher aqui — o launcher fica na MainActivity para evitar
    // IllegalStateException quando o ImagePicker exibe seu diálogo intermediário.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        binding.profleUpdateBtn.setOnClickListener {
            val newUsername = binding.profileUsername.text.toString().trim()
            if (newUsername.length < 3) {
                binding.profileUsername.error = "Username deve ter ao menos 3 caracteres"
                return@setOnClickListener
            }
            val currentUser = viewModel.user.value
            if (currentUser == null) {
                AndroidUtil.showToast(requireContext(), "Aguarde carregar o perfil")
                return@setOnClickListener
            }
            if (selectedImageUri == null && newUsername == currentUser.username) {
                AndroidUtil.showToast(requireContext(), "Nenhuma alteração detectada")
                return@setOnClickListener
            }
            viewModel.updateProfile(newUsername, selectedImageUri, requireContext().contentResolver)
            selectedImageUri = null
        }

        binding.logoutBtn.setOnClickListener {
            binding.logoutBtn.isEnabled = false
            viewModel.logout { onTokenDeleted ->
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener { onTokenDeleted() }
                    .addOnFailureListener { onTokenDeleted() }
            }
        }

        binding.profileImageView.setOnClickListener {
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 100)
                return@setOnClickListener
            }

            // Usa o launcher da MainActivity — registrado antes de qualquer diálogo intermediário
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener
            ImagePicker.with(this).cropSquare().compress(512).maxResultSize(512, 512)
                .createIntent { intent ->
                    mainActivity.launchImagePicker(intent) { uri ->
                        selectedImageUri = uri
                        if (_binding != null) {
                            Glide.with(this).load(uri)
                                .apply(RequestOptions.circleCropTransform()
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE))
                                .into(binding.profileImageView)
                        }
                    }
                }
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.profileProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.profleUpdateBtn.visibility     = if (isLoading) View.GONE    else View.VISIBLE
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            binding.profileUsername.setText(user.username)
            binding.profilePhone.setText(user.phone)
            if (!user.avatarUrl.isNullOrBlank()) {
                Glide.with(this).load(user.avatarUrl)
                    .apply(RequestOptions.circleCropTransform()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE))
                    .into(binding.profileImageView)
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { success ->
            success ?: return@observe
            AndroidUtil.showToast(requireContext(), if (success) "Perfil atualizado!" else "Falha ao atualizar")
            viewModel.clearUpdateResult()
        }

        // logoutEvent é um SingleLiveEvent — dispara apenas uma vez, nunca no observe inicial
        viewModel.logoutEvent.observe(viewLifecycleOwner) {
            binding.logoutBtn.isEnabled = true
            startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

