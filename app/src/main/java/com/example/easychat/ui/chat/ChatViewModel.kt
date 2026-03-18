package com.example.easychat.ui.chat

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.MediaRepository
import com.example.easychat.model.ChatMessageModel
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

class ChatViewModel(
    val otherUser: UserModel,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val mediaRepository: MediaRepository = MediaRepository()
) : ViewModel() {

    private val _chatroom = MutableLiveData<ChatroomModel?>()
    val chatroom: LiveData<ChatroomModel?> = _chatroom

    private val _allMessages = MutableLiveData<List<ChatMessageModel>>(emptyList())

    private val _messages = MutableLiveData<List<ChatMessageModel>>(emptyList())
    val messages: LiveData<List<ChatMessageModel>> = _messages

    private val _pinnedMessage = MutableLiveData<ChatMessageModel?>()
    val pinnedMessage: LiveData<ChatMessageModel?> = _pinnedMessage

    private val _messageSent = MutableLiveData<Boolean?>()
    val messageSent: LiveData<Boolean?> = _messageSent

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    private val _isUploading = MutableLiveData<Boolean>(false)
    val isUploading: LiveData<Boolean> = _isUploading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentFilter: String = ""

    init {
        loadOrCreateChatroom()
    }

    private fun loadOrCreateChatroom() {
        viewModelScope.launch {
            try {
                val room = chatRepository.getOrCreateDirectChatroom(
                    currentUserId = SupabaseClientProvider.currentUserId(),
                    otherUserId = otherUser.id
                )
                _chatroom.postValue(room)
                loadMessages(room.id)
                loadPinnedMessage(room.id)
                subscribeToRealtime(room.id)
            } catch (e: Exception) {
                android.util.Log.e("CHAT_ERROR", "Erro ao carregar chat: ${e.message}", e)
                _error.postValue("Erro ao carregar conversa: ${e.message}")
            }
        }
    }

    private suspend fun loadMessages(chatroomId: String) {
        val remote = chatRepository.getMessages(chatroomId)
        _allMessages.value = remote
        _messages.value = remote
    }

    private suspend fun loadPinnedMessage(chatroomId: String) {
        _pinnedMessage.postValue(chatRepository.getPinnedMessage(chatroomId))
    }



    private fun subscribeToRealtime(chatroomId: String) {
        viewModelScope.launch {
            try {
                // Remove canal anterior se existir
                realtimeChannel?.let {
                    SupabaseClientProvider.realtime.removeChannel(it)
                }

                // Cria novo canal com nome único por sessão
                val channel = SupabaseClientProvider.realtime
                    .channel("messages_${chatroomId}_${System.currentTimeMillis()}")
                realtimeChannel = channel

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { change ->
                    val newMsg = change.decodeRecord<ChatMessageModel>()
                    if (newMsg.chatroomId == chatroomId) {
                        val current = _allMessages.value.orEmpty().toMutableList()
                        if (current.none { it.id == newMsg.id }) {
                            current.add(0, newMsg)
                            _allMessages.value = current
                            _messages.value = if (currentFilter.isBlank()) current
                            else current.filter {
                                it.content?.contains(currentFilter, ignoreCase = true) == true
                            }
                        }
                    }
                }.launchIn(viewModelScope)

                channel.subscribe()
            } catch (e: Exception) {
                android.util.Log.e("REALTIME", "Erro no canal: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.let {
                SupabaseClientProvider.realtime.removeChannel(it)
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val chatroomId = _chatroom.value?.id ?: return

        viewModelScope.launch {
            try {
                val encryptedText = CryptoManager.encrypt(text)
                chatRepository.sendMessage(
                    chatroomId = chatroomId,
                    senderId = SupabaseClientProvider.currentUserId(),
                    content = encryptedText,
                    type = "text"
                )
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar mensagem: ${e.message}")
                _messageSent.postValue(false)
            }
        }
    }

    // Req. 7 + Req. 15 (câmera)
    fun sendImageMessage(uri: Uri, contentResolver: android.content.ContentResolver) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true

        viewModelScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val url = mediaRepository.uploadImage(chatroomId, bytes)
                chatRepository.sendMessage(
                    chatroomId = chatroomId,
                    senderId = SupabaseClientProvider.currentUserId(),
                    content = "",
                    type = "image",
                    mediaUrl = url
                )
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar imagem: ${e.message}")
            } finally {
                _isUploading.postValue(false)
            }
        }
    }

    // Req. 15 (microfone)
    fun sendAudioMessage(audioPath: String) {
        val chatroomId = _chatroom.value?.id ?: return
        _isUploading.value = true

        viewModelScope.launch {
            try {
                val bytes = java.io.File(audioPath).readBytes()
                val url = mediaRepository.uploadAudio(chatroomId, bytes)
                chatRepository.sendMessage(
                    chatroomId = chatroomId,
                    senderId = SupabaseClientProvider.currentUserId(),
                    content = "",
                    type = "audio",
                    mediaUrl = url
                )
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar áudio: ${e.message}")
            } finally {
                _isUploading.postValue(false)
            }
        }
    }

    // Req. 15 (GPS)
    fun sendLocationMessage(lat: Double, lng: Double) {
        val chatroomId = _chatroom.value?.id ?: return

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    chatroomId = chatroomId,
                    senderId = SupabaseClientProvider.currentUserId(),
                    content = "📍 Localização",
                    type = "location",
                    locationLat = lat,
                    locationLng = lng
                )
                _messageSent.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Erro ao enviar localização: ${e.message}")
            }
        }
    }

    // Req. 13 — PIN
    fun togglePin(message: ChatMessageModel) {
        val chatroomId = _chatroom.value?.id ?: return
        viewModelScope.launch {
            try {
                val newPinState = !message.isPinned
                if (newPinState) {
                    _pinnedMessage.value?.let { currentPinned ->
                        if (currentPinned.id != message.id) {
                            chatRepository.pinMessage(currentPinned.id, false)
                        }
                    }
                }
                chatRepository.pinMessage(message.id, newPinState)
                loadPinnedMessage(chatroomId)
            } catch (e: Exception) {
                _error.postValue("Erro ao fixar mensagem: ${e.message}")
            }
        }
    }

    // Req. 14 — FILTRO POR PALAVRA-CHAVE
    fun filterMessages(keyword: String) {
        currentFilter = keyword
        applyFilter()
    }

    private fun applyFilter() {
        val all = _allMessages.value.orEmpty()
        _messages.postValue(
            if (currentFilter.isBlank()) all
            else all.filter { it.content?.contains(currentFilter, ignoreCase = true) == true  }
        )
    }

    // Req. 3 — marcar como lida
    fun markAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.markMessageAsRead(messageId)
            } catch (e: Exception) { /* silencioso */ }
        }
    }

    fun clearError() { _error.value = null }
    fun clearMessageSent() { _messageSent.value = null }
}