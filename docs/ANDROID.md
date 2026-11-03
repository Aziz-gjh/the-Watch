# The Watch for Android — Room Change Watch

Independent Fieldwatch-based Android app, Android 10 or later, requiring Wi-Fi and Bluetooth LE hardware. Install separately from Fieldwatch; their data does not automatically transfer.

## Install

Download [The-Watch.apk](https://github.com/Aziz-gjh/the-Watch/raw/refs/heads/main/dist/The-Watch.apk) on your phone. Open the downloaded APK and allow installation from your browser or Files app if Android asks. This is a sideloaded app, not a Play Store installation. It does not update itself. Future APK updates need the same signing key and a higher version code.

The repository is private. Sign in to an authorized GitHub account first. A 404 while signed out does not mean the APK is missing. Alternatively open the APK's GitHub file page and tap **Download raw file**.

The repository includes `dist/SHA256SUMS.txt` and signing-certificate details. Preserve the private signing key outside the repository for future updates; the public APK is signed with a dedicated The Watch key, not Fieldwatch's certificate.

## First room

1. Read the inherited first-launch disclaimer and grant radio/location permissions. Enable system Location, Wi-Fi and Bluetooth. Android needs these permissions for radio scanning even though this fork defaults GPS tagging off.
2. Open Rooms, create a room, and stand at a repeatable position.
3. Choose **Learn this room**. Default duration: 90 seconds. Keep normal room equipment on. Backgrounding the app or stopping early makes the check incomplete.
4. Review the observations. **Save these as baseline** explicitly saves repeatable observations passing the signal filter. This does not verify the devices are physically in that room.
5. Tap entries to add labels and notes and select **Known room device**, **Nearby background**, or **Unidentified**. Only label equipment you actually recognize. Private/randomized addresses may change.
6. On a later visit, select **Check room** from the same position. Changes are separated from baseline matches, missing observations, and uncertain/background signals.

Relearning preserves the old baseline until you explicitly save the completed replacement. Matching identifiers keep their labels. Settings offers a −100 to −40 dBm signal filter and 60/90/180-second checks. The default −75 dBm threshold reduces clutter rather than defining a wall or radius.

## Review changes and find a signal

Tap a new observation, then **Find nearby signal**. This screen waits for observations received after opening it. It shows averaged signal strength, a trend graph, increasing/decreasing/steady wording, and optional sound/vibration. Feedback stops when readings are stale. BLE freshness is 5 seconds; Wi-Fi is 45 seconds because Android scans update more slowly. Beeps indicate received strength, not measured distance. Audio follows system volume settings.

There is no compass bearing, device location pin, or guaranteed room boundary. A radio may be on another floor or outside. A Wi-Fi hotspot or BLE advertisement does not establish spying, a hidden camera, or malicious intent. Devices that are asleep, silent, out of range, unsupported, or using new addresses may not appear. Avoid treating “not observed” as “removed” or “no changes” as “safe.”

## Storage and scope

Up to 30 rooms, 800 observed identifiers per scan, and the last 10 checks per room. Each check is explicitly started; old cached observations do not count as new evidence. Overflows and interrupted checks are incomplete. History compares a selected report with the room's **current** baseline. Room labels are separate from the advanced scanner's global named radios. Room notes, labels, baselines and reports are private to this installation and are not uploaded. They are not encrypted by an additional app-specific encryption layer. Deleting a room removes its room records, not the inherited scanner's separate rotating logs. Uninstalling removes local app data.

Advanced Live, Filters, Signatures, Reports and Settings remain available. The inherited scanner can continue collecting through its visible foreground notification; stop it in that notification or close the app as described by the scanner. Room checks themselves stop if the app goes to the background. New installations default to no GPS tagging, no online map/place lookup and no watchlist sounds. Turning on inherited online/TAK features enables those features' network behavior.

## Build

JDK 17 is recommended. Android SDK platform 35 is required. The Gradle wrapper downloads Gradle 8.11.1 and pinned dependencies.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows uses `gradlew.bat`. `local.properties` can declare `sdk.dir`; keep it untracked. Development builds use an `.debug` application ID suffix and are not the distributed release APK.

For a signed release, set these environment variables before `:app:assembleRelease`:

- `THEWATCH_STORE_FILE`: absolute path to your private keystore
- `THEWATCH_STORE_PASSWORD`
- `THEWATCH_KEY_ALIAS`
- `THEWATCH_KEY_PASSWORD`

Never commit the keystore or its passwords. An unsigned release output is not the downloadable release and must not be distributed as installable. GitHub Actions builds/tests development APKs; optional signing secrets enable a release artifact. The direct download in `dist/` is updated deliberately after verification.

For the initial build in this workspace, the private keystore and its local signing settings are in the ignored `.tools/signing/` directory. Back up that entire directory securely before moving or deleting the workspace. Losing that key prevents future APKs from updating this installed application under the same signing identity. Signing secrets have not been uploaded to GitHub.

The first release should be tested on a physical Android phone in the actual room: multiple Wi-Fi scan batches, BLE advertiser detection, rotation/background behavior, persistence after force-stop, permission denial, and Bluetooth/Wi-Fi toggles. Compilation and unit tests cannot establish RF accuracy.
