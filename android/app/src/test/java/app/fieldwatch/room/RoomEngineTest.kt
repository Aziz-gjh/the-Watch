package app.fieldwatch.room

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RoomEngineTest {
    private val settings = RoomSettings(durationSeconds = 90)
    private fun radio(key: String = "BLE:AA", kind: String = "BLE", at: Long = 1200, rssi: Int = -55, samples: Int = 3, randomized: Boolean = false) =
        RoomRadio(key, kind, "AA", "Example", rssi = rssi, firstSeen = at, lastSeen = at, samples = samples, randomized = randomized)
    private fun room(baseline: List<RoomRadio> = emptyList()) = RoomProfile("room", "Meeting room", 100, baselineAt = 500, baseline = baseline)
    private fun report(vararg radios: RoomRadio, complete: Boolean = true) = RoomReport(1000, 91000, complete, false, radios.toList())

    @Test fun cachedAndFutureReadingsAreRejected() {
        val session = RoomSession(1000, false, settings)
        listOf(900L, 1000L, 2100L).forEach { session.ingest(radio(at = it), 2000) }
        assertTrue(session.radios().isEmpty())
    }
    @Test fun repeatedUiSnapshotsDoNotManufactureEvidence() {
        val session = RoomSession(1000, false, settings)
        repeat(100) { session.ingest(radio(at = 1200), 2000) }
        assertEquals(1, session.radios().single().samples)
        assertFalse(RoomEngine.stable(session.radios().single(), settings))
    }
    @Test fun distinctBleReadingsPromoteANewIdentifier() {
        val session = RoomSession(1000, false, settings)
        listOf(1100L, 1200L, 1300L).forEach { session.ingest(radio(at = it), 2000) }
        val result = RoomEngine.comparison(room(), session.finish(91000, true))
        assertEquals(1, result.changes.size)
        assertTrue(result.nearby.isEmpty())
    }
    @Test fun weakAndPrivateAddressesRemainUncertain() {
        val result = RoomEngine.comparison(room(), report(radio("weak", rssi = -90), radio("private", randomized = true)))
        assertTrue(result.changes.isEmpty())
        assertEquals(2, result.nearby.size)
    }
    @Test fun wifiNeedsIndependentScanTimestamps() {
        val session = RoomSession(1000, false, settings)
        session.ingest(radio("WIFI:AA", "WIFI", 1100), 40000)
        session.ingest(radio("WIFI:AA", "WIFI", 1100), 40000)
        assertFalse(RoomEngine.stable(session.radios().single(), settings))
        session.ingest(radio("WIFI:AA", "WIFI", 31000), 40000)
        assertTrue(RoomEngine.stable(session.radios().single(), settings))
    }
    @Test fun backgroundIsSeparatedAndLabelsArePreserved() {
        val saved = radio().copy(role = RoomRole.BACKGROUND, label = "Neighbor router", note = "Not our equipment")
        val result = RoomEngine.comparison(room(listOf(saved)), report(radio()))
        assertEquals("Neighbor router", result.nearby.single().radio.title)
        assertEquals("Not our equipment", result.nearby.single().radio.note)
        assertTrue(result.changes.isEmpty())
    }
    @Test fun baselineKnownAndMissingAreClearlySeparated() {
        val saved = radio().copy(role = RoomRole.KNOWN, label = "TV")
        val missing = radio("BLE:BB")
        val result = RoomEngine.comparison(room(listOf(saved, missing)), report(radio()))
        assertEquals("TV", result.known.single().radio.title)
        assertEquals("BLE:BB", result.missing.single().radio.key)
        assertTrue(result.changes.isEmpty())
    }
    @Test fun noReadingsNeverMeansRoomIsClear() {
        val result = RoomEngine.comparison(room(listOf(radio())), report())
        assertTrue(result.missing.single().reason.contains("unknown"))
        assertTrue(RoomEngine.comparison(room(listOf(radio())), report(radio("other"), complete = false)).missing.single().reason.contains("unknown"))
    }
    @Test fun securityChangesRequireObservedFactsOnBothSides() {
        val saved = radio("WIFI:AA", "WIFI").copy(security = "[WPA2-PSK-CCMP][ESS]")
        val changed = saved.copy(security = "[ESS]")
        assertEquals(1, RoomEngine.comparison(room(listOf(saved)), report(changed)).changes.size)
        assertFalse(RoomEngine.securityChanged(saved, saved.copy(security = "")))
    }
    @Test fun baselineSavingRequiresCompletedLearningAndStableSignals() {
        val session = RoomSession(1000, true, settings)
        listOf(1100L, 1200L, 1300L).forEach { session.ingest(radio(at = it), 2000) }
        assertTrue(RoomEngine.baselineCandidates(session.finish(2000, false), settings).isEmpty())
        assertEquals(1, RoomEngine.baselineCandidates(session.finish(91000, true), settings).size)
        assertTrue(RoomEngine.baselineCandidates(report(radio()), settings).isEmpty())
    }
    @Test fun sessionStopsAtItsDeadlineAndCapsIdentifiers() {
        val session = RoomSession(1000, false, settings)
        session.ingest(radio(at = 92000), 100000)
        assertTrue(session.radios().isEmpty())
        repeat(805) { session.ingest(radio("BLE:$it"), 2000) }
        assertEquals(800, session.radios().size)
        assertTrue(session.overflow)
    }
    @Test fun roomArchiveRoundTripsLabelsSettingsAndHistory() {
        val saved = RoomArchive(rooms = listOf(room(listOf(radio().copy(label = "TV", note = "Corner", role = RoomRole.KNOWN))).copy(reports = listOf(report(radio())))), selectedId = "room")
        assertEquals(saved, Json.decodeFromString<RoomArchive>(Json.encodeToString(saved)))
    }
    @Test fun findFeedbackRejectsStaleReadingsAndSmoothsTrend() {
        assertEquals("No recent signal", RoomEngine.signalTrend(listOf(1000L to -45), 10000, "BLE"))
        assertEquals("Signal increasing", RoomEngine.signalTrend(listOf(8000L to -80, 8100L to -78, 8200L to -60, 8300L to -58), 9000, "BLE"))
        assertEquals("Collecting signal readings", RoomEngine.signalTrend(List(10) { 8000L to -50 }, 9000, "BLE"))
        assertEquals("No recent signal", RoomEngine.signalTrend(listOf(10000L to -50), 9000, "BLE"))
    }
}
