# TUR-BROWSER

A lightweight Android browser with a polished, animated interface.

## Version 1.5.1
- Two-column tab cards with animated entry
- Persistent tab groups: create groups, filter the grid, and assign tabs to groups
- New tabs clear the current group filter so the new tab is immediately visible
- Tab counter stays in sync when tabs open, close, or private mode is toggled
- Redesigned three-row T menu with animated cells, Settings naming and an ad-blocking toggle
- Quick-access editor supports rename, gallery photo, original icon reset, move up/down, and remove
- Better favicon source order (SVG, Apple Touch icon, Android icon, favicon and fallback services)
- Polished animated settings, tab cards and download rows

VPN is intentionally excluded. Ad/tracker blocking remains a basic hostname list, not a network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
