package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.CompareListScreen
import com.example.ui.screens.FileCompareScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CompareViewModel

class MainActivity : ComponentActivity() {
  
  private val compareViewModel: CompareViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val selectedFile by compareViewModel.selectedFile.collectAsState()

        // Handle the system back gesture elegantly to exit the diff details view
        if (selectedFile != null) {
          BackHandler {
            compareViewModel.selectFileForDiff(null)
          }
        }

        // Initialize explorer on launch to load persistent SAF tree if it exists
        LaunchedEffect(Unit) {
          compareViewModel.initExplorer(applicationContext)
        }

        Box(modifier = Modifier.fillMaxSize()) {
          if (selectedFile == null) {
            CompareListScreen(
              viewModel = compareViewModel,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            FileCompareScreen(
              viewModel = compareViewModel,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // Perform cleanup of any temporary zip folders when exiting
    compareViewModel.cleanupTempFiles()
  }
}
