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

        // Suporta abertura por UserModel (chat direto) ou por chatroomId (grupo)
        val chatroomId = AndroidUtil.getChatroomIdFromIntent(intent)
        otherUser = if (chatroomId != null) {
            // Grupo: cria um UserModel sintético só para o toolbar
            UserModel(
                id       = "",
                username = intent.getStringExtra("chatroom_display_name") ?: "Grupo",
                avatarUrl = intent.getStringExtra("chatroom_avatar_url")?.takeIf { it.isNotBlank() }
            )
        } else {
            AndroidUtil.getUserModelFromIntent(intent)
        }

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupRecyclerView()
        setupInputBar()
        observeViewModel()

        // Se veio com chatroomId direto (grupo), injeta no ViewModel
        if (chatroomId != null) {
            viewModel.setExistingChatroom(chatroomId)
        }
    }

    private fun setupToolbar() {
        binding.otherUsername.text = otherUser.username
        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (!otherUser.avatarUrl.isNullOrBlank()) {
            Glide.with(this).load(otherUser.avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.profilePicImageView)
        }
        updateUserStatusUi(otherUser)

        // Mostra botão de gerenciar grupo apenas para grupos
        val isGroup = AndroidUtil.getChatroomIdFromIntent(intent) != null
        if (isGroup) {
            binding.manageGroupBtn.visibility = View.VISIBLE
            binding.manageGroupBtn.setOnClickListener {
                viewModel.loadGroupMembers()
                showGroupMembersDialog()
            }
            // Esconde status (não faz sentido para grupos)
            binding.otherUserStatus.visibility = View.GONE
        }
    }

    private fun showGroupMembersDialog() {
        // Remove observer anterior para evitar múltiplos dialogs ao clicar várias vezes
        viewModel.groupMembers.removeObservers(this)
        viewModel.groupMembers.observe(this) { members ->
            if (members.isEmpty()) return@observe
            // Remove imediatamente após receber — evita re-disparar na próxima mudança
            viewModel.groupMembers.removeObservers(this)

            val currentUserId = com.example.easychat.utils.SupabaseClientProvider.currentUserId()
            val chatroomId = AndroidUtil.getChatroomIdFromIntent(intent) ?: return@observe

            // Monta opções: participantes + separador + ações
            val items = mutableListOf<String>()
            items.addAll(members.map { m ->
                if (m.id == currentUserId) "✓ ${m.username} (Você)" else m.username
            })
            items.add("── ──────────────────")
            items.add("➕ Adicionar participante")
            items.add("🚪 Sair do grupo")

            AlertDialog.Builder(this)
                .setTitle("Grupo · ${members.size} participante(s)")
                .setItems(items.toTypedArray()) { _, idx ->
                    when {
                        idx < members.size -> {
                            // Clicou num membro
                            val member = members[idx]
                            if (member.id == currentUserId) return@setItems
                            AlertDialog.Builder(this)
                                .setTitle("Remover ${member.username}?")
                                .setPositiveButton("Remover") { _, _ ->
                                    viewModel.removeGroupMember(member.id)
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                        items[idx].contains("Adicionar") -> {
                            showAddMemberDialog(chatroomId)
                        }
                        items[idx].contains("Sair") -> {
                            AlertDialog.Builder(this)
                                .setTitle("Sair do grupo?")
                                .setMessage("Você não poderá mais ver as mensagens deste grupo.")
                                .setPositiveButton("Sair") { _, _ ->
                                    viewModel.removeGroupMember(currentUserId)
                                    finish()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }
                }
                .setNegativeButton("Fechar", null)
                .show()
        }
    }

    private fun showAddMemberDialog(chatroomId: String) {
        val input = android.widget.EditText(this).apply {
            hint = "Username do novo participante"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Adicionar participante")
            .setView(input)
            .setPositiveButton("Buscar e Adicionar") { _, _ ->
                val username = input.text.toString().trim()
                if (username.length < 3) {
                    Toast.makeText(this, "Digite ao menos 3 caracteres", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addMemberByUsername(username, chatroomId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateUserStatusUi(user: UserModel) {
        // Linha principal: online/offline/last seen
        val connectionText = when (user.status) {
            "online" -> "Online"
            "busy"   -> "Ocupado"
            else -> if (user.lastSeen.isNotBlank())
                "Visto por último: ${formatLastSeen(user.lastSeen)}"
            else "Offline"
        }
        // Se tiver status message, adiciona na mesma linha separado por " · "
        val statusText = if (user.statusMessage.isNotBlank())
            "$connectionText · ${user.statusMessage}"
        else connectionText

        binding.otherUserStatus.text = statusText
        binding.otherUserStatus.visibility = android.view.View.VISIBLE
    }

    private fun formatLastSeen(lastSeen: String): String = try {
        val instant = java.time.Instant.parse(lastSeen)
        val local = instant.atZone(java.time.ZoneId.systemDefault())
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        if (local.toLocalDate() == now.toLocalDate())
            String.format("hoje às %02d:%02d", local.hour, local.minute)
        else
            String.format("%02d/%02d às %02d:%02d", local.dayOfMonth, local.monthValue, local.hour, local.minute)
    } catch (e: Exception) { "offline" }

    private fun setupRecyclerView() {
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
                // FIX: só chama filterMessages — o keyword é propagado pelo ViewModel
                // para cada UiModel, sem precisar tocar no adapter diretamente
                viewModel.filterMessages("")
            } else {
                binding.searchMessagesLayout.visibility = View.VISIBLE
                binding.searchMessagesInput.requestFocus()
            }
        }

        binding.searchMessagesInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterMessages(s.toString())
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

        // Atualiza toolbar quando status/last_seen do outro usuário mudar
        viewModel.otherUserLive.observe(this) { user ->
            updateUserStatusUi(user)
        }

        viewModel.pinnedMessage.observe(this) { pinned ->
            if (pinned != null) {
                binding.pinnedMessageLayout.visibility = View.VISIBLE
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
            .setMessage(if (uiModel.isPinned) "Deseja desafixar esta mensagem?"
                        else "Deseja fixar esta mensagem no topo do chat?")
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

    override fun onResume() {
        super.onResume()
        // Garante que mensagens recebidas em background apareçam e o canal realtime esteja ativo
        viewModel.refreshOnResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (e: Exception) { }
    }
}
