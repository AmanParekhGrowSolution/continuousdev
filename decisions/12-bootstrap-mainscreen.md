## Duplicate notice

> **This issue is a duplicate of #10.** Issue #10 already has an approved plan merged at `decisions/10-bootstrap-mainscreen.md` (PR #11, commit `d4c9f0f`) covering the exact same acceptance criteria, file paths, and out-of-scope list. The implementation PR for #10 has not yet landed at the time of writing — `MainActivity.kt` still mounts the original `Greeting("Android")` scaffold.
>
> Recommended action by a human reviewer: **close #12 as a duplicate of #10** and let the existing plan drive the impl PR (`claude/issue-10`). If for any reason #10 is being abandoned and #12 should drive implementation instead, this SPEC is a complete copy of the #10 SPEC re-pointed at issue #12 so the autonomous pipeline can still proceed.
>
> The rest of this document mirrors `decisions/10-bootstrap-mainscreen.md` 1:1 because the scope is identical. If the two are kept as separate issues, both SPECs must stay in sync — pick one as canonical to avoid drift.

---

# [Issue #12] Bootstrap calendar app shell with MainScreen showing today's date

> **Status:** Draft
> **Issue:** #12
> **Date:** 2026-04-28

---

## Context

The repo is currently the Android Studio scaffold: `MainActivity` mounts a `Greeting("Android")` composable inside `MyApplicationTheme`. There is no `ui/main/` package, no feature scaffolding, and no place for future calendar issues to plug into.

This is the foundation issue for the calendar app. It carves out the first feature package (`ui/main/`), establishes the stateless+wrapper composable pattern that the rest of the app will copy, and replaces the placeholder `Greeting` with something that actually shows today's date. Subsequent issues (calendar grid, navigation, event storage) will mount onto this `MainScreen`.

Issue #12 was filed with identical text to issue #10 (already planned). See the duplicate notice above.

---

## Goal

Launching the app shows a Material 3 screen rendering today's date formatted as e.g. "Tuesday, 28 April 2026", driven by a stateless `MainScreen(today: LocalDate)` composable in `ui/main/MainScreen.kt`.

---

## Non-goals

- Navigation Compose — single-screen app for now.
- Hilt — not introduced in this issue (no DI graph yet; scaffold state per CLAUDE.md).
- Room / event storage — no persistence layer.
- Calendar grid / month view — separate future issue.
- A `ViewModel` — there is no state to hoist beyond the date itself, which the wrapper supplies. Adding a ViewModel just to satisfy the MVVM convention would be over-engineering for a screen with zero state transitions, zero events, and no async work.
- Unit tests — issue explicitly waives them for this trivial composable.
- Adding new dependencies to `libs.versions.toml`.

---

## Architecture choice

**Chosen approach:** A single Kotlin file `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` containing:

1. `MainScreen(today: LocalDate, modifier: Modifier = Modifier)` — pure stateless composable. Formats the date with `DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")` and renders it inside a `Scaffold` using `MaterialTheme.typography.headlineMedium` (or similar) and `MaterialTheme.colorScheme`. No business logic beyond the formatter call.
2. `MainScreen(modifier: Modifier = Modifier)` — wrapper that calls `LocalDate.now()` and delegates to the stateless overload. This is the production entry point.
3. Two `@Preview`s (light and dark) calling the stateless overload with a fixed sample date (e.g. `LocalDate.of(2026, 4, 28)` — a Tuesday) so the preview is deterministic and review-friendly.

`MainActivity` is updated to call `MainScreen()` inside the existing `MyApplicationTheme { ... }` block, replacing the `Scaffold { Greeting(...) }` setup. The `Greeting` composable and its preview are deleted (dead code per CLAUDE.md "delete unused code completely").

The `Scaffold` lives inside `MainScreen` rather than `MainActivity` so that future issues that add a `TopAppBar`, FAB, or bottom nav can wire them in one place.

`Locale` for the formatter: use `Locale.getDefault()` (default `DateTimeFormatter.ofPattern` behaviour) so the date matches the device locale. The literal pattern `"EEEE, d MMMM yyyy"` produces "Tuesday, 28 April 2026" structure regardless of locale (only the names of weekday/month are localised). This matches the acceptance-criteria example.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Introduce `MainViewModel` + `MainUiState` now to follow the MVVM convention strictly. | CLAUDE.md mandates MVVM, but with no events, no async, no state transitions, and no Hilt yet, a ViewModel would be a no-op wrapper around `LocalDate.now()`. The issue is explicit: "Stateless composable: `MainScreen(today: LocalDate)`". Deferred until a feature genuinely needs hoisted state (e.g. selected day, month navigation). |
| Keep the `Scaffold` in `MainActivity` and have `MainScreen` render only the inner content. | Splits screen chrome between activity and screen, making it harder for follow-up issues to add a `TopAppBar` or FAB without touching `MainActivity` again. Putting the `Scaffold` in `MainScreen` localises screen-level decisions. |
| Compute the formatted string inside the wrapper and pass `String` to the stateless composable. | Loses type information — previews and any future test would have to construct a pre-formatted string instead of a `LocalDate`. The issue specifies `MainScreen(today: LocalDate)`. |
| Use `Clock.system(...)` injected via parameter for testability. | No tests required for this issue, and no DI yet. Adding a `Clock` parameter now is speculative scaffolding. Revisit if/when MVVM is introduced. |
| Move the formatter to a `domain/` util. | One-line `DateTimeFormatter.ofPattern(...)` call doesn't justify a utility module. Inline it; extract later if a second screen needs the same format. |
| Treat #12 as net-new and write a fresh SPEC. | #10 already has an approved SPEC for this exact scope. Either close #12 as duplicate (preferred) or keep this SPEC byte-for-byte aligned with #10's. Diverging the two SPECs would create conflicting plans for the same code. |

---

## Implementation notes

Files to create:

- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` — contains the stateless `MainScreen(today: LocalDate, modifier: Modifier)`, the wrapper `MainScreen(modifier: Modifier)`, and two `@Preview`s (`MainScreenLightPreview`, `MainScreenDarkPreview`) using `uiMode = Configuration.UI_MODE_NIGHT_YES` for the dark variant. Both previews wrap in `MyApplicationTheme` and call the stateless overload with a fixed sample date.

Files to modify:

- `app/src/main/java/com/example/myapplication/MainActivity.kt` — replace the `Scaffold { Greeting(...) }` body with a single call to `MainScreen()`. Delete the `Greeting` composable and `GreetingPreview`. Keep `enableEdgeToEdge()` and the `MyApplicationTheme` wrapper.

No changes to:

- `libs.versions.toml` (no new dependencies).
- `strings.xml` — the date string is computed at runtime, not a localised string resource. The format pattern itself is a developer-facing constant, not user-facing copy.
- `themes.xml`, `Theme.kt`, `Color.kt`, `Type.kt` — existing theme is reused as-is.
- Hilt modules / Room / DataStore — none introduced.
- Navigation graph — none introduced.

Layout sketch inside `MainScreen`: a `Scaffold` whose content slot is a `Box` (or `Column`) filling max size with the date `Text` centred horizontally and padded from the top per Material spec. Use `Modifier.padding(innerPadding)` so the content respects insets from edge-to-edge. Use `MaterialTheme.typography.headlineMedium` for the date and `MaterialTheme.colorScheme.onBackground` for its colour. No hardcoded `dp` — use `Modifier.padding(WindowInsets... )` or `Arrangement.Center`/`Alignment.CenterHorizontally` for positioning.

Keep total Kotlin diff under ~80 lines per the issue's note.

**Coordination with #10:** if both #10 and #12 produce impl PRs, only one can land — the second will fail to merge or no-op. The execute phase should check whether `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` already exists (from a merged #10 impl PR) before doing anything; if it does, this issue should be closed without an impl PR.

---

## Open questions

Assumptions made (flagged for human confirmation; default behaviour described under each):

- [ ] **Duplicate of #10 — close or implement?** This issue is byte-for-byte identical to #10 in user-facing summary, acceptance criteria, and out-of-scope. #10 already has an approved plan SPEC merged. Default recommendation: **close #12 as a duplicate of #10** and let the existing plan drive impl. If #12 should drive implementation instead (e.g. #10 was abandoned), say so on the plan PR and close #10.
- [ ] **Package name.** Issue says `app/src/main/java/<package>/ui/main/MainScreen.kt`. The current scaffold uses `com.example.myapplication`. Assumption: keep `com.example.myapplication` for this issue and treat package rename as a separate concern. If a calendar-specific package (e.g. `com.example.calendar`) is wanted, do that in its own issue before more code lands.
- [ ] **Locale for the date format.** The pattern `"EEEE, d MMMM yyyy"` produces "Tuesday, 28 April 2026" using the device locale's localised weekday/month names. On a non-English locale the words will be translated. Assumption: this is desired (i18n-friendly). If the date must always be English regardless of device locale, pass `Locale.ENGLISH` explicitly to `DateTimeFormatter.ofPattern`.
- [ ] **`Scaffold` placement.** Choosing to put the `Scaffold` inside `MainScreen` rather than `MainActivity`. This is a forward-looking choice for future top-bar/FAB issues. Pushback welcome.
- [ ] **No `ViewModel` despite MVVM convention.** The issue explicitly asks for `MainScreen(today: LocalDate)`. CLAUDE.md says "One ViewModel per screen". Resolved by treating this issue as bootstrap that pre-dates the DI graph; a `MainViewModel` will be added when the screen needs state. If reviewers want a `MainViewModel` *now* (without Hilt, manual instantiation), say so on the plan PR.
- [ ] **Edge-to-edge inset handling.** `MainActivity` already calls `enableEdgeToEdge()`. Assumption: `Scaffold`'s default insets are sufficient for a single-`Text` screen. No explicit `WindowInsets` plumbing needed yet.
- [ ] **No string resource for the date format pattern.** The pattern `"EEEE, d MMMM yyyy"` is a code-level format spec, not user-facing copy. Assumption: keep it inline as a `private const val` in `MainScreen.kt`. If house style requires all format patterns in `strings.xml` or a constants file, point that out.

---

## Acceptance criteria

- [ ] App launches to MainScreen (replaces the current `Greeting` placeholder).
- [ ] `MainScreen` lives in `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt`.
- [ ] Uses `MaterialTheme.typography` and `MaterialTheme.colorScheme` — no hardcoded colours, fonts, or `dp` values.
- [ ] Today's date computed with `java.time.LocalDate.now()`, formatted with `DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")`.
- [ ] Stateless composable signature `MainScreen(today: LocalDate)` exists; a wrapper `MainScreen()` calls `LocalDate.now()` and passes it through.
- [ ] `@Preview` light + dark, both using a fixed sample date so previews are deterministic.
- [ ] `MainActivity` calls `MainScreen()` inside the existing `MyApplicationTheme { ... }` block.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green.

---

## Out of scope

- Navigation Compose — single-screen app for this issue.
- Hilt / DI graph.
- Room or any event storage.
- Calendar grid / month view.
- New dependencies in `libs.versions.toml`.
- Unit or instrumentation tests for `MainScreen` (issue explicitly waives them).
- Renaming the application package away from `com.example.myapplication`.
- Any change to theme colours, typography, or `strings.xml`.
