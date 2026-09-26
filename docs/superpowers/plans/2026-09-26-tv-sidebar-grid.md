# TV Sidebar and Recommendation Grid Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for isolated implementation and review. Preserve the existing clean TV worktree and branch.

**Goal:** Add a left navigation rail, vertical recommendation grid and richer focus-aware background.

**Architecture:** Use one lazy vertical container for overview sections and recommendation cells, with full-span overview sections. Preserve paging indexes and business-key focus restoration. Keep background rendering separate from paging and data loading.

**Tech Stack:** Kotlin, Compose TV, Paging 3, existing TV focus state, Android Compose instrumentation.

## Task 1: Background

- [x] Add `TvHomeBackdrop.kt`: choose only an unmasked current poster image, draw right-aligned artwork with horizontal/vertical dark gradients and static blue/violet illumination. Reuse existing `AsyncImage` without crossfade.
- [x] Update `TvHomePresentation.kt` so the fixed preview panel lets the backdrop contribute to the composition while preserving text contrast and height.
- [x] Add a test for masked, known restricted, mismatched-details and missing-image background selection.

## Task 2: Sidebar and vertical recommendations

- [x] Add failing Compose regression coverage for Down advancing by a grid row, leftmost-card Left returning to the sidebar, sidebar Right restoring content, and sidebar bounds at 720×405dp.
- [x] Implement sidebar navigation in `TvHomeScreen.kt`, including explicit overview/recommendation anchors and stable return focus. Add the backdrop behind all home content.
- [x] Compose recommendation items directly in the vertical lazy container; use four columns at 960dp and three at 720dp, full-span overview sections and footer states. Keep `pager[entry.pagingIndex]` access and bounded target-page recovery.
- [x] Update old horizontal-navigation test paths; verify pagination, append retry, missing target fallback, terminal partial row and focus after detail return.

## Task 3: Verification and delivery

- [x] Run `:app:shared:ui-tv:testDebugUnitTest` and affected Android instrumentation, then complete the TV instrumentation regression once changes stabilize.
- [x] Build `:app:android:assembleDefaultTvPreview`, install the optimized ARM64 APK, inspect screenshots at both viewport sizes and drive real recommendation paging/detail-return paths.
- [x] Complete independent specification and code quality review; update installation and verification documentation with actual results.
- [ ] Commit and push, publish dated versioned APKs/checksums/screenshots to the user's fork, and verify uploaded digests and download access.
