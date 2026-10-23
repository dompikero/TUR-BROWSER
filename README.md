# TUR-BROWSER

A lightweight Android browser with a polished, animated interface.

## Version 1.7.0
- Redesigned download centre with stronger visual hierarchy, download totals, status chips, smoother cards, and custom crisp vector-style file icons for documents, images, audio, video and archives
- More polished empty downloads state and an optional clear-list action that does not delete device files
- Redesigned T menu header, richer icon tiles, tighter spacing, refined opening/closing animations and staggered cell entry
- Quick-access drag preview now follows the finger in a floating ghost card while a placeholder shows the target position; neighbouring tiles animate out of the way
- Drop animates the card into its final slot and saves order; edit controls remain compact within tiles

VPN is intentionally excluded. Ad/tracker blocking remains a basic hostname list, not a network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
