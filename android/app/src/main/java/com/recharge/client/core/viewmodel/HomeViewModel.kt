package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)

    private val _user = MutableStateFlow<CurrentUserResponse?>(null)
    val user = _user.asStateFlow()
    private val _wallet = MutableStateFlow<WalletResponse?>(null)
    val wallet = _wallet.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var loadJob: Job? = null
    private var walletJob: Job? = null
    private var requestGeneration = 0L

    fun load() {
        val generation = ++requestGeneration
        loadJob?.cancel()
        walletJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val userRequest = async { repository.currentUser() }
            val walletRequest = async { repository.wallet() }
            val userResult = userRequest.await()
            val walletResult = walletRequest.await()

            if (generation != requestGeneration) return@launch

            userResult.onSuccess { _user.value = it }
            walletResult.onSuccess { _wallet.value = it }
            _error.value = userResult.exceptionOrNull()?.message
                ?: walletResult.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun refreshWallet() {
        val generation = ++requestGeneration
        loadJob?.cancel()
        walletJob?.cancel()
        _loading.value = false
        walletJob = viewModelScope.launch {
            _error.value = null
            repository.wallet()
                .onSuccess {
                    if (generation == requestGeneration) _wallet.value = it
                }
                .onFailure {
                    if (generation == requestGeneration) _error.value = it.message
                }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        walletJob?.cancel()
        super.onCleared()
    }
}
