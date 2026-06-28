package com.example

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.DuoResult
import com.example.data.CalculationEntity
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DuoCalculatorApp()
            }
        }
    }
}

@Composable
fun DuoCalculatorApp() {
    val viewModel: DuoViewModel = viewModel()
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val partialSpeechText by viewModel.partialSpeechText.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val isSpeechSupported by viewModel.isSpeechSupported.collectAsState()

    var typedQuery by remember { mutableStateOf("") }

    // Speech permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startListening()
            } else {
                Toast.makeText(context, "ভয়েস ইনপুটের জন্য অডিও রেকর্ডিং পারমিশন প্রয়োজন।", Toast.LENGTH_LONG).show()
            }
        }
    )

    val checkPermissionAndListen = {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (isListening) {
                viewModel.stopListening()
            } else {
                viewModel.startListening()
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Helper to copy result to clipboard
    val copyToClipboard = { label: String, text: String ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Interactive glowing Assistant Microphone controller
            VoiceControllerSection(
                isListening = isListening,
                partialText = partialSpeechText,
                isSpeechSupported = isSpeechSupported,
                onMicClick = { checkPermissionAndListen() },
                onCancelClick = { viewModel.cancelListening() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Header Section
            HeaderSection(
                ttsEnabled = ttsEnabled,
                onTtsToggle = { viewModel.toggleTts(it) },
                onClearHistory = { viewModel.clearAllHistory() }
            )

            // 2. Typing Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = typedQuery,
                    onValueChange = { typedQuery = it },
                    placeholder = { Text("এখানে লিখে হিসাব করুন (যেমন: ২৫০ গ্রাম আলু)...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("query_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (typedQuery.trim().isNotEmpty()) {
                                viewModel.processQuery(typedQuery)
                                typedQuery = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        if (typedQuery.isNotEmpty()) {
                            IconButton(onClick = { typedQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear text")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (typedQuery.trim().isNotEmpty()) {
                            viewModel.processQuery(typedQuery)
                            typedQuery = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .size(48.dp)
                        .testTag("send_query_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Calculate",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // 3. Quick Scenario Chips
            QuickScenarioChips { presetQuery ->
                viewModel.processQuery(presetQuery)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Main Scrollable Panel (Active calculation + History)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active / Latest Result Card
                item {
                    ActiveResultPanel(
                        uiState = uiState,
                        onSpeakAgain = { text -> viewModel.speak(text) },
                        onCopyToClipboard = { label, text -> copyToClipboard(label, text) }
                    )
                }

                // History Title & List
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "হিসাবের ইতিহাস (${history.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }

                    items(history, key = { it.id }) { item ->
                        HistoryCard(
                            entity = item,
                            onFavoriteClick = { viewModel.toggleFavorite(item) },
                            onDeleteClick = { viewModel.deleteHistoryItem(item.id) },
                            onSpeakClick = { viewModel.speak(item.spokenResponse) },
                            onCopyClick = { text -> copyToClipboard("Duo Calculation", text) }
                        )
                    }
                } else if (uiState is UiState.Idle) {
                    // Custom Empty state
                    item {
                        EmptyStatePanel()
                    }
                }

                // Extra padding at bottom for voice controller
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    ttsEnabled: Boolean,
    onTtsToggle: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "D",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Duo",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            Text(
                text = "ভয়েস বিজনেস ক্যালকুলেটর",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Speaker / Voice Feedback Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Voice feedback active",
                    tint = if (ttsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = ttsEnabled,
                    onCheckedChange = onTtsToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete History Bin
            IconButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear all logs",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun QuickScenarioChips(onPresetClick: (String) -> Unit) {
    val presets = listOf(
        "৩ টি ৩.৫ করে এবং ৮ টি ২.৫ করে",
        "১৩০ টাকা কেজি দরে ৬০০ গ্রাম আলু",
        "১৩০ টাকা কেজি দরে ১০০ টাকার কত গ্রাম",
        "২০০ টাকা কিলো আলু হলে ২৫০ গ্রাম এর দাম কত"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "উদাহরণ (ট্যাপ করে চেক করুন):",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
            ),
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .clickable { onPresetClick(preset) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveResultPanel(
    uiState: UiState,
    onSpeakAgain: (String) -> Unit,
    onCopyToClipboard: (String, String) -> Unit
) {
    AnimatedVisibility(
        visible = uiState != UiState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Header accent ribbon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "নতুন হিসাবের রসিদ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    when (uiState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "হিসাব কষা হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Error icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is UiState.Success -> {
                            val result = uiState.result
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Spoken Response / Greeting
                                Text(
                                    text = result.spokenResponse,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Custom serrated shop receipt background for calculations
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "হিসাবের বিশদ বিবরণ (Receipt Breakdown):",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = result.calculationSteps,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Divider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "সর্বমোট পরিমাণ:",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = result.resultValue,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Quick action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onSpeakAgain(result.spokenResponse) },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Hear response again",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("শুনুন", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onCopyToClipboard("Duo Calculation Result", "${result.spokenResponse}\n\nবিশদ বিবরণ: ${result.calculationSteps}") },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text("কপি করুন", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing visual ring for empty state
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice assistant",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "স্বাগতম! আমি ডুও (Duo)",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "নিচের বড় সবুজ বাটনটি ট্যাপ করে আপনার হিসাব বলুন, অথবা উপরে টাইপ করে দিন। আমি হিসেব করে সঠিক উত্তর বাংলায় বুঝিয়ে দেব!",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun VoiceControllerSection(
    isListening: Boolean,
    partialText: String,
    isSpeechSupported: Boolean,
    onMicClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    // Beautiful infinite pulsing animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "Scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(top = 16.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Real-time transcribed text display
            if (isListening || partialText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isListening) {
                    // Cancel button during active listing
                    IconButton(
                        onClick = onCancelClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancel speech",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }

                // Glowing Mic Action Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(scale)
                ) {
                    // Pulsating outer glow ring
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (isListening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        if (isListening) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .clickable { onMicClick() }
                            .testTag("microphone_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = if (isListening) "Listening active" else "Start speech calculation",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (isListening) {
                    Spacer(modifier = Modifier.width(76.dp)) // Equal balance offset
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    entity: CalculationEntity,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onCopyClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_card_${entity.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Date, Favorite icon, Speak icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(entity.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Favorite Toggle star
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (entity.isFavorite) Icons.Default.Star else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle favorite status",
                            tint = if (entity.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Speak audio replay
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Replay audio response",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete item
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete item",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Query Text (Speech style)
            Text(
                text = "“${entity.queryText}”",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calculation and spoken response summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .clickable { onCopyClick("${entity.spokenResponse}\nবিশদ: ${entity.calculationSteps}") }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = entity.calculationSteps,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entity.spokenResponse,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// -------------------------------------------------------------
// COMPATIBILITY HELPER FOR PRE-EXISTING SCREENSHOT TESTS
// -------------------------------------------------------------
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
