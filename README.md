# The Watch

A private, browser-local workspace for reviewing Wi-Fi and Bluetooth LE observations. Inspired by [Fieldwatch](https://github.com/OffGridPete/Fieldwatch), independently implemented as a website.

**This is a working web preview, not a live radio scanner.** Websites cannot replace Android's Wi-Fi scanning and broader radio access. Import a Fieldwatch log to review real observations, or explore the clearly labeled fictional sample session.

## Run locally

Install Node.js 22 or newer, then:

```sh
npm start
```

Open **http://127.0.0.1:4173**. There are no packages to install or build steps. Serve over HTTP; opening `index.html` directly as a file does not reliably support JavaScript modules.

## Use it

1. Explore the sample or choose **Import observations** and select a Fieldwatch **Share log** CSV/JSONL file.
2. Filter Wi-Fi/BLE, search names, vendors, identifiers, or notes, and sort by signal or observation time.
3. Select a radio name for details, add notes, and star it to keep it on your watchlist.
4. **Export view** downloads the currently filtered radios as JSON, including identifiers, notes, and watchlist state. That file can be imported again.

Import replaces the current session after validation. The complete file must be valid; unsupported types and malformed rows are reported without replacing existing observations. Maximum 10 MB and 50,000 observations. Latest timestamp wins when identifiers repeat within the same radio type. There is no automatic identity inference across randomized addresses.

The app saves the current session in this browser's local storage when available. It makes no network calls for observations, fonts, analytics, or maps. **Clear local data** removes this app's stored session, notes, and watchlist; it does not delete downloaded exports. Browser storage is not encrypted by the app.

## Accepted JSON example

```json
[
  {
    "ts": 1793687400000,
    "kind": "WIFI",
    "mac": "02:00:00:00:00:01",
    "name": "Example access point",
    "rssi": -55,
    "channel": 6,
    "freq": 2437,
    "vendor": "Example"
  }
]
```

`kind` and `mac` are required. `kind` is `WIFI` or `BLE`. Time fields are epoch milliseconds or ISO dates; missing times and RSSI appear as unknown. JSONL uses one JSON object per line. Fieldwatch's current CSV/JSONL format is supported; PDF reports, signature settings, and AI text exports are not.

## Checks

```sh
npm run check
```

Checks cover JavaScript syntax and synthetic Fieldwatch CSV/JSONL imports, quoted CSV fields, grouping by radio type, validation, filters, and JSON round trips. No dependencies are required.

An optional interaction check uses an existing Chrome installation:

```sh
node scripts/browser-check.mjs
```

Set `CHROME_PATH` if Chrome is outside the default Windows installation path. This starts a temporary localhost server and headless browser, checks desktop/mobile interactions and persistence, saves screenshots in ignored `test-results/`, and stops both processes.

## Hosting

This is a static website. Publish `index.html`, `app.js`, `data.js`, `styles.css`, `icon.svg`, and `docs/` on a static host. For GitHub Pages, select **Settings → Pages → Deploy from a branch → main / (root)**. Pages setup is not automatically enabled by committing this repository. Do not place private logs in the repository or public hosting directory.

## Review and next steps

See [the Fieldwatch review and web roadmap](docs/PROJECT-REVIEW.md) for browser constraints, improvement ideas, implementation scope, upstream references, and a future collector architecture.

The initial web version adds desktop review, notes, and watchlist functionality. Good next steps are interactive session comparison, evidence-based explanations for signature matches, export redaction controls, offline installation, and an optional live Android/hardware collector.

Independent project; not an official Fieldwatch release. No upstream code or radio databases are bundled. See [LICENSE](LICENSE).
