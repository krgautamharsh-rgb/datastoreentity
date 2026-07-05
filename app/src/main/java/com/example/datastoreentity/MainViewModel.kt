package com.example.datastoreentity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datastoreentity.DataStore.DataStore_Temp
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val datastoreTemp: DataStore_Temp
): ViewModel() {

    // One-shot read: call from any coroutine
    suspend fun getCurrentCount(): Int {
        return datastoreTemp.readCount()
    }

     fun setKey(num : Int){
         viewModelScope.launch(Dispatchers.IO) {
             datastoreTemp.saveCount(num)
         }
    }

}