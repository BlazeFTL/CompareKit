package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diff.DiffOptions

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (relativePath: String, isSource: Boolean, content: String) -> Unit
) {
    var relativePath by remember { mutableStateOf("") }
    var isSource by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sandbox File") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = relativePath,
                    onValueChange = { relativePath = it },
                    label = { Text("Relative File Path (e.g., config.json)") },
                    placeholder = { Text("folder/file.ext") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Text("Create file inside:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSource, onClick = { isSource = true })
                    Text("Source Folder", modifier = Modifier.padding(end = 16.dp))

                    RadioButton(selected = !isSource, onClick = { isSource = false })
                    Text("Modified Folder")
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Initial Content") },
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (relativePath.isNotBlank()) {
                        onCreate(relativePath, isSource, content)
                    }
                },
                enabled = relativePath.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditFileDialog(
    filename: String,
    initialContent: String,
    isSource: Boolean,
    onDismiss: () -> Unit,
    onSave: (newContent: String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Editing $filename (${if (isSource) "Source" else "Modified"})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("Type file content here...") },
                    maxLines = Int.MAX_VALUE
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(content) }) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun DiffSettingsDialog(
    options: DiffOptions,
    beautifierEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (options: DiffOptions, beautifierEnabled: Boolean) -> Unit
) {
    var ignoreWhitespace by remember { mutableStateOf(options.ignoreWhitespace) }
    var ignoreEmptyLines by remember { mutableStateOf(options.ignoreEmptyLines) }
    var matchCase by remember { mutableStateOf(options.matchCase) }
    var beautifier by remember { mutableStateOf(beautifierEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comparison Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = ignoreWhitespace, onCheckedChange = { ignoreWhitespace = it })
                    Text("Ignore White Spaces", modifier = Modifier.padding(start = 8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = ignoreEmptyLines, onCheckedChange = { ignoreEmptyLines = it })
                    Text("Ignore Empty Lines & Breaks", modifier = Modifier.padding(start = 8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = !matchCase, onCheckedChange = { matchCase = !it })
                    Text("Ignore Character Case", modifier = Modifier.padding(start = 8.dp))
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = beautifier, onCheckedChange = { beautifier = it })
                    Text("Auto-Beautify Code (JSON & XML/HTML)", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        DiffOptions(
                            ignoreWhitespace = ignoreWhitespace,
                            ignoreEmptyLines = ignoreEmptyLines,
                            matchCase = matchCase
                        ),
                        beautifier
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
