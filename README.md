# TUR-BROWSER

A lightweight Android browser with a polished, animated interface.

## Version 1.5.0
- Two-column tab cards with animated entry
- Persistent tab groups: create groups, filter the grid, and assign tabs to groups
- Redesigned three-row T menu with animated cells and a direct ad-blocking toggle
- Clearer Settings naming (the T menu no longer says "Tools" for Settings)
- Quick-access editor supports rename, own photo from the device gallery, restore original site icon, move up/down, and remove
- Cleaner animated settings, tab cards and download rows
- Better favicon source order (SVG, Apple Touch icon, Android icon, favicon and fallback services)

VPN is intentionally excluded. Ad/tracker blocking remains a basic hostname list, not a network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
