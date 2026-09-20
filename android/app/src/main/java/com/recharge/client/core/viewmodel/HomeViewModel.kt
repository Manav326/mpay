package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)

    private val _user = MutableStateFlow<CurrentUserResponse?>(null)
    val user = _user.asStateFlow()
    private val _wallet = MutableStateFlow<WalletResponse?>(null)
    val wallet = _wallet.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val userResult = repository.currentUser()
            val walletResult = repository.wallet()
            userResult.onSuccess { _user.value = it }
            walletResult.onSuccess { _wallet.value = it }
            _error.value = userResult.exceptionOrNull()?.message
                ?: walletResult.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun refreshWallet() {
        if (_loading.value) return
        viewModelScope.launch {
            _error.value = null
            repository.wallet().onSuccess { _wallet.value = it }.onFailure { _error.value = it.message }
        }
    }
}
