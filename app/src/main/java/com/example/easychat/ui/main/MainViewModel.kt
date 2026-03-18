package com.example.easychat.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.ChatroomModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

class MainViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _recentChats = MutableLiveData<List<ChatroomModel>>(emptyList())
    val recentChats: LiveData<List<ChatroomModel>> = _recentChats

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    fun loadRecentChats() {
        _loading.value = true
        viewModelScope.launch {
            try {
                val chats = chatRepository.getRecentChats(SupabaseClientProvider.currentUserId())
                _recentChats.postValue(chats)
            } catch (e: Exception) {
                _recentChats.postValue(emptyList())
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try {
                userRepository.updateFcmToken(token)
            } catch (e: Exception) { /* silencioso */ }
        }
    }

    fun setUserOnline() {
        viewModelScope.launch {
            try {
                userRepository.updateStatus("online")
            } catch (e: Exception) { /* silencioso */ }
        }
    }

    fun setUserOffline() {
        viewModelScope.launch {
            try {
                userRepository.updateStatus("offline")
            } catch (e: Exception) { /* silencioso */ }
        }
    }
}
