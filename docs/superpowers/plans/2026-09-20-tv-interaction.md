# TV Interaction Implementation Plan

**Goal:** Complete the remote interaction requirements in the TV interaction design and deliver a verified upgrade APK.

**Architecture:** Keep repositories, navigation routes and playback sessions shared. UI-only changes stay in `app/shared/ui-tv`; small presentation components expose state and callbacks for synthetic D-pad tests. Separate file ownership allows catalogue, player and settings work to proceed independently.

**Tech Stack:** Kotlin, Compose TV Material 1.1.0, Navigation 3, Media3/Mediamp, Android TV API 34 emulator.

## Tasks

- [x] Shared components — `TvComponents.kt`, `TvTheme.kt`, new component tests. Give `TvButton` an optional selected state, coherent focus border and constrained scale; use TV Card border/scale APIs rather than nested visual borders. Preserve full semantics labels and hidden-content behavior. Verify disabled targets skipped and selected state distinct from focus with synthetic key input.
- [x] Catalogue — `TvHomeScreen`, `TvSearchScreen`, `TvCollectionsScreen`, `TvHistoryScreen`, `TvSubjectScreen`, catalogue tests. Unify home navigation; show actual titles and readable metadata; add long-episode access; reject unbroadcast episodes. Test navigation order, episode IDs across grouping, disabled selection and return focus.
- [x] Player — `TvPlayerInput`, `TvPlayerScreen`, extracted player presentation components and tests. Write failing tests for one-confirm toggle with consumed release/repeat, preserve seek tests, then implement result actions. Add progress track and transport/secondary panels, focus current choice and return to trigger, contextual loading and recovery actions.
- [x] Settings and login — `TvSettingsScreen`, `TvLoginScreen`, `TvTextField`, `TvDialogs`, corresponding tests. Build category/content focus paths and user-readable settings rows, scroll forms/dialog content, preserve explicit IME opening, test cancellation/default focus and input escape.
- [x] Integrated review — inspect shared changes and each area against the design; repair cross-area focus, layout and lifecycle errors. Do not operate the emulator concurrently with instrumented tests.
- [x] Validation — 30 TV unit, 64 instrumented and 4 device mode tests pass; APK assembled. Actual TV screenshots cover 960×540 dp and 720×405 dp, live search, playback controls, source panel, history, settings, keyboard, Home and return. Keyboard tests also assert complete field bounds at 105 dp available height.
- [x] Delivery — prior APK retained; named interaction APKs, matching signature, ARM64/ARM32 ABI, installation, checksums, reports and current behavior/limits documented in `output/android-tv/`. Code and docs are saved as a separate commit on the existing fork branch `codex/android-tv`.

## Commands

Use task-local JBR 21, Android SDK and Gradle cache under the original checkout's `.tv-build/`. The existing emulator is `Animeko_TV_API_34`. Run from the TV worktree:

```sh
./gradlew :app:shared:ui-tv:testDebugUnitTest \
  :app:shared:ui-tv:connectedDebugAndroidTest \
  :app:android:assembleDefaultDebug \
  --no-configuration-cache --parallel --max-workers=6
./gradlew :app:shared:app-platform:testAndroidHostTest \
  --tests me.him188.ani.app.platform.DeviceUiModeTest --no-configuration-cache
```

Expected: no failed tests, installable signed APK, clear focus and reachable controls in inspected runtime screenshots. Android `assertScreenshot` is a no-op in this checkout; it is not visual verification. External-service failures are recorded separately from UI results and require a usable retry or alternative route.
