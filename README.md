# TUR-BROWSER

A lightweight Android browser with a turquoise/blue T logo and a home screen inspired by the supplied references.

## Features
- WebView browsing, address/search bar, back/forward/reload and tabs
- Search engine selection: Google, Bing, DuckDuckGo and Startpage
- Bookmarks, browsing history, downloads and page sharing
- Private mode with history saving disabled, cache disabled and cookies cleared on entry
- Basic ad/tracker host blocking
- Best-effort Do Not Track / Global Privacy Control signals
- Text-size control and Turkish/English home-screen setting
- Android Safe Browsing where supported

VPN is intentionally excluded. Ad/tracker blocking uses a small hostname list, not a full network-wide blocker. Do Not Track is a preference signal; sites can ignore it.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Find it in the workflow artifact named `TUR-BROWSER-APK`.
