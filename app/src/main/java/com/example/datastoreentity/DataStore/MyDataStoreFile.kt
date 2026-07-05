package com.example.datastoreentity.DataStore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appInfo")

@Singleton
class MyDataStoreFile @Inject constructor(
    @ApplicationContext private val context: Context
) {

    object PreferenceKeys {
        val COUNT = intPreferencesKey("count")
    }

    suspend fun saveCount(count: Int) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.COUNT] = count
            }
        }
    }

    fun readCountFlow(): Flow<Int> {
        return context.dataStore.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferenceKeys.COUNT] ?: 0
        }
    }

    suspend fun readCount(): Int {
        val preference = context.dataStore.data.first()
        return preference[PreferenceKeys.COUNT] ?: 0
    }

    suspend fun containsKey(): Boolean {
        val preference = context.dataStore.data.first()
        return preference.contains(PreferenceKeys.COUNT)
    }
}