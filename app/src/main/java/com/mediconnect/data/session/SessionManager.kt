package com.mediconnect.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediconnect.data.model.UserResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mediconnect_session")

/**
 * Manages authenticated session — stores token and user profile
 * persistently via DataStore.
 */
class SessionManager(private val context: Context) {

    companion object {
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    private val USER_FIRST_NAME_KEY = stringPreferencesKey("user_first_name")
    private val USER_LAST_NAME_KEY = stringPreferencesKey("user_last_name")

    /** Observe the auth token as a Flow */
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }

    /** Observe the user's full name as a Flow */
    val userNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        val first = prefs[USER_FIRST_NAME_KEY] ?: return@map null
        val last = prefs[USER_LAST_NAME_KEY] ?: ""
        "$first $last"
    }

    /** Check synchronously if user is logged in */
    suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.first()[TOKEN_KEY] != null
    }

    /** Get the stored auth token (suspend) */
    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    /** Save auth session after login/register */
    suspend fun saveSession(token: String, user: UserResponse) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_ID_KEY] = user.id
            prefs[USER_EMAIL_KEY] = user.email
            prefs[USER_FIRST_NAME_KEY] = user.firstName
            prefs[USER_LAST_NAME_KEY] = user.lastName
        }
    }

    /** Clear session on logout */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
