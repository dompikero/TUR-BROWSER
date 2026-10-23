# TUR-BROWSER

A lightweight Android browser focused on a modern, animated UI.

## Version 1.3.0
- Refined animated home screen
- Editable quick-access links (add/remove)
- Site favicons loaded from each site's own favicon URL, with a fallback icon service
- Current headlines from Google News RSS, localized for Turkish or English
- News refresh control and animated loading cards
- Card-based settings, tab, bookmark and download screens
- Private mode with a darker top bar and persistent label
- Search engine selection, text sizing, basic privacy controls

VPN is intentionally excluded. Ad/tracker blocking uses a small hostname list and is not a full network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
