# TV Feature Parity Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans for scoped tasks. Build and emulator operations are coordinated by the main task.

**Goal:** Restore recommendations, tag filtering and Bangumi authorization in the Android TV application.

**Architecture:** Keep shared repositories, search query and OAuth session establishment. TV presentation owns remote navigation, filter drafts and local QR rendering. Work stays in the existing TV worktree and branch.

**Tech Stack:** Kotlin, Compose TV, Paging, existing Animeko APIs, ZXing core 3.5.4 for offline QR encoding.

- [x] Home: add recommendation paging from ExplorationPageState, expose a callback-driven presentation boundary and remote UI regression tests in ui-tv. Verify loaded rows, retry, paging and return focus at 960×540 dp and 720×405 dp.
- [x] Search: implement TvSearchFilters.kt with a query draft, canonical tag groups, year/season/sort and apply/reset/cancel. Integrate TvSearchScreen with UpdateQuery; fix shared empty-query clearing only if required by a failing regression. Test tag-only requests, retained keyword, apply/cancel/reset, custom route tags and focus restoration.
- [x] Authorization: route BangumiAuthorize, add account/login entry, implement TvBangumiAuthorizeScreen and request lifecycle, encode QR locally, and cover timeout/cancel/failure/success. Show QR only for the active request; browser failures do not destroy the QR flow. Test QR decode round-trip with a representative authorization URL.
- [x] Validation: run full ui-tv unit/instrumented suites and any changed shared tests. Build three APKs, install over previous preview, inspect real recommendations, filter results and authorization QR; do not complete a real user login or publish active authorization evidence.
- [x] Delivery: update current docs and validation report, preserve old APK and Release, copy new APKs with the same package/signature, verify checksums, commit/push and publish a new prerelease to N3urda/animeko.

Run with the task-local JBR 21 / SDK / Gradle home:

```sh
./gradlew :app:shared:ui-tv:testDebugUnitTest :app:shared:ui-tv:connectedDebugAndroidTest :app:android:assembleDefaultDebug --no-configuration-cache --parallel --max-workers=6
```

Check behavior and layout bounds, not Android assertScreenshot (a no-op here). Release only verified artifacts at the committed revision; declare physical-TV and real-account validation limits.

Published prerelease: [android-tv-preview-20260921](https://github.com/N3urda/animeko/releases/tag/android-tv-preview-20260921), source `ca3bfb223084656d2d90138c51c10b4cd9ccf6e2`. All 124 scoped tests pass. Five release assets match the local SHA-256 digests; the public universal APK download also matches.
