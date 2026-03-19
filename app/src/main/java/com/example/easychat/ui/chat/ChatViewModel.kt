package com.example.easychat.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    private val localRepo: LocalMessageRepository = LocalMessageRepository(context)

    private val _chatroom = MutableLiveData<ChatroomModel?>()
    val chatroom: LiveData<ChatroomModel?> = _chatroom

    // Status do outro usuário em tempo real
    private val _otherUserLive = MutableLiveData<UserModel>(otherUser)
    val otherUserLive: LiveData<UserModel> = _otherUserLive

    private val _allMessages = mutableListOf<ChatMessageModel>()

    private val _messages = MutableLiveData<List<ChatMessageUiModel>>(emptyList())
    val messages: LiveData<List<ChatMessageUiModel>> = _messages

    private val _pinnedMessageUi = MutableLiveData<ChatMessageUiModel?>()
    val pinnedMessage: LiveData<ChatMessageUiModel?> = _pinnedMessageUi

    private val _messageSent = MutableLiveData<Boolean?>()
    val messageSent: LiveData<Boolean?> = _messageSent

    private val _isUploading = MutableLiveData<Boolean>(false)
    val isUploading: LiveData<Boolean> = _isUploading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var userStatusChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var currentFilter: String = ""
    private val currentUserId = SupabaseClientProvider.currentUserId()

    init {
        // Se otherUser.id está vazio, foi aberto como grupo via chatroomId —
        // setExistingChatroom() será chamado pela Activity logo após
        if (otherUser.id.isNotEmpty()) {
            loadOrCreateChatroom()
            subscribeToUserStatus()
        }
    }

    private fun loadOrCreateChatroom() {
        viewModelScope.launch {
            try {
                val room = chatRepository.getOrCreateDirectChatroom(currentUserId, otherUser.id)
                _chatroom.postValue(room)
                loadMessages(room.id)
                loadPinnedMessage(room.id)
                subscribeToRealtime(room.id)
            } catch (e: Exception) {
                _error.postValue("Erro ao carregar conversa: ${e.message}")
            }
        }
    }

    private suspend fun loadMessages(chatroomId: String) {
        // 1. Exibe cache local imediatamente — boa UX offline e evita tela em branco
        val cached = localRepo.getMessages(chatroomId)
        if (cached.isNotEmpty()) {
            _allMessages.clear()
            _allMessages.addAll(cached)
            publishMessages()
        }
        // 2. Busca remoto e atualiza
        try {
            val remote = chatRepository.getMessages(chatroomId)
            _allMessages.clear()
            _allMessages.addAll(remote)
            publishMessages()
            localRepo.saveMessages(remote)
        } catch (e: Exception) {
            android.util.Log.w("OFFLINE", "Sem conexão, usando cache: ${e.message}")
        }
    }

    private suspend fun loadPinnedMessage(chatroomId: String) {
        try {
            val pinned = chatRepository.getPinnedMessage(chatroomId)
            _pinnedMessageUi.postValue(pinned?.let { toUiModel(it) })
        } catch (e: Exception) { /* ignora offline */ }
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

                // Novas mensagens
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val newMsg = change.decodeRecord<ChatMessageModel>()
                    if (newMsg.chatroomId == chatroomId &&
                        _allMessages.none { it.id == newMsg.id }) {
                        _allMessages.add(0, newMsg)
                        publishMessages()
                        localRepo.saveMessage(newMsg)
                        if (newMsg.senderId != currentUserId) markAsRead(newMsg.id)
                    }
                }.launchIn(viewModelScope)

                // Updates de status (lida/entregue)
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val updated = change.decodeRecord<ChatMessageModel>()
                    val idx = _allMessages.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) {
                        _allMessages[idx] = updated
                        publishMessages()
                        localRepo.updateStatus(updated.id, updated.status)
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()

                // Recarrega mensagens APÓS o canal estar inscrito para garantir que
                // nenhuma mensagem chegada durante a conexão do WebSocket seja perdida
                loadMessages(chatroomId)
                markAllUnreadAsRead(chatroomId)

            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro no canal: ${e.message}")
            }
        }
    }

    /** Recarrega ao voltar pro app — garante mensagens recebidas em background */
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
        _messages.postValue(filtered.map { toUiModel(it) })
    }

    private fun toUiModel(msg: ChatMessageModel): ChatMessageUiModel {
        val isMe = msg.senderId == currentUserId
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
            statusSymbol     = when (msg.status) {
                "read", "delivered" -> "✓✓"
                else                -> "✓"
            },
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
                val unread = _allMessages.filter {
                    it.senderId != currentUserId && it.status != "read"
                }
                unread.forEach { chatRepository.markMessageAsRead(it.id) }
                unread.forEach { msg ->
                    val idx = _allMessages.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) _allMessages[idx] = msg.copy(status = "read")
                    localRepo.updateStatus(msg.id, "read")
                }
                if (unread.isNotEmpty()) publishMessages()
            } catch (e: Exception) { /* ignora offline */ }
        }
    }

    /** Escuta mudanças de status/last_seen do outro usuário em tempo real */
    private fun subscribeToUserStatus() {
        viewModelScope.launch {
            try {
                val channel = SupabaseClientProvider.realtime
                    .channel("user_status_${otherUser.id}")
                userStatusChannel = channel

                // Correção: Removendo o bloco filter problemático
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "users"
                }.onEach { change ->
                    val updated = change.decodeRecord<UserModel>()
                    // Verificamos manualmente se o ID é o que queremos
                    // para evitar problemas de compatibilidade com a DSL de filtro
                    if (updated.id == otherUser.id) {
                        _otherUserLive.postValue(updated)
                    }
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
                realtimeChannel?.let { SupabaseClientProvider.realtime.removeChannel(it) }
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
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar mensagem: ${e.message}")
                _messageSent.postValue(false)
            }
        }
    }

    fun sendImageMessage(uri: Uri, contentResolver: android.content.ContentResolver) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val url = mediaRepository.uploadImage(chatroomId, bytes)
                chatRepository.sendMessage(chatroomId, currentUserId, "", "image", mediaUrl = url)
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar imagem: ${e.message}")
            } finally { _isUploading.postValue(false) }
        }
    }

    fun sendAudioMessage(audioPath: String) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val bytes = java.io.File(audioPath).readBytes()
                val url = mediaRepository.uploadAudio(chatroomId, bytes)
                chatRepository.sendMessage(chatroomId, currentUserId, "", "audio", mediaUrl = url)
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar áudio: ${e.message}")
            } finally { _isUploading.postValue(false) }
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
                _messageSent.postValue(true)
            } catch (e: Exception) { _error.postValue("Erro ao enviar localização: ${e.message}") }
        }
    }

    fun togglePin(uiModel: ChatMessageUiModel) {
        val chatroomId = _chatroom.value?.id ?: return
        val message = uiModel.source
        viewModelScope.launch {
            try {
                val newPinState = !message.isPinned
                if (newPinState) {
                    _pinnedMessageUi.value?.source?.let { current ->
                        if (current.id != message.id) chatRepository.pinMessage(current.id, false)
                    }
                }
                chatRepository.pinMessage(message.id, newPinState)
                loadPinnedMessage(chatroomId)
            } catch (e: Exception) { _error.postValue("Erro ao fixar mensagem: ${e.message}") }
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

    private val _groupMembers = MutableLiveData<List<com.example.easychat.model.UserModel>>(emptyList())
    val groupMembers: LiveData<List<com.example.easychat.model.UserModel>> = _groupMembers

    fun loadGroupMembers() {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                val memberIds = chatRepository.getMembersOfChatroom(chatroomId).map { it.userId }
                val users = com.example.easychat.data.repository.UserRepository()
                    .getUsersByIds(memberIds)
                _groupMembers.postValue(users)
            } catch (e: Exception) { _error.postValue("Erro ao carregar membros: ${e.message}") }
        }
    }

    fun addMemberByUsername(username: String, chatroomId: String) {
        viewModelScope.launch {
            try {
                val users = com.example.easychat.data.repository.UserRepository().searchUsers(username)
                val user = users.firstOrNull { it.username.equals(username, ignoreCase = true) }
                if (user == null) {
                    _error.postValue("Usuário \"$username\" não encontrado")
                    return@launch
                }
                chatRepository.addMemberToChatroom(chatroomId, user.id)
                loadGroupMembers()
            } catch (e: Exception) {
                _error.postValue("Erro ao adicionar membro: ${e.message}")
            }
        }
    }

    fun removeGroupMember(userId: String) {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                chatRepository.removeMemberFromChatroom(chatroomId, userId)
                loadGroupMembers()
            } catch (e: Exception) { _error.postValue("Erro ao remover membro: ${e.message}") }
        }
    }

    /**
     * Usado quando a Activity já tem o chatroomId (grupos abertos da lista de recentes).
     * Cancela o loadOrCreateChatroom e carrega diretamente pelo id.
     */
    fun setExistingChatroom(chatroomId: String) {
        viewModelScope.launch {
            try {
                val room = chatRepository.getChatroomById(chatroomId) ?: return@launch
                _chatroom.postValue(room)
                loadMessages(chatroomId)
                loadPinnedMessage(chatroomId)
                subscribeToRealtime(chatroomId)
            } catch (e: Exception) {
                _error.postValue("Erro ao carregar grupo: ${e.message}")
            }
        }
    }

    fun clearError()       { _error.value = null }
    fun clearMessageSent() { _messageSent.value = null }
}
