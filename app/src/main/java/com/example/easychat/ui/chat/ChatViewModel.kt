package com.example.easychat.ui.chat

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val chatRepository: ChatRepository = ChatRepository(),
    private val mediaRepository: MediaRepository = MediaRepository()
) : ViewModel() {

    private val _chatroom = MutableLiveData<ChatroomModel?>()
    val chatroom: LiveData<ChatroomModel?> = _chatroom

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
    private var currentFilter: String = ""
    private val currentUserId = SupabaseClientProvider.currentUserId()

    init { loadOrCreateChatroom() }

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
        val remote = chatRepository.getMessages(chatroomId)
        _allMessages.clear()
        _allMessages.addAll(remote)
        publishMessages()
    }

    private suspend fun loadPinnedMessage(chatroomId: String) {
        val pinned = chatRepository.getPinnedMessage(chatroomId)
        _pinnedMessageUi.postValue(pinned?.let { toUiModel(it) })
    }

    private fun subscribeToRealtime(chatroomId: String) {
        viewModelScope.launch {
            try {
                realtimeChannel?.let { SupabaseClientProvider.realtime.removeChannel(it) }
                val channel = SupabaseClientProvider.realtime
                    .channel("messages_${chatroomId}_${System.currentTimeMillis()}")
                realtimeChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val newMsg = change.decodeRecord<ChatMessageModel>()
                    if (newMsg.chatroomId == chatroomId &&
                        _allMessages.none { it.id == newMsg.id }) {
                        _allMessages.add(0, newMsg)
                        publishMessages()
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()
            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro no canal: ${e.message}")
            }
        }
    }

    /**
     * Converte domínio → UiModels aplicando filtro, descriptografia, formatação
     * e — FIX — inclui o keyword atual em cada UiModel para que o DiffUtil
     * detecte a mudança de keyword e force o redesenho do highlight.
     */
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
            statusSymbol     = when (msg.status) { "read", "delivered" -> "✓✓"; else -> "✓" },
            showStatus       = isMe,
            isPinned         = msg.isPinned,
            // FIX: keyword incluída no UiModel — quando muda, areContentsTheSame
            // retorna false e o DiffUtil força onBindViewHolder para todos os itens
            highlightKeyword = currentFilter,
            source           = msg
        )
    }

    private fun formatTime(createdAt: String): String = try {
        val instant = Instant.parse(createdAt)
        val local   = instant.atZone(ZoneId.systemDefault())
        String.format("%02d:%02d", local.hour, local.minute)
    } catch (e: Exception) { "" }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try { realtimeChannel?.let { SupabaseClientProvider.realtime.removeChannel(it) } }
            catch (e: Exception) { /* ignora */ }
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
            try { chatRepository.markMessageAsRead(messageId) } catch (e: Exception) { }
        }
    }

    fun clearError()       { _error.value = null }
    fun clearMessageSent() { _messageSent.value = null }
}
