# TUR-BROWSER

A modern Android browser with a high-motion, polished interface.

## Version 1.9.0 — T brand and motion overhaul
- Restored the supplied T brand logo to the central bottom button; its open/close action keeps the T visible
- Added a pulse ring and animated T-logo spin/settle on menu interactions
- Rebuilt the T quick-actions panel with a branded blue-cyan hero, cleaner grid cells, footer status for protection and tab count, tactile press motion and cascading overshoot entrances
- Expanded motion across native settings/history/bookmarks/download screens, page transitions, address focus and bottom navigation
- Added a shared animated micro-interaction/ripple layer to quick-access cards, news cards and controls
- Site logos prefer crisp vector brand marks, then site SVG/Apple Touch/Android icon, icon.horse, high-res Google favicon and further fallbacks
- If a site provides no usable logo, show a generated sharp SVG initial instead of a blurry or broken image
- Quick-access drag still uses a floating preview that follows the pointer and animates neighbouring sites into place

VPN remains intentionally excluded. Ad/tracker blocking is a lightweight hostname list, not a network-wide blocker.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download the artifact named `TUR-BROWSER-APK` from the latest successful workflow run.
