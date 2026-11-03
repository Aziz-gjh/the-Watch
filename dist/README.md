# The Watch Android downloads

[**Download The-Watch.apk**](https://github.com/Aziz-gjh/the-Watch/raw/refs/heads/main/dist/The-Watch.apk)

Version **1.0.0**, application ID `io.github.azizgjh.thewatch`, Android 10+ (API 29), approximately 12 MB. Independently signed release; installs separately from Fieldwatch. Download on your phone and open the APK from Downloads/Files. Android may require permission to install from that source.

This repository is private. Sign in to GitHub before downloading; signed-out requests return 404. You can also open [the APK file page](https://github.com/Aziz-gjh/the-Watch/blob/main/dist/The-Watch.apk) and choose **Download raw file**.

Checksum: [SHA256SUMS.txt](SHA256SUMS.txt).

Release signing certificate SHA-256:

```text
9596f559418e8f9c57b966e8c6eb7e03acb48bbd8e81131ce7201d3956f84b49
```

This is an independent Fieldwatch-based build. It is not reviewed or signed by Off Grid Pete LLC. Attribution and third-party notices are in [`android/`](../android/UPSTREAM.md).

Verification: all 897 Android JVM unit tests passed; release compilation and Android release lint checks passed; APK signature verified; signed APK installed and launched on an Android 15 emulator. Emulator checks covered room creation, saved settings, early/background interruption, persistence, classification and Find nearby's no-fresh-signal state. Classification tests used explicitly synthetic observations. Physical Wi-Fi/Bluetooth performance, range, battery use and OEM behavior still require testing on a real phone.

Use [the Android guide](../docs/ANDROID.md) for setup and limitations. Radio signals pass through walls, private identifiers change, and signal strength cannot pinpoint a device or establish a threat.
