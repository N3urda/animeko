# Android TV Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement bounded tasks, and review specification compliance before code quality.

**Goal:** Deliver an installable Animeko APK with a dedicated remote-operated TV experience, sharing existing content, account and playback services.

**Architecture:** Android-only `app/shared/ui-tv` depends on `app/shared`, while the Android application selects the TV or standard root. TV routes use existing repositories/ViewModels and the same playback session. TV components and remote control logic are isolated from phone/desktop interaction.

**Tech Stack:** Kotlin, Compose TV Material, Navigation 3, Koin, Mediamp/ExoPlayer, Android API 27+, Gradle.

## Tasks and evidence

- [x] **Build baseline** — install task-local JBR 21 and Android SDK, resolve Gradle dependencies, run `:app:android:assembleDefaultDebug`, record pre-existing failures and repair build prerequisites.
- [x] **TV module and primitives** — add `app/shared/ui-tv/build.gradle.kts`, `TvTheme`, focusable buttons/cards and focus memory; wire `settings.gradle.kts` and version catalog. Test remote focus and activation before implementing controls.
- [x] **Catalogue** — TV home, search, collections/history, subject details and episode selection. Reuse public ViewModels; synthetic D-pad tests cover empty state, pagination and return focus.
- [x] **Playback** — TV player screen/overlay uses `EpisodeViewModel.player`, existing video/subtitle/danmaku rendering, resource and episode selectors. Add a pure input reducer and tests for show/hide, seek preview/commit/cancel and key repeat. Add Android MediaSession bridge with supported commands and lifecycle handling.
- [x] **Account and settings** — remote-operable email/OTP login; subscription list, URL entry/update/enablement; subtitle/danmaku/cache settings; actionable error and session-expiry UI. QR authorization remains gated on actual cross-device verification.
- [x] **App integration** — TV launcher/banner, device mode preference/detection, shared initialization and overlay injection, navigation entries, deep links, Back semantics. Preserve standard root for phones. Test dispatch and mode persistence.
- [x] **Validation** — compile, unit and interactive UI tests; inspect merged manifest, min SDK, native libraries and signing; install on a compatible emulator and verify launcher, D-pad, IME and actual playback. Record evidence and distinguish emulator results from user TV compatibility.
- [x] **Delivery** — copy verified APKs to `output/android-tv`, write Chinese install/testing notes, checksums and known limitations, keep fixed app ID/signing for iterations, review code and scope.

## Ownership and commands

The controller owns build tooling, app integration, validation and deliverables. Delegate one implementation area at a time, with disjoint file ownership; use separate read-only reviewers after each area. Do not commit unrelated changes, overwrite user configuration, or modify generated API clients.

Build from this worktree with task-local `JAVA_HOME`, `ANDROID_HOME` and `GRADLE_USER_HOME`. Effective ABI selection must be checked because local properties override `-P`.

```sh
./gradlew :app:android:assembleDefaultDebug
./gradlew :app:shared:ui-tv:testDebugUnitTest
./gradlew :app:shared:ui-tv:connectedDebugAndroidTest
```

Confirm actual task names after creating the Android library. Android `assertScreenshot` is currently a no-op; use semantic assertions and real screenshot inspection, not that helper as visual proof.

## Completion requirements

An actual signed APK must exist and its installability must be verified. The TV root must support first-run configuration, login, content discovery, episode/resource selection and playback with only remote keys. A manifest-only adaptation does not satisfy the goal. Device-specific decoding and firmware compatibility remain identified for the user's television test; unresolved core implementation or build failures keep the goal active.

## Verified delivery — 2026-09-20

The preview APK is built and signed in `output/android-tv/`, with Chinese installation instructions, SHA-256 checksums, selected runtime screenshots and XML test reports. See [Android TV preview guide](../../android-tv-preview.md) for installation, build configuration and exact validation limits.

- Final `assembleDefaultDebug` and the TV test tasks succeeded. TV unit tests: 14 passed; Android TV instrumented tests: 27 passed; device-mode host tests: 4 passed. No tests were skipped in these suites. The full repository `check` was not run.
- The actual delivered universal APK installed as an update on Android TV API 34 ARM64. Its manifest declares API 27 minimum, optional touchscreen and Leanback, and a TV launcher entry. Native libraries for ARM64 and ARM32 are present. APK Signature Scheme v2 verification passed.
- Real remote-key checks covered launcher banner, catalogue, TV keyboard, online search, episode playback, media pause, seek preview/commit, Home/background pause, search focus restoration, local history and restoring the seek target to 6:25, settings and the email input form.
- The preview reuses account APIs, but real OTP login and signed-in synchronization were not exercised. QR authorization is not exposed. Physical television decoding, ARM32 runtime, Android 8.1 runtime, audible output and long-session performance remain device acceptance work.
- Implementation and specification/code reviews are complete for the preview scope. Sources use branch `codex/android-tv` in the [N3urda/animeko fork](https://github.com/N3urda/animeko), with local worktree `.worktrees/android-tv`. APKs, runtime evidence and build caches are local artifacts excluded from source control.

Known preview issue: one web resource remained buffering after reopening and restoring position to 6:25. The cause is unconfirmed; this run proves position restoration but not continuous decoding after that resume. Initial continuous playback was verified separately. This is recorded for the target-TV iteration.
