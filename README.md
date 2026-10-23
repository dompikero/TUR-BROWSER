# TUR-BROWSER

A lightweight Android browser with the turquoise/blue T logo, a modern tool sheet, an animated tab switcher, and clear private-mode styling.

## Features
- WebView browsing, address/search bar, back/forward/reload and tabs
- Card-based animated tab switcher with close controls and new-tab action
- Search engine selection: Google, Bing, DuckDuckGo and Startpage
- Bookmarks, browsing history, downloads and page sharing
- Tool panel inspired by the supplied browser UI with History, Bookmarks, Downloads, Incognito, Share, Add Bookmark, Refresh and Tools
- Private mode with a darker top area and a persistent PRIVATE MODE label
- Basic ad/tracker host blocking, Do Not Track / Global Privacy Control signals
- Text-size control and Turkish/English setting
- Android Safe Browsing where supported

VPN is intentionally excluded. Ad/tracker blocking uses a small hostname list and is not a full network-wide blocker. Do Not Track is a preference signal; websites can ignore it.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Find it in the workflow artifact named `TUR-BROWSER-APK`.
