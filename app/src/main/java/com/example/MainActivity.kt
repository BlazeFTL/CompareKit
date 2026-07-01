package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ui.screens.CompareListScreen
import com.example.ui.screens.FileCompareScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CompareViewModel

class MainActivity : ComponentActivity() {
  
  private val compareViewModel: CompareViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    compareViewModel.loadTheme(applicationContext)
    enableEdgeToEdge()
    setContent {
      val appTheme by compareViewModel.appTheme.collectAsState()
      MyApplicationTheme(appTheme = appTheme) {
        val selectedFile by compareViewModel.selectedFile.collectAsState()
        val compareListState = rememberLazyListState()

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
              compareListState = compareListState,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            FileCompareScreen(
              viewModel = compareViewModel,
              modifier = Modifier.fillMaxSize()
            )
          }

          val exportProgress by compareViewModel.exportProgress.collectAsState()
          val exportProgressMsg by compareViewModel.exportProgressMsg.collectAsState()
          val isExportMinimized by compareViewModel.isExportMinimized.collectAsState()

          ExportProgressOverlay(
            progress = exportProgress,
            message = exportProgressMsg,
            minimized = isExportMinimized,
            onMinimizeChange = { compareViewModel.setExportMinimized(it) }
          )
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

@Composable
fun ExportProgressOverlay(
    progress: Float?,
    message: String,
    minimized: Boolean,
    onMinimizeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val percentage = (progress * 100).toInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = !minimized,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Generating Report...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = { onMinimizeChange(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Minimize", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "$percentage% completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = minimized,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onMinimizeChange(false) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Exporting: $percentage%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Restore",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
