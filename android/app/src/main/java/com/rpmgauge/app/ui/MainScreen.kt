package com.rpmgauge.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpmgauge.app.MainViewModel
import com.rpmgauge.app.MeasurementSession
import com.rpmgauge.app.RpmAnalyzer
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onOpenSession: (String) -> Unit) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val rpm by viewModel.currentRpm.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.start()
        else viewModel.errorMessage =
            "RPM Gauge needs microphone access to measure engine RPM from sound frequency. " +
                "You can allow it in Settings › Apps › RPM Gauge › Permissions."
    }

    fun onStart() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("RPM Gauge") }) },
        bottomBar = {
            ControlBar(
                isRunning = isRunning,
                hasSelection = viewModel.selectedIds.isNotEmpty(),
                onStart = ::onStart,
                onStop = viewModel::stop,
                onDelete = viewModel::deleteSelected,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Live RPM display
            AnimatedVisibility(
                visible = isRunning,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                LiveRpmView(rpm)
            }

            // Blade count picker
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf(1, 2, 3).forEachIndexed { index, blades ->
                    SegmentedButton(
                        selected = viewModel.bladeCount == blades,
                        onClick = { viewModel.bladeCount = blades },
                        enabled = !isRunning,
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) {
                        Text(if (blades == 1) "1 blade" else "$blades blades")
                    }
                }
            }

            // Sessions list
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isSelected = session.id in viewModel.selectedIds,
                            onToggle = { viewModel.toggleSelection(session.id) },
                            onOpen = { onOpenSession(session.id) },
                        )
                        HorizontalDivider(Modifier.padding(start = 64.dp))
                    }
                }
                if (sessions.isEmpty() && !isRunning) {
                    EmptyState(Modifier.align(Alignment.Center))
                }
            }
        }
    }

    viewModel.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.errorMessage = null },
            confirmButton = { TextButton(onClick = { viewModel.errorMessage = null }) { Text("OK") } },
            title = { Text("Error") },
            text = { Text(message) },
        )
    }
}

fun rpmColor(rpm: Double, inRange: Color): Color = when {
    rpm < RpmAnalyzer.MIN_RPM -> BelowRangeOrange
    rpm > RpmAnalyzer.MAX_RPM -> AboveRangeRed
    else -> inRange
}

@Composable
private fun LiveRpmView(rpm: Double) {
    val color = if (rpm > 0) rpmColor(rpm, InRangeGreen) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (rpm > 0) "${formatRpm(rpm)} RPM" else "Listening…",
            color = color,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        if (rpm > 0) {
            val label = when {
                rpm < RpmAnalyzer.MIN_RPM -> "Below range"
                rpm > RpmAnalyzer.MAX_RPM -> "Above range"
                else -> "In range"
            }
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SessionRow(
    session: MeasurementSession,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Deselect" else "Select",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatDateTime(session.startTime, DateFormat.MEDIUM, DateFormat.MEDIUM),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                val caption = MaterialTheme.typography.bodySmall
                Icon(Icons.Filled.BarChart, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(formatRpm(session.averageRpm), style = caption)
                Text("  avg RPM  ·  ${session.readings.size} samples", style = caption, color = secondary)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.GraphicEq,
            contentDescription = null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text("No Measurements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(4.dp))
        Text(
            "Press Start to begin measuring RPM",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlBar(
    isRunning: Boolean,
    hasSelection: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControlButton("Start", Icons.Filled.PlayArrow, InRangeGreen, enabled = !isRunning, onStart, Modifier.weight(1f))
                ControlButton("Stop", Icons.Filled.Stop, AboveRangeRed, enabled = isRunning, onStop, Modifier.weight(1f))
                ControlButton("Delete", Icons.Filled.Delete, NeutralGray, enabled = hasSelection, onDelete, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1)
    }
}
