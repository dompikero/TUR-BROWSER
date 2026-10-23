# TUR-BROWSER

A lightweight Android browser focused on a polished, animated mobile UI.

## Version 1.4.0
- Download page redesigned with a blue transfer card, green progress indicators, colored file-type icons, file sizes, completion dates, and file actions
- DownloadManager IDs are saved so progress and completion reflect Android download status
- New filled-roof home icon and a stacked-tabs icon with a live count
- Larger central T brand mark
- Quick-access and news favicons now try original site Apple Touch icons and favicons before higher-resolution fallback providers
- Animated UI, editable quick access, and news feed retained

VPN is intentionally excluded. Ad/tracker blocking uses a small hostname list and is not a full network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
