package app.fieldwatch.room

import kotlinx.serialization.Serializable

@Serializable
enum class RoomRole { KNOWN, BACKGROUND, UNIDENTIFIED }

@Serializable
data class RoomRadio(
    val key: String,
    val kind: String,
    val mac: String,
    val name: String,
    val vendor: String = "",
    val label: String = "",
    val note: String = "",
    val role: RoomRole = RoomRole.UNIDENTIFIED,
    val randomized: Boolean = false,
    val rssi: Int = -127,
    val security: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val samples: Int = 0,
) {
    val title: String get() = label.ifBlank { name.ifBlank { "Unnamed ${if (kind == "BLE") "Bluetooth" else "Wi-Fi"} radio" } }
}

@Serializable
data class RoomSettings(val minRssi: Int = -75, val durationSeconds: Int = 90, val minBleSamples: Int = 3, val minWifiSamples: Int = 2)

@Serializable
data class RoomReport(val startedAt: Long, val endedAt: Long, val complete: Boolean, val learning: Boolean, val radios: List<RoomRadio>, val note: String = "")

@Serializable
data class RoomProfile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val baselineAt: Long = 0,
    val settings: RoomSettings = RoomSettings(),
    val baseline: List<RoomRadio> = emptyList(),
    val reports: List<RoomReport> = emptyList(),
)

@Serializable
data class RoomArchive(val version: Int = 1, val rooms: List<RoomProfile> = emptyList(), val selectedId: String? = null)

data class RoomEntry(val radio: RoomRadio, val reason: String)
data class RoomComparison(val changes: List<RoomEntry>, val known: List<RoomEntry>, val missing: List<RoomEntry>, val nearby: List<RoomEntry>)

/** A bounded session accepts distinct actual reception timestamps, never UI refreshes. */
class RoomSession(val startedAt: Long, val learning: Boolean, val settings: RoomSettings) {
    private val accumulated = LinkedHashMap<String, RoomRadio>()
    private val sampleTimes = HashMap<String, MutableSet<Long>>()
    val endsAt: Long get() = startedAt + settings.durationSeconds * 1000L
    var overflow: Boolean = false
        private set

    fun ingest(radio: RoomRadio, now: Long) {
        if (radio.lastSeen <= startedAt || radio.lastSeen > minOf(now, endsAt) || radio.rssi !in -126..-1) return
        if (!accumulated.containsKey(radio.key) && accumulated.size >= MAX_RADIOS) { overflow = true; return }
        val seen = sampleTimes.getOrPut(radio.key) { HashSet() }
        if (!seen.add(radio.lastSeen)) return
        val previous = accumulated[radio.key]
        // Cap retained timestamps; scanning duration and BLE callback rate can be high.
        if (seen.size > 1500) seen.remove(seen.minOrNull())
        val first = minOf(previous?.firstSeen ?: radio.lastSeen, radio.lastSeen)
        val latest = if (previous == null || radio.lastSeen >= previous.lastSeen) radio else previous
        accumulated[radio.key] = latest.copy(firstSeen = first, samples = (previous?.samples ?: 0) + 1)
    }

    fun radios(): List<RoomRadio> = accumulated.values.sortedByDescending { it.rssi }
    fun finish(now: Long, complete: Boolean, note: String = ""): RoomReport = RoomReport(startedAt, minOf(now, endsAt), complete, learning, radios(), note)

    companion object { const val MAX_RADIOS = 800 }
}

object RoomEngine {
    fun stable(radio: RoomRadio, settings: RoomSettings): Boolean =
        radio.rssi >= settings.minRssi && radio.samples >= if (radio.kind == "WIFI") settings.minWifiSamples else settings.minBleSamples

    fun comparison(room: RoomProfile, report: RoomReport): RoomComparison {
        val baseline = room.baseline.associateBy { it.key }
        val observed = report.radios.associateBy { it.key }
        val changes = ArrayList<RoomEntry>()
        val known = ArrayList<RoomEntry>()
        val nearby = ArrayList<RoomEntry>()
        report.radios.forEach { heard ->
            val old = baseline[heard.key]
            val current = if (old != null) heard.copy(label = old.label, note = old.note, role = old.role) else heard
            val enough = stable(heard, room.settings)
            when {
                old?.role == RoomRole.BACKGROUND -> nearby += RoomEntry(current, "Marked as nearby background")
                old != null && enough && securityChanged(old, heard) -> changes += RoomEntry(current, "Wi-Fi security changed: ${old.security} → ${heard.security}")
                old != null -> known += RoomEntry(current, when {
                    !enough -> "Baseline identifier observed; weak or limited readings"
                    old.role == RoomRole.KNOWN -> "Reviewed room device; identifier matches baseline"
                    else -> "Baseline identifier; still unidentified"
                })
                !enough -> nearby += RoomEntry(current, if (heard.rssi < room.settings.minRssi) "Below the signal filter" else "Too few independent observations")
                heard.randomized -> nearby += RoomEntry(current, "Private / randomized address; may be a familiar device")
                else -> changes += RoomEntry(current, "New identifier with repeated observations; location unconfirmed")
            }
        }
        val missing = room.baseline.filter { it.role != RoomRole.BACKGROUND && !observed.containsKey(it.key) }.map {
            RoomEntry(it, when {
                !report.complete || report.radios.isEmpty() -> "Check incomplete or no readings; presence unknown"
                report.radios.none { heard -> heard.kind == it.kind } -> "No fresh ${it.kind} readings; presence unknown"
                else -> "Not observed during this check; may be asleep or using another address"
            })
        }
        return RoomComparison(changes, known, missing, nearby)
    }

    fun securityChanged(old: RoomRadio, current: RoomRadio): Boolean = old.kind == "WIFI" && old.security.isNotBlank() && current.security.isNotBlank() && old.security != current.security

    fun baselineCandidates(report: RoomReport, settings: RoomSettings): List<RoomRadio> =
        if (!report.complete || !report.learning) emptyList() else report.radios.filter { stable(it, settings) }

    fun signalTrend(samples: List<Pair<Long, Int>>, now: Long, kind: String): String {
        val freshness = if (kind == "BLE") 5000L else 45000L
        val fresh = samples.distinctBy { it.first }.filter { it.first <= now && now - it.first <= freshness && it.second in -126..-1 }.sortedBy { it.first }.takeLast(12)
        if (fresh.isEmpty()) return "No recent signal"
        if (fresh.size < 4) return "Collecting signal readings"
        val half = fresh.size / 2
        val before = fresh.take(half).map { it.second }.average()
        val after = fresh.takeLast(half).map { it.second }.average()
        return when { after - before >= 3 -> "Signal increasing"; before - after >= 3 -> "Signal decreasing"; else -> "Signal steady" }
    }
}
