# TV Home Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enrich the Android TV home screen with focus details, denser rails, today's schedule and genre navigation.

**Architecture:** Keep existing catalogue paging and business-key focus restoration. A TV discovery ViewModel reads existing repositories for one focused subject and today's schedule; presentation stays in TV composables. Phone UI and backend APIs retain their contracts.

**Tech Stack:** Kotlin, Compose TV, Paging 3, coroutines, existing Animeko repositories, Android instrumentation.

## Task 1: Discovery data

- [x] Add `TvHomeDiscoveryViewModel.kt` and unit tests: cancellable delayed subject detail loading, bounded successful-result cache, explicit failed/loading state, schedule loading/retry/date mapping.
- [x] Use `SubjectCollectionRepository.subjectCollectionFlow(id).first().subjectInfo` and `AnimeScheduleRepository.recentAiringSchedulesFlow(date, zone)`; inject suspend providers into a testable state owner.
- [x] Verify rapid A → B selection cannot publish A for B, repeat selection uses cache, failure does not permanently poison cache, cancellation propagates, schedule deduplicates by subject ID.

## Task 2: Home presentation and navigation

- [x] Add `TvHomePresentation.kt` for the compact navigation, focus preview and compact cards. Modify `TvHomeScreen.kt` to compose existing pagers with discovery state and stable section keys.
- [x] Add `TvHomeDiscoveryTest.kt`: verify two loaded rows inside 960×540dp viewport, focus title updates after Right, category callback carries canonical tag, compact card is not clipped.
- [x] Run new instrumented tests against the baseline and confirm the missing layout behavior, then implement.
- [x] Wire `onSearchTag` to `navigateSubjectSearch(tags = listOf(tag))` in `TvAppContent.kt`. Add real schedule states and category row to the home focus registry.
- [x] Keep pagination indexes and NSFW masking intact; update existing navigation expectations only when the intended layout changes the path.

## Task 3: Acceptance and delivery

- [x] Run `:app:shared:ui-tv:testDebugUnitTest :app:shared:ui-tv:connectedDebugAndroidTest`; review spec compliance and code quality.
- [x] Build `:app:android:assembleDefaultTvPreview` with ARM32 and ARM64, inspect package and signature against the latest release, and validate on the TV emulator using screenshots.
- [x] Save actual verification results in `docs/android-tv-preview.md`.
- [x] Commit and push the new branch to the user's fork; upload versioned APKs/checksums/screenshots to a dated release.

**Delivery:** Build commit `2b662f1a07e2663a6fe348060552b505042e2497` on `codex/tv-home-discovery`. [Android TV home release](https://github.com/N3urda/animeko/releases/tag/android-tv-preview-20260926) contains three APKs, checksums, a package manifest, installation and validation notes, and two final screenshots. All eight uploaded assets match local sizes and SHA-256 digests.
