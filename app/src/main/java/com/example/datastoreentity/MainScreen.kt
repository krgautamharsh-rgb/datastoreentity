package com.example.datastoreentity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier) {

    val viewModel = hiltViewModel<MainViewModel>()
    var num by remember { mutableStateOf(0) }
    var prefValue by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        prefValue = viewModel.getCurrentCount()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Text("Compose State - $num")
            Text("Preference Value - $prefValue")
            Button(
                onClick = {
                    num++
                    viewModel.setKey(num)
                }

            ) {
                Text("Add")
            }
        }
    }
}