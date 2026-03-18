package com.example.easychat.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.R
import com.example.easychat.databinding.ActivityChatBinding
import com.example.easychat.model.ChatMessageUiModel
import com.example.easychat.model.UserModel
import com.example.easychat.utils.AndroidUtil
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var otherUser: UserModel
    private lateinit var adapter: ChatRecyclerAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputPath: String = ""
    private var isRecording = false

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(otherUser, applicationContext)
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            viewModel.sendImageMessage(uri, contentResolver)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendLocation()
        else Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording()
        else Toast.makeText(this, "Permissão de microfone negada", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        otherUser = AndroidUtil.getUserModelFromIntent(intent)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupRecyclerView()
        setupInputBar()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.otherUsername.text = otherUser.username
        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (!otherUser.avatarUrl.isNullOrBlank()) {
            Glide.with(this).load(otherUser.avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.profilePicImageView)
        }
    }

    private fun setupRecyclerView() {
        // Adapter recebe apenas o callback de long-click — sem currentUserId (ViewModel já resolve)
        adapter = ChatRecyclerAdapter(onLongClick = { uiModel -> showPinDialog(uiModel) })
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
        }
        binding.chatRecyclerView.adapter = adapter
    }

    private fun setupInputBar() {
        binding.messageSendBtn.setOnClickListener {
            val text = binding.chatMessageInput.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendTextMessage(text)
        }

        binding.attachImageBtn.setOnClickListener {
            val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 100)
                return@setOnClickListener
            }
            ImagePicker.with(this).cropSquare().compress(1024).maxResultSize(1080, 1080)
                .createIntent { intent -> imagePickerLauncher.launch(intent) }
        }

        binding.recordAudioBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) toggleRecording()
            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.sendLocationBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) sendLocation()
            else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        binding.searchMessagesBtn.setOnClickListener {
            if (binding.searchMessagesLayout.visibility == View.VISIBLE) {
                binding.searchMessagesLayout.visibility = View.GONE
                binding.searchMessagesInput.setText("")
                viewModel.filterMessages("")
                adapter.highlightKeyword = ""
                adapter.notifyDataSetChanged()
            } else {
                binding.searchMessagesLayout.visibility = View.VISIBLE
                binding.searchMessagesInput.requestFocus()
            }
        }

        binding.searchMessagesInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString()
                adapter.highlightKeyword = keyword
                viewModel.filterMessages(keyword)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.pinnedMessageClose.setOnClickListener {
            viewModel.pinnedMessage.value?.let { pinned -> viewModel.togglePin(pinned) }
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) binding.chatRecyclerView.scrollToPosition(0)
        }

        viewModel.pinnedMessage.observe(this) { pinned ->
            if (pinned != null) {
                binding.pinnedMessageLayout.visibility = View.VISIBLE
                // texto já descriptografado pelo ViewModel
                val displayText = when (pinned.type) {
                    "image"    -> "📷 Foto"
                    "audio"    -> "🎵 Áudio"
                    "location" -> "📍 Localização"
                    else       -> pinned.text ?: ""
                }
                binding.pinnedMessageText.text = displayText.ifBlank { "[Mensagem]" }
            } else {
                binding.pinnedMessageLayout.visibility = View.GONE
            }
        }

        viewModel.messageSent.observe(this) { sent ->
            if (sent == true) { binding.chatMessageInput.setText(""); viewModel.clearMessageSent() }
        }

        viewModel.isUploading.observe(this) { uploading ->
            binding.uploadProgressBar.visibility = if (uploading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun showPinDialog(uiModel: ChatMessageUiModel) {
        val action = if (uiModel.isPinned) "Desafixar mensagem" else "Fixar mensagem"
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(if (uiModel.isPinned) "Deseja desafixar esta mensagem?" else "Deseja fixar esta mensagem no topo do chat?")
            .setPositiveButton("Confirmar") { _, _ -> viewModel.togglePin(uiModel) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleRecording() { if (isRecording) stopRecording() else startRecording() }

    private fun startRecording() {
        audioOutputPath = "${externalCacheDir?.absolutePath}/audio_${System.currentTimeMillis()}.m4a"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioOutputPath)
            prepare(); start()
        }
        isRecording = true
        binding.recordAudioBtn.setImageResource(R.drawable.ic_stop)
        Toast.makeText(this, "Gravando...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release() }
        catch (e: Exception) { /* ignora gravação muito curta */ }
        finally { mediaRecorder = null; isRecording = false }
        binding.recordAudioBtn.setImageResource(R.drawable.ic_mic)
        if (File(audioOutputPath).exists()) viewModel.sendAudioMessage(audioOutputPath)
    }

    @SuppressLint("MissingPermission")
    private fun sendLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) viewModel.sendLocationMessage(location.latitude, location.longitude)
            else Toast.makeText(this, "Não foi possível obter localização", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (e: Exception) { }
    }
}
