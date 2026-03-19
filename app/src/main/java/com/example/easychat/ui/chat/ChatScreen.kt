package com.example.easychat.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.easychat.R
import com.example.easychat.model.ChatMessageUiModel
import com.example.easychat.model.UserModel
import com.example.easychat.ui.compose.components.MessageBubble
import com.example.easychat.ui.compose.components.UserAvatar
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.gms.location.LocationServices
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.foundation.layout.imePadding
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatroomId: String?,
    onBack: () -> Unit
) {
    val context          = LocalContext.current
    val messages         by viewModel.messages.collectAsStateWithLifecycle()
    val otherUserLive    by viewModel.otherUserLive.collectAsStateWithLifecycle()
    val pinnedMessage    by viewModel.pinnedMessage.collectAsStateWithLifecycle()
    val messageSent      by viewModel.messageSent.collectAsStateWithLifecycle()
    val isUploading      by viewModel.isUploading.collectAsStateWithLifecycle()
    val error            by viewModel.error.collectAsStateWithLifecycle()
    val groupMembers     by viewModel.groupMembers.collectAsStateWithLifecycle()

    var inputText         by remember { mutableStateOf("") }
    var showSearch        by remember { mutableStateOf(false) }
    var searchKeyword     by remember { mutableStateOf("") }
    var isRecording       by remember { mutableStateOf(false) }
    var audioPath         by remember { mutableStateOf("") }
    var mediaRecorder     by remember { mutableStateOf<MediaRecorder?>(null) }
    var showPinDialog     by remember { mutableStateOf<ChatMessageUiModel?>(null) }
    var showGroupDialog   by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // ─── Launchers de permissão / mídia ────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
            viewModel.sendImageMessage(uri, context.contentResolver)
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendLocation(context, viewModel)
        else android.widget.Toast.makeText(context, "Permissão de localização negada", android.widget.Toast.LENGTH_SHORT).show()
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val result = toggleRecording(context, isRecording, mediaRecorder, audioPath)
            isRecording   = result.isRecording
            audioPath     = result.audioPath
            mediaRecorder = result.recorder
            if (!result.isRecording && result.audioPath.isNotBlank()) {
                viewModel.sendAudioMessage(result.audioPath)
            }
        } else android.widget.Toast.makeText(context, "Permissão de microfone negada", android.widget.Toast.LENGTH_SHORT).show()
    }

    // ─── Reações aos estados ────────────────────────────────────────────────
    LaunchedEffect(messageSent) {
        if (messageSent == true) { inputText = ""; viewModel.clearMessageSent() }
    }

    LaunchedEffect(error) {
        error ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.clearError()
    }

    // Scroll automático ao receber nova mensagem (lista invertida, idx = 0)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // ─── Dialogs ────────────────────────────────────────────────────────────
    showPinDialog?.let { msg ->
        PinDialog(
            isPinned = msg.isPinned,
            onConfirm = { viewModel.togglePin(msg); showPinDialog = null },
            onDismiss = { showPinDialog = null }
        )
    }

    if (showGroupDialog) {
        GroupMembersDialog(
            members    = groupMembers,
            onAdd      = { showAddMemberDialog = true; showGroupDialog = false },
            onRemove   = { userId -> viewModel.removeGroupMember(userId) },
            onLeave    = { viewModel.removeGroupMember(com.example.easychat.utils.SupabaseClientProvider.currentUserId()); onBack() },
            onDismiss  = { showGroupDialog = false }
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            chatroomId = viewModel.chatroom.value?.id ?: "",
            onAdd      = { username, cid -> viewModel.addMemberByUsername(username, cid) },
            onDismiss  = { showAddMemberDialog = false }
        )
    }

    // ─── Scaffold principal ──────────────────────────────────────────────────
    Scaffold(
        topBar = {
            ChatTopBar(
                otherUser  = otherUserLive,
                isGroup    = chatroomId != null,
                onBack     = onBack,
                onSearch   = { showSearch = !showSearch; if (!showSearch) { searchKeyword = ""; viewModel.filterMessages("") } },
                onManageGroup = { viewModel.loadGroupMembers(); showGroupDialog = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            // ── Mensagem fixada ──────────────────────────────────────────────
            pinnedMessage?.let { pinned ->
                PinnedMessageBanner(
                    text    = when (pinned.type) {
                        "image"    -> "📷 Foto"
                        "audio"    -> "🎵 Áudio"
                        "location" -> "📍 Localização"
                        else       -> pinned.text ?: ""
                    },
                    onClose = { viewModel.togglePin(pinned) }
                )
            }

            // ── Barra de busca (toggle) ───────────────────────────────────────
            if (showSearch) {
                OutlinedTextField(
                    value         = searchKeyword,
                    onValueChange = { searchKeyword = it; viewModel.filterMessages(it) },
                    placeholder   = { Text("Buscar nas mensagens") },
                    singleLine    = true,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ── Upload progressbar ───────────────────────────────────────────
            if (isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Lista de mensagens ───────────────────────────────────────────
            LazyColumn(
                modifier      = Modifier.weight(1f).fillMaxWidth(),
                state         = listState,
                reverseLayout = true,   // mesmo comportamento do RecyclerView original
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = messages, key = { it.id }) { msg ->
                    MessageRow(
                        msg         = msg,
                        onLongClick = { showPinDialog = msg }
                    )
                }
            }

            // ── Barra de input ───────────────────────────────────────────────
            InputBar(
                text      = inputText,
                onTextChange = { inputText = it },
                isRecording  = isRecording,
                onSend = {
                    if (inputText.trim().isNotEmpty()) viewModel.sendTextMessage(inputText.trim())
                },
                onAttachImage = {
                    val perm = if (android.os.Build.VERSION.SDK_INT >= 33)
                        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                        ImagePicker.with(context as androidx.activity.ComponentActivity)
                            .cropSquare().compress(1024).maxResultSize(1080, 1080)
                            .createIntent { imagePickerLauncher.launch(it) }
                    } else {
                        (context as? androidx.activity.ComponentActivity)
                            ?.requestPermissions(arrayOf(perm), 100)
                    }
                },
                onRecordAudio = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val result = toggleRecording(context, isRecording, mediaRecorder, audioPath)
                        isRecording   = result.isRecording
                        audioPath     = result.audioPath
                        mediaRecorder = result.recorder
                        if (!result.isRecording && result.audioPath.isNotBlank()) {
                            viewModel.sendAudioMessage(result.audioPath)
                        }
                    } else {
                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSendLocation = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ) sendLocation(context, viewModel)
                    else locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables locais
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    otherUser: UserModel,
    isGroup: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onManageGroup: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(avatarUrl = otherUser.avatarUrl, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(otherUser.username, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (!isGroup) {
                        val statusText = buildStatusText(otherUser)
                        if (statusText.isNotBlank()) {
                            Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.icon_search), contentDescription = "Buscar mensagens")
            }
            if (isGroup) {
                IconButton(onClick = onManageGroup) {
                    Icon(painterResource(R.drawable.ic_group), contentDescription = "Gerenciar grupo")
                }
            }
        }
    )
}

private fun buildStatusText(user: UserModel): String {
    val connectionText = when (user.status) {
        "online" -> "Online"
        "busy"   -> "Ocupado"
        else     -> if (user.lastSeen.isNotBlank()) "Visto: ${formatLastSeen(user.lastSeen)}" else "Offline"
    }
    return if (user.statusMessage.isNotBlank()) "$connectionText · ${user.statusMessage}" else connectionText
}

private fun formatLastSeen(lastSeen: String): String = try {
    val instant = Instant.parse(lastSeen)
    val local   = instant.atZone(ZoneId.systemDefault())
    val now     = LocalDateTime.now(ZoneId.systemDefault())
    if (local.toLocalDate() == now.toLocalDate())
        "hoje às %02d:%02d".format(local.hour, local.minute)
    else
        "%02d/%02d às %02d:%02d".format(local.dayOfMonth, local.monthValue, local.hour, local.minute)
} catch (e: Exception) { "offline" }

@Composable
private fun PinnedMessageBanner(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.send_icon), contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text  = text.ifBlank { "[Mensagem]" },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Desafixar",
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(msg: ChatMessageUiModel, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(onLongClick = onLongClick, onClick = {})
        ) {
            when (msg.type) {
                "image" -> ImageMessage(msg)
                "audio" -> AudioMessage(msg)
                "location" -> LocationMessage(msg)
                else -> MessageBubble(
                    text             = msg.text,
                    timeFormatted    = msg.timeFormatted,
                    statusSymbol     = msg.statusSymbol,
                    showStatus       = msg.showStatus,
                    isFromMe         = msg.isFromMe,
                    highlightKeyword = msg.highlightKeyword
                )
            }
        }
    }
}

@Composable
private fun ImageMessage(msg: ChatMessageUiModel) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!msg.mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(msg.mediaUrl).crossfade(true).build(),
                contentDescription = "Imagem",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = msg.timeFormatted,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
        )
    }
}

@Composable
private fun AudioMessage(msg: ChatMessageUiModel) {
    var isPlaying by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (msg.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (!isPlaying && !msg.mediaUrl.isNullOrBlank()) {
                    isPlaying = true
                    try {
                        val player = android.media.MediaPlayer()
                        player.setDataSource(msg.mediaUrl)
                        player.prepareAsync()
                        player.setOnPreparedListener { it.start() }
                        player.setOnCompletionListener { it.release(); isPlaying = false }
                    } catch (e: Exception) { isPlaying = false }
                }
            }) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Parar" else "Reproduzir",
                    tint = if (msg.isFromMe) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("🎵 Áudio", fontSize = 13.sp,
                color = if (msg.isFromMe) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(msg.timeFormatted, fontSize = 10.sp,
                color = (if (msg.isFromMe) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LocationMessage(msg: ChatMessageUiModel) {
    val locText = if (msg.locationLat != null && msg.locationLng != null)
        "📍 ${"%.5f".format(msg.locationLat)}, ${"%.5f".format(msg.locationLng)}"
    else "📍 Localização"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (msg.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(locText, fontSize = 13.sp,
                color = if (msg.isFromMe) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(msg.timeFormatted, fontSize = 10.sp,
                color = (if (msg.isFromMe) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isRecording: Boolean,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onRecordAudio: () -> Unit,
    onSendLocation: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachImage) {
                Icon(painterResource(R.drawable.ic_image), contentDescription = "Imagem")
            }
            IconButton(onClick = onRecordAudio) {
                Icon(
                    painter = painterResource(if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = if (isRecording) "Parar gravação" else "Gravar áudio",
                    tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
            IconButton(onClick = onSendLocation) {
                Icon(painterResource(R.drawable.ic_location), contentDescription = "Localização")
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Mensagem") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = text.trim().isNotEmpty(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (text.trim().isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Icon(
                    painterResource(R.drawable.icon_send),
                    contentDescription = "Enviar",
                    tint = if (text.trim().isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Dialogs de grupo ────────────────────────────────────────────────────────

@Composable
private fun PinDialog(isPinned: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(if (isPinned) "Desafixar mensagem" else "Fixar mensagem") },
        text    = { Text(if (isPinned) "Deseja desafixar esta mensagem?"
        else "Deseja fixar esta mensagem no topo do chat?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GroupMembersDialog(
    members: List<UserModel>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentUserId = com.example.easychat.utils.SupabaseClientProvider.currentUserId()
    var confirmRemove by remember { mutableStateOf<UserModel?>(null) }
    var confirmLeave  by remember { mutableStateOf(false) }

    confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remover ${member.username}?") },
            confirmButton = { TextButton(onClick = { onRemove(member.id); confirmRemove = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Sair do grupo?") },
            text  = { Text("Você não poderá mais ver as mensagens deste grupo.") },
            confirmButton = { TextButton(onClick = { onLeave(); confirmLeave = false }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grupo · ${members.size} participante(s)") },
        text = {
            Column {
                members.forEach { member ->
                    val isSelf = member.id == currentUserId
                    TextButton(
                        onClick = { if (!isSelf) confirmRemove = member },
                        enabled = !isSelf
                    ) {
                        Text(if (isSelf) "✓ ${member.username} (Você)" else member.username)
                    }
                }
                Divider()
                TextButton(onClick = onAdd) { Text("➕ Adicionar participante") }
                TextButton(onClick = { confirmLeave = true }) {
                    Text("🚪 Sair do grupo", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun AddMemberDialog(
    chatroomId: String,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar participante") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("Username do novo participante") },
                    isError = error != null,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (username.trim().length < 3) { error = "Digite ao menos 3 caracteres"; return@TextButton }
                onAdd(username.trim(), chatroomId)
                onDismiss()
            }) { Text("Buscar e Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ─── Funções auxiliares de gravação e localização (lógica = original) ────────

private data class RecordingState(
    val isRecording: Boolean,
    val audioPath: String,
    val recorder: MediaRecorder?
)

private fun toggleRecording(
    context: android.content.Context,
    isRecording: Boolean,
    recorder: MediaRecorder?,
    currentPath: String
): RecordingState {
    return if (isRecording) {
        // Para gravação
        try { recorder?.stop(); recorder?.release() } catch (e: Exception) { }
        android.widget.Toast.makeText(context, "Áudio gravado", android.widget.Toast.LENGTH_SHORT).show()
        RecordingState(isRecording = false, audioPath = currentPath, recorder = null)
    } else {
        // Inicia gravação
        val path = "${context.externalCacheDir?.absolutePath}/audio_${System.currentTimeMillis()}.m4a"
        val mr   = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(path)
            prepare()
            start()
        }
        android.widget.Toast.makeText(context, "Gravando...", android.widget.Toast.LENGTH_SHORT).show()
        RecordingState(isRecording = true, audioPath = path, recorder = mr)
    }
}

@SuppressLint("MissingPermission")
private fun sendLocation(context: android.content.Context, viewModel: ChatViewModel) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation.addOnSuccessListener { location ->
        if (location != null) viewModel.sendLocationMessage(location.latitude, location.longitude)
        else android.widget.Toast.makeText(context, "Não foi possível obter localização", android.widget.Toast.LENGTH_SHORT).show()
    }
}