# Fieldwatch review and The Watch web plan

Reviewed 3 November 2026 against the public [Fieldwatch repository](https://github.com/OffGridPete/Fieldwatch). This is a focused review of its README, project structure, and observation export implementation, not a full code or security audit.

## What the original project does well

Fieldwatch is a Kotlin Android application built around local, receive-only Wi-Fi access point and Bluetooth LE observation. Its existing features already include signatures, filters, trusted networks, maps, field sessions, comparison reports, and signal-based searching. It also documents phone GNSS and cell connection monitoring, with explicit limits on what warnings mean. The README's broad “not cellular” statement should be read alongside those newer monitoring sections: this is not a general cellular scanner.

Local processing, no accounts, no backend, offline use, and a clear distinction between hypotheses and identity are valuable design choices to preserve. A web version should extend the workflow rather than advertise those existing features as new inventions.

## Can it become a website?

Yes, the review and analysis experience can. It cannot provide all the Android radio collection features as a standalone website.

| Capability | Browser-only web app | Collector plus web app |
| --- | --- | --- |
| CSV/JSONL import, search, signal summaries, notes | Yes; implemented in this version | Yes |
| Saved sessions and comparison | Possible; future work beyond one current session | Yes |
| Nearby Wi-Fi access point scanning | No standard browser API | Requires Android/native/hardware collector |
| Bluetooth LE | Limited browser API and permissions; not implemented here | Native collector retains broader access |
| Raw GNSS measurements and serving cell monitoring | Not available through ordinary browser geolocation | Requires native capabilities |
| Offline field analysis | Local processing is implemented; offline app-shell caching is future work | Yes |
| Live observations from multiple collectors | Requires a transport and backend/local bridge | Future architecture |

Browser API reference: [Web Bluetooth](https://developer.mozilla.org/en-US/docs/Web/API/Web_Bluetooth_API). It has limited browser availability, requires a secure context and permissions, and is not a drop-in passive Android scan API.

## Recommended improvements

1. **Make the desktop review workflow excellent.** Use the existing Fieldwatch exports for a responsive inventory, fast search, latest signal summaries, notes, and watchlist. This repository implements that first step.
2. **Add visual session comparison.** Fieldwatch already has comparison reports; the web opportunity is an interactive side-by-side view showing new, returning, and absent identifiers with filters and a timeline. Explain randomized addresses to avoid treating identifiers as device identity.
3. **Show why something was flagged.** Surface observed facts, signature rule, evidence, and uncertainty separately. Never convert a manufacturer or name match into a confirmed tracker or hostile device. The current web app only displays exported labels; it does not run signatures or alerts.
4. **Improve export privacy controls.** Add explicit switches for coordinates, identifiers, names, and notes, with a preview. This web version excludes coordinates and raw payloads from its model, but its exports still include identifiers and notes. Hashing alone does not guarantee anonymity.
5. **Formalize a versioned collector contract.** Keep native Android collection and expose a small, documented observation format to the website. Start with manual file transfer; add authenticated streaming only after the schema is stable.
6. **Add an installable offline web app.** Include a manifest and versioned service-worker caching, then test updates and stale-data behavior. Do not confuse local data processing with a fully offline-installable application.
7. **Support accessibility and localization.** Keep keyboard access, visible focus, meaningful empty states and textual signal readings. Arabic and RTL support would be a useful next addition once terminology is agreed.

## What this repository implements

An independent, dependency-free website inspired by Fieldwatch, rather than a fork or an automatic Kotlin conversion. No Android source, signature catalogs, vendor databases, APKs, maps, or third-party assets are copied.

- Responsive inventory, Wi-Fi/BLE views, watchlist, searchable names/vendors/identifiers/notes, signal filters, and sorting.
- Latest-per-radio RSSI histogram. This is a snapshot, not a time series or distance estimate. Outlying signals are clamped into the outer histogram bins.
- Fieldwatch Share log CSV and JSONL import, JSON array import, and The Watch JSON round trips. Duplicate observations are grouped by type and identifier; latest timestamp wins.
- Device detail dialog, observation counts, local notes, browser persistence, filtered JSON export, clear-data action, and unmistakably fictional sample observations.
- Import validation, 10 MB/50,000-row limits, safe text rendering, and no external runtime dependencies or requests.

Input compatibility is based on [`LogStore.kt`](https://github.com/OffGridPete/Fieldwatch/blob/main/app/src/main/java/app/fieldwatch/data/LogStore.kt), specifically `jsonLine`, `CSV_HEADER`, and `writeExport`. CSV fields include `timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie`. JSONL uses `ts` for epoch milliseconds. Only `WIFI` and `BLE` observations are accepted. PDF, AI narrative, signature settings, and ZIP exports are not supported. Tests use synthetic records shaped like the upstream schema; validation against real handset exports remains a follow-up.

The dashboard is a review tool, with no live radio scans, maps, signature detection, threat scores, alerts, user accounts, cloud sync, or multi-session history. Local storage depends on browser policy and capacity. Export before replacing a session. Browser data is not encrypted by this application.

## Suggested next architecture

```text
Android collector (Fieldwatch or a dedicated companion)
    → versioned observation files (current integration)
    → The Watch browser workspace

Later: collector → authenticated ingestion endpoint → per-user storage
                                                → live web workspace
```

For streaming, validate payloads server-side, authenticate collectors, isolate each user's observations, set retention/deletion rules, and bound ingestion rate and record size. Avoid a publicly exposed, unauthenticated local collector endpoint. A browser-based dashboard still needs a nearby collector; deploying a server does not give that server access to the visitor's radios.

Fieldwatch is MIT licensed, with separately governed third-party datasets. If Android source or those assets are incorporated later, preserve the upstream license and notices and review dataset terms. Link: [Fieldwatch LICENSE](https://github.com/OffGridPete/Fieldwatch/blob/main/LICENSE), [NOTICE](https://github.com/OffGridPete/Fieldwatch/blob/main/NOTICE).
