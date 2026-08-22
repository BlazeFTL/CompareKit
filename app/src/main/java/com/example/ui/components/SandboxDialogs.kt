package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diff.DiffOptions
import com.example.file.DexCompareOptions

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
        title = { Text("Create New File") },
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
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
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
    lineHeightMultiplier: Float = 1.40f,
    isDecompiledApk: Boolean = false,
    dexOptions: DexCompareOptions = DexCompareOptions(),
    onDismiss: () -> Unit,
    onSave: (options: DiffOptions, beautifierEnabled: Boolean, dexOptions: DexCompareOptions, lineHeightMultiplier: Float) -> Unit
) {
    var ignoreWhitespace by remember { mutableStateOf(options.ignoreWhitespace) }
    var ignoreEmptyLines by remember { mutableStateOf(options.ignoreEmptyLines) }
    var matchCase by remember { mutableStateOf(options.matchCase) }
    var beautifier by remember { mutableStateOf(beautifierEnabled) }
    var currentLineHeight by remember { mutableStateOf(lineHeightMultiplier) }
    var ignoreDebugInfo by remember { mutableStateOf(dexOptions.ignoreDebugInfo) }
    var ignoreCompilationOptimizations by remember { mutableStateOf(dexOptions.ignoreCompilationOptimizations) }
    var ignoreNopInstruction by remember { mutableStateOf(dexOptions.ignoreNopInstruction) }
    var ignoreRegisterCount by remember { mutableStateOf(dexOptions.ignoreRegisterCount) }
    var ignoreFieldInitialValues by remember { mutableStateOf(dexOptions.ignoreFieldInitialValues) }
    var isPresetsExpanded by remember { mutableStateOf(false) }

    val densityLabel = when {
        currentLineHeight <= 0.95f -> "Dense"
        currentLineHeight <= 1.20f -> "Compact"
        currentLineHeight <= 1.50f -> "Normal"
        currentLineHeight <= 1.80f -> "Comfortable"
        else -> "Spacious"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* prevent dismiss */ }
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Comparison Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.5.sp
                                )
                                Text(
                                    text = "Configure diff rules & editor display",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION 1: DIFF COMPARISON
                    Text(
                        text = "DIFF COMPARISON",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    SettingsSwitchRow(
                        title = "Ignore White Spaces",
                        subtitle = "Skip leading/trailing spaces & tabs",
                        checked = ignoreWhitespace,
                        onCheckedChange = { ignoreWhitespace = it }
                    )

                    SettingsSwitchRow(
                        title = "Ignore Empty Lines & Breaks",
                        subtitle = "Skip blank line additions and deletions",
                        checked = ignoreEmptyLines,
                        onCheckedChange = { ignoreEmptyLines = it }
                    )

                    SettingsSwitchRow(
                        title = "Ignore Character Case",
                        subtitle = "Treat lowercase and UPPERCASE as equal",
                        checked = !matchCase,
                        onCheckedChange = { matchCase = !it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // SECTION 2: EDITOR & VIEW DISPLAY
                    Text(
                        text = "EDITOR & VIEW DISPLAY",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    SettingsSwitchRow(
                        title = "Auto-Beautify Code",
                        subtitle = "Format minified JSON, XML & HTML before diffing",
                        checked = beautifier,
                        onCheckedChange = { beautifier = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // LINE SPACING WITH DEFAULT VISIBLE SLIDER & VERTICAL EXPANDABLE PRESETS
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            // Header Row with current status and expand toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isPresetsExpanded = !isPresetsExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Line Spacing / Height",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Current: ${String.format(java.util.Locale.US, "%.2fx", currentLineHeight)} ($densityLabel)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.clickable { isPresetsExpanded = !isPresetsExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Presets",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (isPresetsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand presets",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // SLIDER IS VISIBLE BY DEFAULT
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = currentLineHeight,
                                onValueChange = { currentLineHeight = (kotlin.math.round(it * 20f) / 20f) },
                                valueRange = 0.80f..2.20f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0.80x (Dense)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "2.20x (Spacious)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // VERTICAL EXPANDABLE PRESET LIST
                            AnimatedVisibility(
                                visible = isPresetsExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val presets = listOf(
                                        Triple("Dense", 0.90f, "Maximum code lines on screen"),
                                        Triple("Compact", 1.15f, "Tight single-line spacing"),
                                        Triple("Normal", 1.40f, "Recommended default comfortable height"),
                                        Triple("Comfortable", 1.65f, "Spacious line padding for readability"),
                                        Triple("Spacious", 1.95f, "Generous room between code lines")
                                    )

                                    presets.forEach { (label, value, desc) ->
                                        val isSelected = kotlin.math.abs(currentLineHeight - value) < 0.05f
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { currentLineHeight = value },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        if (isSelected) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Column {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = desc,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 10.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.padding(start = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "${String.format(java.util.Locale.US, "%.2f", value)}x",
                                                        style = TextStyle(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isDecompiledApk) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Text(
                            text = "APK & DEX / SMALI COMPARISON",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        SettingsSwitchRow(
                            title = "Ignore Debug Info",
                            subtitle = "Skip line numbers, local tables & source debug info",
                            checked = ignoreDebugInfo,
                            onCheckedChange = { ignoreDebugInfo = it }
                        )

                        SettingsSwitchRow(
                            title = "Ignore Compilation Optimization",
                            subtitle = "Skip synthetic helpers & compiler bytecode variations",
                            checked = ignoreCompilationOptimizations,
                            onCheckedChange = { ignoreCompilationOptimizations = it }
                        )

                        SettingsSwitchRow(
                            title = "Ignore NOP Instructions",
                            subtitle = "Skip padding & alignment nop bytecodes",
                            checked = ignoreNopInstruction,
                            onCheckedChange = { ignoreNopInstruction = it }
                        )

                        SettingsSwitchRow(
                            title = "Ignore Register Count",
                            subtitle = "Skip register count differences in methods",
                            checked = ignoreRegisterCount,
                            onCheckedChange = { ignoreRegisterCount = it }
                        )

                        SettingsSwitchRow(
                            title = "Ignore Field Default Values",
                            subtitle = "Skip default/null field initializers in Smali",
                            checked = ignoreFieldInitialValues,
                            onCheckedChange = { ignoreFieldInitialValues = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                onSave(
                                    DiffOptions(
                                        ignoreWhitespace = ignoreWhitespace,
                                        ignoreEmptyLines = ignoreEmptyLines,
                                        matchCase = matchCase
                                    ),
                                    beautifier,
                                    dexOptions.copy(
                                        ignoreDebugInfo = ignoreDebugInfo,
                                        ignoreCompilationOptimizations = ignoreCompilationOptimizations,
                                        ignoreNopInstruction = ignoreNopInstruction,
                                        ignoreRegisterCount = ignoreRegisterCount,
                                        ignoreFieldInitialValues = ignoreFieldInitialValues
                                    ),
                                    currentLineHeight
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun DecompiledApkOptionsDialog(
    currentOptions: DexCompareOptions,
    onDismiss: () -> Unit,
    onConfirm: (DexCompareOptions) -> Unit
) {
    var ignoreDebugInfo by remember { mutableStateOf(currentOptions.ignoreDebugInfo) }
    var ignoreCompilationOptimizations by remember { mutableStateOf(currentOptions.ignoreCompilationOptimizations) }
    var ignoreNopInstruction by remember { mutableStateOf(currentOptions.ignoreNopInstruction) }
    var ignoreRegisterCount by remember { mutableStateOf(currentOptions.ignoreRegisterCount) }
    var ignoreFieldInitialValues by remember { mutableStateOf(currentOptions.ignoreFieldInitialValues) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "APK, DEX & Smali Options",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Configure DEX and Smali comparison rules:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                SettingsSwitchRow(
                    title = "Ignore Debug Info",
                    subtitle = "Skip line numbers, local tables & debug info",
                    checked = ignoreDebugInfo,
                    onCheckedChange = { ignoreDebugInfo = it }
                )
                
                SettingsSwitchRow(
                    title = "Ignore Compilation Optimization",
                    subtitle = "Skip synthetic helpers & bytecode variations",
                    checked = ignoreCompilationOptimizations,
                    onCheckedChange = { ignoreCompilationOptimizations = it }
                )
                
                SettingsSwitchRow(
                    title = "Ignore NOP Instructions",
                    subtitle = "Skip padding & alignment nop bytecodes",
                    checked = ignoreNopInstruction,
                    onCheckedChange = { ignoreNopInstruction = it }
                )
                
                SettingsSwitchRow(
                    title = "Ignore Register Count",
                    subtitle = "Skip register count differences in methods",
                    checked = ignoreRegisterCount,
                    onCheckedChange = { ignoreRegisterCount = it }
                )

                SettingsSwitchRow(
                    title = "Ignore Field Default Values",
                    subtitle = "Skip default/null field initializers in Smali",
                    checked = ignoreFieldInitialValues,
                    onCheckedChange = { ignoreFieldInitialValues = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        currentOptions.copy(
                            ignoreDebugInfo = ignoreDebugInfo,
                            ignoreCompilationOptimizations = ignoreCompilationOptimizations,
                            ignoreNopInstruction = ignoreNopInstruction,
                            ignoreRegisterCount = ignoreRegisterCount,
                            ignoreFieldInitialValues = ignoreFieldInitialValues
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Comparison", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

