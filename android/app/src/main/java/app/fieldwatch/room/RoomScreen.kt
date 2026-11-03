package app.fieldwatch.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.FieldwatchUi
import java.text.DateFormat
import java.util.Date

private fun time(at: Long): String = if (at <= 0) "Not recorded" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))

@Composable
fun RoomScreen(ui: RoomUi, scanner: FieldwatchUi, vm: RoomViewModel, onFind: (RoomRadio) -> Unit, onScannerSettings: () -> Unit) {
    val room = ui.room
    var create by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<RoomRadio?>(null) }
    var nearbyExpanded by rememberSaveable(room?.id) { mutableStateOf(false) }
    var missingExpanded by rememberSaveable(room?.id) { mutableStateOf(false) }
    val active = ui.session != null
    val report = ui.session?.finish(ui.now, false) ?: ui.report
    val comparison = if (room != null && report != null) RoomEngine.comparison(room, report) else null
    val healthy = scanner.permissionsOk && scanner.scanning && !scanner.scanBlocked && scanner.locationOn && !scanner.settings.demoMode

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("ROOM CHANGE WATCH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(room?.name ?: "Know what changed.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Learn a room. Label familiar devices. Review new signals.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { choose = true }, enabled = !active && ui.archive.rooms.isNotEmpty()) { Text("Switch room") }
                TextButton(onClick = { create = true }, enabled = ui.ready && !active && ui.archive.rooms.size < 30) { Text("+ New room") }
                TextButton(onClick = { help = true }) { Text("How it works") }
            }
        }
        if (!ui.ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.message?.let { message -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp)) { Text(message); TextButton(onClick = { vm.notice(null) }) { Text("Dismiss") } }
            }
        } }
        if (room == null && ui.ready) item {
            Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Start with one room", style = MaterialTheme.typography.titleLarge)
                Text("Stand in a consistent spot. Keep familiar equipment on, then learn the room for 90 seconds. You review the labels before trusting them.")
                Button(onClick = { create = true }) { Text("Create your first room") }
            } }
        }
        if (room != null) {
            item {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (healthy) "Scanner ready" else "Scanner needs attention", style = MaterialTheme.typography.titleMedium, color = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(when {
                        scanner.settings.demoMode -> "Demo mode is on. Turn it off before learning or checking a real room."
                        !scanner.permissionsOk -> "Grant location and nearby-device permissions."
                        !scanner.locationOn -> "Turn on system Location, Wi-Fi and Bluetooth."
                        !scanner.scanning || scanner.scanBlocked -> "Turn on Wi-Fi and Bluetooth and check scanner diagnostics."
                        else -> "Stay in the same spot. Wi-Fi updates follow Android scan limits."
                    }, style = MaterialTheme.typography.bodySmall)
                    if (scanner.throttleHint.isNotBlank()) Text(scanner.throttleHint, style = MaterialTheme.typography.labelSmall)
                    if (!healthy) TextButton(onClick = onScannerSettings) { Text("Open scanner settings") }
                    Text("Signal filter: ${room.settings.minRssi} dBm • ${room.settings.durationSeconds}s check", style = MaterialTheme.typography.labelMedium)
                    if (active) {
                        val session = ui.session!!
                        Text(if (session.learning) "Learning room…" else "Checking for changes…", style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(progress = { ((ui.now - session.startedAt).toFloat() / (session.endsAt - session.startedAt)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${((session.endsAt - ui.now).coerceAtLeast(0) + 999) / 1000}s remaining • ${report?.radios?.size ?: 0} identifiers observed")
                        OutlinedButton(onClick = { vm.finish(false, "Stopped early. Run a full check for a complete comparison.") }) { Text("Stop check") }
                    } else {
                        Button(onClick = { vm.start(room.baselineAt == 0L) }, enabled = healthy && ui.ready, modifier = Modifier.fillMaxWidth()) {
                            Text(if (room.baselineAt == 0L) "Learn this room" else "Check room")
                        }
                        if (room.baselineAt > 0) {
                            Text("Baseline saved ${time(room.baselineAt)}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { replace = true }, enabled = healthy) { Text("Learn again") }
                                TextButton(onClick = { history = true }) { Text("Check history") }
                            }
                        }
                        TextButton(onClick = { settings = true }) { Text("Room settings & labels") }
                    }
                } }
            }
            item {
                Text("Signals can pass through walls. This filter reduces clutter; it cannot establish which room a radio is inside.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (report != null) {
                item {
                    Text(when { active -> "Live observations"; !report.complete -> "Incomplete check"; report.learning -> "Review your learning scan"; else -> "Check results" }, style = MaterialTheme.typography.titleLarge)
                    if (!active) Text("${time(report.startedAt)} • ${report.radios.size} observed identifiers", style = MaterialTheme.typography.bodySmall)
                    if (report.note.isNotBlank()) Text(report.note, color = MaterialTheme.colorScheme.error)
                    if (report.radios.isEmpty()) Text("No fresh readings. This is not evidence that the room is clear. Check radio settings or try again.")
                    if (report.learning && report.complete && !active) {
                        val n = RoomEngine.baselineCandidates(report, room.settings).size
                        Text("$n identifiers passed the repetition and signal filters. Saving keeps your existing labels for matching identifiers.")
                        Button(onClick = { vm.saveBaseline() }, enabled = n > 0) { Text("Save these as baseline") }
                    }
                }
                if (comparison != null) {
                    item { SectionTitle("Changes to review", comparison.changes.size, "New repeated signals and changed Wi-Fi security. These are clues, not threats.") }
                    if (comparison.changes.isEmpty()) item { Text(if (active) "Waiting for repeated signals…" else "No changes met the current filters. This is not a security clearance.", style = MaterialTheme.typography.bodySmall) }
                    items(comparison.changes, key = { "change:${it.radio.key}" }) { entry -> RoomRadioCard(entry, !active) { edit = entry.radio } }
                    item { SectionTitle("Known room devices", comparison.known.size, "Matches your saved baseline. Unidentified entries still need your review.") }
                    items(comparison.known, key = { "known:${it.radio.key}" }) { entry -> RoomRadioCard(entry, !active) { edit = entry.radio } }
                    item { TextButton(onClick = { missingExpanded = !missingExpanded }) { Text("${if (missingExpanded) "▾" else "▸"} Not observed this time (${comparison.missing.size})") } }
                    if (missingExpanded) items(comparison.missing, key = { "missing:${it.radio.key}" }) { entry -> RoomRadioCard(entry, !active) { edit = entry.radio } }
                    item { TextButton(onClick = { nearbyExpanded = !nearbyExpanded }) { Text("${if (nearbyExpanded) "▾" else "▸"} Nearby / uncertain (${comparison.nearby.size})") } }
                    if (nearbyExpanded) items(comparison.nearby, key = { "nearby:${it.radio.key}" }) { entry -> RoomRadioCard(entry, !active) { edit = entry.radio } }
                }
            } else if (room.baseline.isNotEmpty()) {
                item { SectionTitle("Saved room baseline", room.baseline.size, "Tap an entry to label it, mark background, or find its current signal.") }
                items(room.baseline.sortedBy { it.title }, key = { "baseline:${it.key}" }) { radio -> RoomRadioCard(RoomEntry(radio, "Saved baseline • ${time(radio.lastSeen)}"), true) { edit = radio } }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (create || rename) {
        var name by remember(create, rename) { mutableStateOf(if (rename) room?.name.orEmpty() else "") }
        AlertDialog(onDismissRequest = { create = false; rename = false }, title = { Text(if (rename) "Rename room" else "Create a room") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Room name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (rename) vm.renameRoom(name) else vm.createRoom(name); create = false; rename = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { create = false; rename = false }) { Text("Cancel") } })
    }
    if (choose) AlertDialog(onDismissRequest = { choose = false }, title = { Text("Your rooms") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { ui.archive.rooms.forEach { item -> TextButton(onClick = { vm.selectRoom(item.id); choose = false }) { Text(item.name) } } }
    }, confirmButton = { TextButton(onClick = { choose = false }) { Text("Close") } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("A repeatable room check") }, text = {
        Text("1. Learn from a consistent spot with normal equipment on.\n\n2. Save the baseline and label devices you recognize. Mark neighboring signals as background.\n\n3. Check from the same spot later. Review new signals separately.\n\n4. Tap a result and use Find nearby. Stronger signal is a search clue, never an exact position.\n\nPrivate Bluetooth addresses can change. No detection does not mean no device. Room labels are your assignments, not verified identities.")
    }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } })
    if (replace) AlertDialog(onDismissRequest = { replace = false }, title = { Text("Learn this room again?") }, text = { Text("Your current baseline stays until you finish learning and explicitly save a replacement. Matching identifiers keep their labels. Scan from the same spot.") }, confirmButton = { TextButton(onClick = { replace = false; vm.start(true) }) { Text("Start learning") } }, dismissButton = { TextButton(onClick = { replace = false }) { Text("Cancel") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this room?") }, text = { Text("Its baseline, room labels and saved checks will be removed from this app. Advanced scanner logs are separate.") }, confirmButton = { TextButton(onClick = { delete = false; vm.deleteRoom() }) { Text("Delete room") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (settings && room != null) {
        var threshold by remember { mutableFloatStateOf(room.settings.minRssi.toFloat()) }
        var duration by remember { mutableIntStateOf(room.settings.durationSeconds) }
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Room settings") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Minimum signal: ${threshold.toInt()} dBm")
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = -100f..-40f, steps = 11)
                Text("Stronger filters hide more signals, including devices inside the room. Default: −75 dBm.", style = MaterialTheme.typography.bodySmall)
                Text("Check duration")
                Row { listOf(60, 90, 180).forEach { seconds -> FilterChip(selected = duration == seconds, onClick = { duration = seconds }, label = { Text("${seconds}s") }, modifier = Modifier.padding(end = 6.dp)) } }
                Text("New Bluetooth identifiers need 3 readings; Wi-Fi needs 2 distinct scan timestamps. Weak or private addresses stay uncertain.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { settings = false; rename = true }) { Text("Rename room") }
                TextButton(onClick = { settings = false; delete = true }) { Text("Delete room and its checks") }
            }
        }, confirmButton = { TextButton(onClick = { vm.settings(room.settings.copy(minRssi = threshold.toInt(), durationSeconds = duration)); settings = false }) { Text("Save settings") } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("Cancel") } })
    }
    if (history && room != null) AlertDialog(onDismissRequest = { history = false }, title = { Text("Recent checks") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (room.reports.isEmpty()) Text("No saved checks yet.")
            room.reports.forEach { saved -> TextButton(onClick = { vm.showReport(saved); history = false }) { Text("${time(saved.startedAt)}\n${if (saved.learning) "Learning" else "Check"} • ${saved.radios.size} radios • ${if (saved.complete) "Finished" else "Incomplete"}") } }
            Text("Last 10 checks per room. Historical checks are compared with the current baseline.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { history = false }) { Text("Close") } })
    edit?.let { radio ->
        var label by remember(radio.key) { mutableStateOf(radio.label) }
        var note by remember(radio.key) { mutableStateOf(radio.note) }
        var role by remember(radio.key) { mutableStateOf(radio.role) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(radio.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${radio.kind} • ${radio.mac}\n${radio.vendor.ifBlank { "Vendor unknown" }}\nLast observed ${time(radio.lastSeen)}", style = MaterialTheme.typography.bodySmall)
                if (radio.randomized) Text("Private / randomized address: this label may not survive an address change.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = label, onValueChange = { label = it.take(80) }, label = { Text("Your device label") }, placeholder = { Text("Meeting room TV") }, singleLine = true)
                RoomRole.entries.forEach { value -> Row(Modifier.fillMaxWidth().clickable { role = value }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = role == value, onClick = { role = value })
                    Text(roleLabel(value), modifier = Modifier.padding(top = 12.dp))
                } }
                OutlinedTextField(value = note, onValueChange = { note = it.take(1000) }, label = { Text("Notes") }, minLines = 2)
                OutlinedButton(onClick = { edit = null; onFind(radio) }, modifier = Modifier.fillMaxWidth()) { Text("Find nearby signal") }
                if (room?.baseline?.any { it.key == radio.key } == true) TextButton(onClick = { vm.forget(radio.key); edit = null }) { Text("Forget this baseline identifier") }
                Text("Saving adds this identifier to the room baseline. Only mark it known after you recognize the equipment.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { vm.label(radio, label, note, role); edit = null }) { Text("Save label & classification") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Close") } })
    }
}

private fun roleLabel(role: RoomRole): String = when (role) { RoomRole.KNOWN -> "Known room device"; RoomRole.BACKGROUND -> "Nearby background"; RoomRole.UNIDENTIFIED -> "Unidentified" }

@Composable
private fun SectionTitle(title: String, count: Int, description: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text("$title ($count)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoomRadioCard(entry: RoomEntry, enabled: Boolean, onClick: () -> Unit) {
    val radio = entry.radio
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(radio.title, fontWeight = FontWeight.SemiBold)
            Text("${roleLabel(radio.role)} • ${radio.kind} • ${radio.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(entry.reason, style = MaterialTheme.typography.bodySmall)
            Text("${radio.mac} • ${radio.samples} readings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (radio.note.isNotBlank()) Text(radio.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
