package app.fieldwatch.room

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.Sighting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun RoomFindScreen(target: RoomRadio?, devices: List<Sighting>, onBack: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val enteredAt = remember(target?.key) { System.currentTimeMillis() }
    var sound by rememberSaveable(target?.key) { mutableStateOf(false) }
    var vibration by rememberSaveable(target?.key) { mutableStateOf(false) }
    val live = devices.firstOrNull { it.key == target?.key }
    val samples = live?.rssiHistory.orEmpty().filter { it.at > enteredAt && it.rssi in -126..-1 }.distinctBy { it.at }.sortedBy { it.at }.takeLast(40)
    val latest = samples.lastOrNull()
    val fresh = latest != null && now - latest.at in 0..(if (target?.kind == "BLE") 5000L else 45000L)
    val average = if (fresh) samples.takeLast(6).map { it.rssi }.average().toInt() else null
    val trend = RoomEngine.signalTrend(samples.map { it.at to it.rssi }, now, target?.kind.orEmpty())
    val audibleRssi by rememberUpdatedState(if (fresh) average else null)
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull() }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sound = false
                vibration = false
                tone?.stopTone()
                vibrator?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { tone?.release(); vibrator?.cancel() } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(300) } }
    LaunchedEffect(sound, vibration, target?.key) {
        while (sound || vibration) {
            val rssi = audibleRssi
            if (rssi == null) { delay(250); continue }
            if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
            if (vibration && vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            delay((1700L - (rssi + 100).coerceIn(0, 65) * 20L).coerceIn(300L, 1700L))
        }
    }
    val ink = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back to room") }
        Text("Find nearby", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(target?.title ?: "No device selected", style = MaterialTheme.typography.titleMedium)
        Text("${target?.kind.orEmpty()} • ${target?.mac.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        Text(if (average == null) "—" else "$average dBm", style = MaterialTheme.typography.displayLarge, color = if (fresh) ink else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(trend, style = MaterialTheme.typography.titleLarge)
        Text(if (latest == null) "Waiting for a reading received after you opened this screen." else "Last observed ${((now - latest.at).coerceAtLeast(0) / 1000)}s ago • ${if (fresh) "Fresh reading" else "Stale reading; feedback paused"}", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = { if (average == null) 0f else ((average + 100) / 65f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            if (samples.size >= 2) samples.zipWithNext().forEachIndexed { index, (a, b) ->
                fun y(value: Int) = size.height * (1f - ((value + 100) / 65f).coerceIn(0f, 1f))
                drawLine(ink, Offset(size.width * index / (samples.size - 1), y(a.rssi)), Offset(size.width * (index + 1) / (samples.size - 1), y(b.rssi)), 3.dp.toPx())
            }
        }
        Text("Watch the trend while carefully changing position. Strength can change with your grip, walls and reflections. The signal may come from outside this room.")
        if (target?.kind == "WIFI") Text("Wi-Fi updates are slower because Android limits scans. This is signal feedback, not an exact distance or direction.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Sound feedback"); Switch(checked = sound, onCheckedChange = { sound = it }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Vibration feedback"); Switch(checked = vibration, onCheckedChange = { vibration = it }) }
    }
}
