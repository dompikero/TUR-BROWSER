# TUR-BROWSER

A lightweight Android browser focused on a polished, animated mobile UI.

## Version 1.4.1
- Uses the supplied original T mark as a crisp transparent WebP for the app icon, header, tabs and centre action button
- Download page redesigned with a blue transfer card, green progress indicators, coloured file-type icons, file sizes, completion dates and file actions
- Android DownloadManager IDs are stored to show actual progress and completion state
- Filled-roof home navigation icon and a stacked-tabs icon with live tab count
- Quick access favicons try original site Apple Touch icons and favicons before high-resolution fallback providers
- Animated home screen, editable quick access, and live news feed retained

VPN is intentionally excluded. Ad/tracker blocking uses a small hostname list and is not a full network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
