# Upstream source and independent build

The Watch Android starts from [OffGridPete/Fieldwatch](https://github.com/OffGridPete/Fieldwatch), pinned to commit `8c900455419ba67287d6be71e57a0739f4aed173` (downloaded 3 November 2026). The original Kotlin/Compose application, radio collectors, settings, reports, signatures, and Hunt remain available.

This is an independent fork, not an official Fieldwatch release. Original code retains the copyright and MIT license in [LICENSE](LICENSE), with third-party datasets and libraries covered by [NOTICE](NOTICE). New room-check code and modifications are covered by the root repository's MIT license.

Changes:

- Independent application ID `io.github.azizgjh.thewatch`, name The Watch, version 1.0.0, separate release signing key.
- Rooms is the start screen. Room profiles, baseline learning, reviewed labels and background classification, bounded comparison sessions and recent check history.
- Four result groups: changes, known/baseline identifiers, not observed, nearby/uncertain. Repeated observations and adjustable RSSI threshold reduce clutter without implying a physical boundary.
- Native Find nearby screen for BLE and Wi-Fi, with fresh-reading gates, smoothed strength, trend graph, optional sound and vibration. No position/distance claim.
- Wi-Fi observation timestamps use the scan result's actual monotonic reception timestamp, preventing cached results from becoming fresh evidence.
- Room data stays in an atomic, private app file; failed reads preserve the existing file. Room checks interrupted by app backgrounding are marked incomplete.
- New installations default to GPS tagging off, online lookups/maps off and watchlist sounds off. Advanced Fieldwatch settings remain available if explicitly enabled.

The room UI is English in this first version. The inherited scanner retains its bundled translations. No APK or proprietary signing material from Fieldwatch is used.
