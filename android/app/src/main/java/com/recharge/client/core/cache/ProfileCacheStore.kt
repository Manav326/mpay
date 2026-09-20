package com.recharge.client.core.cache

import android.content.Context
import com.google.gson.Gson
import com.recharge.client.core.model.CurrentUserResponse

class ProfileCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(user: CurrentUserResponse) {
        prefs.edit().putString(KEY_USER, gson.toJson(user)).apply()
    }

    fun get(): CurrentUserResponse? = prefs.getString(KEY_USER, null)?.let {
        runCatching { gson.fromJson(it, CurrentUserResponse::class.java) }.getOrNull()
    }

    fun clear() { prefs.edit().clear().apply() }

    companion object {
        private const val PREFS = "profile_cache"
        private const val KEY_USER = "current_user"
    }
}
