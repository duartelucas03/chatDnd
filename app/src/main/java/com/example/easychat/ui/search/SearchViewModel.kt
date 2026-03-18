package com.example.easychat.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import kotlinx.coroutines.launch

class SearchViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _results = MutableLiveData<List<UserModel>>(emptyList())
    val results: LiveData<List<UserModel>> = _results

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    fun searchUsers(term: String) {
        if (term.length < 3) return
        _loading.value = true
        viewModelScope.launch {
            val users = userRepository.searchUsers(term)
            _results.postValue(users)
            _loading.postValue(false)
        }
    }
}
