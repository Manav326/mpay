package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
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
    private var loadGeneration = 0L
    private var walletGeneration = 0L


    fun resetSession() {
        loadJob?.cancel()
        walletJob?.cancel()
        loadJob = null
        walletJob = null
        loadGeneration++
        walletGeneration++
        viewModelScope.coroutineContext.cancelChildren()
        _user.value = null
        _wallet.value = null
        _loading.value = false
        _error.value = null
    }


    fun load() {
        val loadGenerationAtStart = ++loadGeneration
        val walletGenerationAtStart = ++walletGeneration
        loadJob?.cancel()
        walletJob?.cancel()

        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val userRequest = async { repository.currentUser() }
            val walletRequest = async { repository.wallet() }
            val userResult = userRequest.await()
            val walletResult = walletRequest.await()

            if (loadGenerationAtStart != loadGeneration) return@launch

            userResult.onSuccess { _user.value = it }
            if (walletGenerationAtStart == walletGeneration) {
                walletResult.onSuccess { _wallet.value = it }
            }
            _error.value = userResult.exceptionOrNull()?.message
                ?: walletResult.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun refreshWallet() {
        val generation = ++walletGeneration
        walletJob?.cancel()
        _error.value = null
        walletJob = viewModelScope.launch {
            repository.wallet()
                .onSuccess {
                    if (generation == walletGeneration) _wallet.value = it
                }
                .onFailure {
                    if (generation == walletGeneration) _error.value = it.message
                }
        }
    }
}
