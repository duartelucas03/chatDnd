// ─────────────────────────────────────────────────────────────────────────────
// ui/chat/ChatViewModel.kt — apenas os campos MutableLiveData → MutableStateFlow
// (toda a lógica de negócio permanece idêntica ao original)
// ─────────────────────────────────────────────────────────────────────────────
package com.example.easychat.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.local.LocalMessageRepository
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.MediaRepository
import com.example.easychat.model.ChatMessageModel
import com.example.easychat.model.ChatMessageUiModel
import com.example.easychat.model.ChatroomModel
import com.example.easychat.model.UserModel
import com.example.easychat.utils.CryptoManager
import com.example.easychat.utils.SupabaseClientProvider
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class ChatViewModel(
    val otherUser: UserModel,
    context: android.content.Context,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val mediaRepository: MediaRepository = MediaRepository()
) : ViewModel() {

    private val localRepo = LocalMessageRepository(context)

    private val _chatroom = MutableStateFlow<ChatroomModel?>(null)
    val chatroom: StateFlow<ChatroomModel?> = _chatroom.asStateFlow()

    private val _otherUserLive = MutableStateFlow(otherUser)
    val otherUserLive: StateFlow<UserModel> = _otherUserLive.asStateFlow()

    private val _allMessages = mutableListOf<ChatMessageModel>()

    private val _messages = MutableStateFlow<List<ChatMessageUiModel>>(emptyList())
    val messages: StateFlow<List<ChatMessageUiModel>> = _messages.asStateFlow()

    private val _pinnedMessage = MutableStateFlow<ChatMessageUiModel?>(null)
    val pinnedMessage: StateFlow<ChatMessageUiModel?> = _pinnedMessage.asStateFlow()

    private val _messageSent = MutableStateFlow<Boolean?>(null)
    val messageSent: StateFlow<Boolean?> = _messageSent.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var userStatusChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var currentFilter: String = ""
    private val currentUserId = SupabaseClientProvider.currentUserId()

    init {
        if (otherUser.id.isNotEmpty()) {
            loadOrCreateChatroom()
            subscribeToUserStatus()
        }
    }

    private fun loadOrCreateChatroom() {
        viewModelScope.launch {
            try {
                val room = chatRepository.getOrCreateDirectChatroom(currentUserId, otherUser.id)
                _chatroom.value = room
                loadMessages(room.id)
                loadPinnedMessage(room.id)
                subscribeToRealtime(room.id)
            } catch (e: Exception) {
                _error.value = "Erro ao carregar conversa: ${e.message}"
            }
        }
    }

    private suspend fun loadMessages(chatroomId: String) {
        val cached = localRepo.getMessages(chatroomId)
        if (cached.isNotEmpty()) {
            _allMessages.clear(); _allMessages.addAll(cached); publishMessages()
        }
        try {
            val remote = chatRepository.getMessages(chatroomId)
            _allMessages.clear(); _allMessages.addAll(remote); publishMessages()
            localRepo.saveMessages(remote)
        } catch (e: Exception) {
            android.util.Log.w("OFFLINE", "Usando cache: ${e.message}")
        }
    }

    private suspend fun loadPinnedMessage(chatroomId: String) {
        try {
            _pinnedMessage.value = chatRepository.getPinnedMessage(chatroomId)?.let { toUiModel(it) }
        } catch (e: Exception) { }
    }

    private fun subscribeToRealtime(chatroomId: String) {
        viewModelScope.launch {
            try {
                realtimeChannel?.let {
                    try { SupabaseClientProvider.realtime.removeChannel(it) } catch (e: Exception) { }
                }
                realtimeChannel = null
                val channel = SupabaseClientProvider.realtime
                    .channel("messages_${chatroomId}_${System.currentTimeMillis()}")
                realtimeChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val newMsg = change.decodeRecord<ChatMessageModel>()
                    if (newMsg.chatroomId == chatroomId && _allMessages.none { it.id == newMsg.id }) {
                        _allMessages.add(0, newMsg)
                        publishMessages()
                        localRepo.saveMessage(newMsg)
                        if (newMsg.senderId != currentUserId) markAsRead(newMsg.id)
                    }
                }.launchIn(viewModelScope)

                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val updated = change.decodeRecord<ChatMessageModel>()
                    val idx = _allMessages.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) {
                        _allMessages[idx] = updated; publishMessages()
                        localRepo.updateStatus(updated.id, updated.status)
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()
                loadMessages(chatroomId)
                markAllUnreadAsRead(chatroomId)
            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro no canal: ${e.message}")
            }
        }
    }

    fun refreshOnResume() {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                loadMessages(chatroomId)
                if (realtimeChannel == null) subscribeToRealtime(chatroomId)
            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro ao reconectar: ${e.message}")
            }
        }
    }

    private fun publishMessages() {
        val filtered = if (currentFilter.isBlank()) _allMessages.toList()
        else _allMessages.filter {
            CryptoManager.decrypt(it.content ?: "").contains(currentFilter, ignoreCase = true)
        }
        _messages.value = filtered.map { toUiModel(it) }
    }

    private fun toUiModel(msg: ChatMessageModel): ChatMessageUiModel {
        val isMe          = msg.senderId == currentUserId
        val decryptedText = if (msg.type == "text" || msg.type == null)
            CryptoManager.decrypt(msg.content ?: "") else null
        return ChatMessageUiModel(
            id               = msg.id,
            localId          = msg.localId,
            isFromMe         = isMe,
            type             = msg.type ?: "text",
            text             = decryptedText,
            mediaUrl         = msg.mediaUrl,
            locationLat      = msg.locationLat,
            locationLng      = msg.locationLng,
            timeFormatted    = formatTime(msg.createdAt),
            statusSymbol     = if (msg.status in listOf("read", "delivered")) "✓✓" else "✓",
            showStatus       = isMe,
            isPinned         = msg.isPinned,
            highlightKeyword = currentFilter,
            source           = msg
        )
    }

    private fun formatTime(createdAt: String): String = try {
        val instant = Instant.parse(createdAt)
        val local   = instant.atZone(ZoneId.systemDefault())
        String.format("%02d:%02d", local.hour, local.minute)
    } catch (e: Exception) { "" }

    private fun markAllUnreadAsRead(chatroomId: String) {
        viewModelScope.launch {
            try {
                val unread = _allMessages.filter { it.senderId != currentUserId && it.status != "read" }
                unread.forEach { chatRepository.markMessageAsRead(it.id) }
                unread.forEach { msg ->
                    val idx = _allMessages.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) _allMessages[idx] = msg.copy(status = "read")
                    localRepo.updateStatus(msg.id, "read")
                }
                if (unread.isNotEmpty()) publishMessages()
            } catch (e: Exception) { }
        }
    }

    private fun subscribeToUserStatus() {
        viewModelScope.launch {
            try {
                val channel = SupabaseClientProvider.realtime.channel("user_status_${otherUser.id}")
                userStatusChannel = channel
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "users"
                }.onEach { change ->
                    val updated = change.decodeRecord<UserModel>()
                    if (updated.id == otherUser.id) _otherUserLive.value = updated
                }.launchIn(viewModelScope)
                channel.subscribe()
            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro status user: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                realtimeChannel?.let  { SupabaseClientProvider.realtime.removeChannel(it) }
                userStatusChannel?.let { SupabaseClientProvider.realtime.removeChannel(it) }
            } catch (e: Exception) { }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    chatroomId = chatroomId,
                    senderId   = currentUserId,
                    content    = CryptoManager.encrypt(text),
                    type       = "text"
                )
                _messageSent.value = true
            } catch (e: Exception) {
                _error.value = "Erro ao enviar mensagem: ${e.message}"
                _messageSent.value = false
            }
        }
    }

    fun sendImageMessage(uri: Uri, contentResolver: android.content.ContentResolver) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val url   = mediaRepository.uploadImage(chatroomId, bytes)
                chatRepository.sendMessage(chatroomId, currentUserId, "", "image", mediaUrl = url)
                _messageSent.value = true
            } catch (e: Exception) {
                _error.value = "Erro ao enviar imagem: ${e.message}"
            } finally { _isUploading.value = false }
        }
    }

    fun sendAudioMessage(audioPath: String) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val bytes = java.io.File(audioPath).readBytes()
                val url   = mediaRepository.uploadAudio(chatroomId, bytes)
                chatRepository.sendMessage(chatroomId, currentUserId, "", "audio", mediaUrl = url)
                _messageSent.value = true
            } catch (e: Exception) {
                _error.value = "Erro ao enviar áudio: ${e.message}"
            } finally { _isUploading.value = false }
        }
    }

    fun sendVideoMessage(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val url   = mediaRepository.uploadVideo(chatroomId, bytes)
                chatRepository.sendMessage(chatroomId, currentUserId, "", "video", mediaUrl = url)
                _messageSent.value = true
            } catch (e: Exception) {
                _error.value = "Erro ao enviar vídeo: ${e.message}"
            } finally { _isUploading.value = false }
        }
    }

    fun sendLocationMessage(lat: Double, lng: Double) {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    chatroomId  = chatroomId,
                    senderId    = currentUserId,
                    content     = "📍 Localização",
                    type        = "location",
                    locationLat = lat,
                    locationLng = lng
                )
                _messageSent.value = true
            } catch (e: Exception) { _error.value = "Erro ao enviar localização: ${e.message}" }
        }
    }

    fun togglePin(uiModel: ChatMessageUiModel) {
        val chatroomId = _chatroom.value?.id ?: return
        val message    = uiModel.source
        viewModelScope.launch {
            try {
                val newPinState = !message.isPinned
                if (newPinState) {
                    _pinnedMessage.value?.source?.let { current ->
                        if (current.id != message.id) chatRepository.pinMessage(current.id, false)
                    }
                }
                chatRepository.pinMessage(message.id, newPinState)
                loadPinnedMessage(chatroomId)
            } catch (e: Exception) { _error.value = "Erro ao fixar mensagem: ${e.message}" }
        }
    }

    fun filterMessages(keyword: String) {
        currentFilter = keyword
        publishMessages()
    }

    fun markAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.markMessageAsRead(messageId)
                localRepo.updateStatus(messageId, "read")
            } catch (e: Exception) { }
        }
    }

    private val _groupMembers = MutableStateFlow<List<UserModel>>(emptyList())
    val groupMembers: StateFlow<List<UserModel>> = _groupMembers.asStateFlow()

    fun loadGroupMembers() {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                val memberIds = chatRepository.getMembersOfChatroom(chatroomId).map { it.userId }
                _groupMembers.value = com.example.easychat.data.repository.UserRepository()
                    .getUsersByIds(memberIds)
            } catch (e: Exception) { _error.value = "Erro ao carregar membros: ${e.message}" }
        }
    }

    fun addMemberByUsername(username: String, chatroomId: String) {
        viewModelScope.launch {
            try {
                val users = com.example.easychat.data.repository.UserRepository().searchUsers(username)
                val user  = users.firstOrNull { it.username.equals(username, ignoreCase = true) }
                if (user == null) { _error.value = "Usuário \"$username\" não encontrado"; return@launch }
                chatRepository.addMemberToChatroom(chatroomId, user.id)
                loadGroupMembers()
            } catch (e: Exception) { _error.value = "Erro ao adicionar membro: ${e.message}" }
        }
    }

    fun removeGroupMember(userId: String) {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                chatRepository.removeMemberFromChatroom(chatroomId, userId)
                loadGroupMembers()
            } catch (e: Exception) { _error.value = "Erro ao remover membro: ${e.message}" }
        }
    }

    fun setExistingChatroom(chatroomId: String) {
        viewModelScope.launch {
            try {
                val room = chatRepository.getChatroomById(chatroomId) ?: return@launch
                _chatroom.value = room
                loadMessages(chatroomId)
                loadPinnedMessage(chatroomId)
                subscribeToRealtime(chatroomId)
            } catch (e: Exception) { _error.value = "Erro ao carregar grupo: ${e.message}" }
        }
    }

    fun clearError()       { _error.value = null }
    fun clearMessageSent() { _messageSent.value = null }
}