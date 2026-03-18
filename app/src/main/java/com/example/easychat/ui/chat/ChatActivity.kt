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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.R
import com.example.easychat.databinding.ActivityChatBinding
import com.example.easychat.model.ChatMessageModel
import com.example.easychat.model.UserModel
import com.example.easychat.utils.AndroidUtil
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import androidx.core.app.ActivityCompat


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

    // Launcher para selecionar imagem da galeria ou câmera
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            viewModel.sendImageMessage(uri, contentResolver)
        }
    }

    // Launcher para permissão de localização
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendLocation()
        else Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
    }

    // Launcher para permissão de microfone
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording()
        else Toast.makeText(this, "Permissão de microfone negada", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        otherUser = AndroidUtil.getUserModelFromIntent(intent)
        super.onCreate(savedInstanceState)
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
            Glide.with(this)
                .load(otherUser.avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.profilePicImageView)
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatRecyclerAdapter(
            currentUserId = com.example.easychat.utils.SupabaseClientProvider.currentUserId(),
            onLongClick = { message -> showPinDialog(message) }
        )

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
        }
        binding.chatRecyclerView.adapter = adapter
    }

    private fun setupInputBar() {
        // Enviar texto
        binding.messageSendBtn.setOnClickListener {
            val text = binding.chatMessageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendTextMessage(text)
            }
        }

        // Req. 7 + Req. 15 — Câmera / galeria
        binding.attachImageBtn.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                        100
                    )
                    return@setOnClickListener
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        100
                    )
                    return@setOnClickListener
                }
            }
            ImagePicker.with(this)
                .cropSquare()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .createIntent { intent -> imagePickerLauncher.launch(intent) }
        }

        // Req. 15 — Microfone (gravar/parar)
        binding.recordAudioBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleRecording()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Req. 15 — GPS
        binding.sendLocationBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                sendLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Req. 14 — Filtro por palavra-chave
        binding.searchMessagesBtn.setOnClickListener {
            val searchBar = binding.searchMessagesLayout
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
                viewModel.filterMessages("")
                adapter.highlightKeyword = ""
                adapter.notifyDataSetChanged()
            } else {
                searchBar.visibility = View.VISIBLE
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

        // Req. 13 — Fechar mensagem fixada
        binding.pinnedMessageClose.setOnClickListener {
            viewModel.pinnedMessage.value?.let { pinned ->
                viewModel.togglePin(pinned)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.chatRecyclerView.scrollToPosition(0)
            }
        }

        viewModel.pinnedMessage.observe(this) { pinned ->
            if (pinned != null) {
                binding.pinnedMessageLayout.visibility = View.VISIBLE
                binding.pinnedMessageText.text = pinned.content?.ifBlank { "[Mídia]" }
            } else {
                binding.pinnedMessageLayout.visibility = View.GONE
            }
        }

        viewModel.messageSent.observe(this) { sent ->
            if (sent == true) {
                binding.chatMessageInput.setText("")
                viewModel.clearMessageSent()
            }
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

    // ─────────────────────────────────────────────
    // Req. 13 — Dialog de fixar/desafixar
    // ─────────────────────────────────────────────
    private fun showPinDialog(message: ChatMessageModel) {
        val action = if (message.isPinned) "Desafixar mensagem" else "Fixar mensagem"
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(
                if (message.isPinned) "Deseja desafixar esta mensagem?"
                else "Deseja fixar esta mensagem no topo do chat?"
            )
            .setPositiveButton("Confirmar") { _, _ -> viewModel.togglePin(message) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─────────────────────────────────────────────
    // Req. 15 — Microfone
    // ─────────────────────────────────────────────
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        audioOutputPath = "${externalCacheDir?.absolutePath}/audio_${System.currentTimeMillis()}.m4a"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioOutputPath)
            prepare()
            start()
        }
        isRecording = true
        binding.recordAudioBtn.setImageResource(R.drawable.ic_stop)
        Toast.makeText(this, "Gravando...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        isRecording = false
        binding.recordAudioBtn.setImageResource(R.drawable.ic_mic)

        if (File(audioOutputPath).exists()) {
            viewModel.sendAudioMessage(audioOutputPath)
        }
    }

    // ─────────────────────────────────────────────
    // Req. 15 — GPS
    // ─────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private fun sendLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.sendLocationMessage(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "Não foi possível obter localização", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        }
    }
}
