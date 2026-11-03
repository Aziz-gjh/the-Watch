package app.fieldwatch.room

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fieldwatch.domain.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class RoomUi(
    val archive: RoomArchive = RoomArchive(), val ready: Boolean = false,
    val session: RoomSession? = null, val report: RoomReport? = null,
    val now: Long = System.currentTimeMillis(), val message: String? = null,
    val findTarget: RoomRadio? = null,
) { val room: RoomProfile? get() = archive.rooms.firstOrNull { it.id == archive.selectedId } }

class RoomViewModel(application: Application) : AndroidViewModel(application) {
    private val file = AtomicFile(File(application.filesDir, "watch-rooms-v1.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val mutable = MutableStateFlow(RoomUi())
    val ui = mutable.asStateFlow()
    private var failedLoad = false
    private var deviceCache: List<Sighting> = emptyList()

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) json.decodeFromString<RoomArchive>(file.readFully().toString(Charsets.UTF_8)) else RoomArchive()
            } }
            val archive = result.getOrNull()
            failedLoad = archive == null || archive.version != 1
            mutable.value = RoomUi(archive = archive?.takeIf { !failedLoad } ?: RoomArchive(), ready = true,
                message = if (failedLoad) "Room storage could not be read. Existing file was preserved; room editing is disabled." else null)
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                val session = mutable.value.session
                if (session != null) {
                    ingest(deviceCache)
                    if (now >= session.endsAt) finish(true)
                }
                mutable.value = mutable.value.copy(now = now)
            }
        }
    }

    fun observe(devices: List<Sighting>) { deviceCache = devices; if (mutable.value.session != null) ingest(devices) }
    private fun ingest(devices: List<Sighting>) {
        val session = mutable.value.session ?: return
        val now = System.currentTimeMillis()
        devices.forEach { device ->
            device.rssiHistory.forEach { sample ->
                session.ingest(radio(device).copy(rssi = sample.rssi, lastSeen = sample.at), now)
            }
        }
    }

    fun createRoom(name: String) {
        if (!canEdit() || name.trim().isEmpty() || mutable.value.archive.rooms.size >= 30) return
        val room = RoomProfile(UUID.randomUUID().toString(), name.trim().take(80), System.currentTimeMillis())
        updateArchive(mutable.value.archive.copy(rooms = mutable.value.archive.rooms + room, selectedId = room.id))
        mutable.value = mutable.value.copy(report = null)
    }
    fun selectRoom(id: String) {
        if (mutable.value.session != null) return
        updateArchive(mutable.value.archive.copy(selectedId = id)); mutable.value = mutable.value.copy(report = null)
    }
    fun renameRoom(name: String) { if (name.trim().isNotEmpty()) editRoom { it.copy(name = name.trim().take(80)) } }
    fun deleteRoom() {
        val selected = mutable.value.room ?: return
        val rooms = mutable.value.archive.rooms.filter { it.id != selected.id }
        updateArchive(mutable.value.archive.copy(rooms = rooms, selectedId = rooms.firstOrNull()?.id))
        mutable.value = mutable.value.copy(report = null)
    }
    fun settings(settings: RoomSettings) { editRoom { it.copy(settings = settings.copy(minRssi = settings.minRssi.coerceIn(-100, -40), durationSeconds = settings.durationSeconds.coerceIn(30, 180))) } }
    fun start(learning: Boolean) {
        if (!canEdit() || mutable.value.session != null) return
        val room = mutable.value.room ?: return
        mutable.value = mutable.value.copy(session = RoomSession(System.currentTimeMillis(), learning, room.settings), report = null, message = null)
    }
    fun finish(complete: Boolean, note: String = "") {
        val current = mutable.value
        val session = current.session ?: return
        val report = session.finish(System.currentTimeMillis(), complete,
            if (session.overflow) "Observation limit reached; this check is incomplete." else note)
            .let { if (session.overflow) it.copy(complete = false) else it }
        mutable.value = current.copy(session = null, report = report)
        editRoom { it.copy(reports = (listOf(report) + it.reports).take(10)) }
    }
    fun interrupt() { if (mutable.value.session != null) finish(false, "Check interrupted. Run a complete check before reviewing absence.") }
    fun saveBaseline() {
        val room = mutable.value.room ?: return
        val report = mutable.value.report ?: return
        val candidates = RoomEngine.baselineCandidates(report, room.settings)
        if (candidates.isEmpty()) { notice("No repeatable signals passed the filter. Baseline was not changed."); return }
        val old = room.baseline.associateBy { it.key }
        val baseline = candidates.map { heard -> old[heard.key]?.let { heard.copy(label = it.label, note = it.note, role = it.role) } ?: heard }
        editRoom { it.copy(baselineAt = report.endedAt, baseline = baseline) }
        notice("Baseline saved. Review the identifiers and label devices you recognize.")
    }
    fun label(radio: RoomRadio, label: String, note: String, role: RoomRole) {
        val updated = radio.copy(label = label.trim().take(80), note = note.trim().take(1000), role = role)
        editRoom { it.copy(baseline = it.baseline.filter { item -> item.key != radio.key } + updated, baselineAt = it.baselineAt.takeIf { at -> at > 0 } ?: System.currentTimeMillis()) }
        notice("Room label saved. This assigns an observed identifier, not a confirmed physical location.")
    }
    fun forget(key: String) { editRoom { it.copy(baseline = it.baseline.filter { radio -> radio.key != key }) } }
    fun showReport(report: RoomReport) { if (mutable.value.session == null) mutable.value = mutable.value.copy(report = report) }
    fun find(radio: RoomRadio) { mutable.value = mutable.value.copy(findTarget = radio) }
    fun notice(message: String?) { mutable.value = mutable.value.copy(message = message) }
    private fun canEdit(): Boolean = mutable.value.ready && !failedLoad
    private fun editRoom(transform: (RoomProfile) -> RoomProfile) {
        if (!canEdit()) return
        val id = mutable.value.archive.selectedId
        updateArchive(mutable.value.archive.copy(rooms = mutable.value.archive.rooms.map { if (it.id == id) transform(it) else it }))
    }
    private fun updateArchive(archive: RoomArchive) {
        if (!canEdit()) return
        // Atomic, small writes on the owner thread preserve ordering and survive interrupted writes.
        val encoded = json.encodeToString(archive).toByteArray(Charsets.UTF_8)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite(); stream.write(encoded); file.finishWrite(stream)
            mutable.value = mutable.value.copy(archive = archive)
        } catch (_: Exception) { if (stream != null) file.failWrite(stream); notice("Could not save room data. The last saved version is preserved.") }
    }
    companion object {
        fun radio(device: Sighting) = RoomRadio(device.key, device.kind.name, device.mac, device.name, device.vendor.orEmpty(),
            randomized = device.randomized, rssi = device.rssi, security = device.facts.capabilities.orEmpty(), firstSeen = device.firstSeen, lastSeen = device.lastSeen)
    }
}
