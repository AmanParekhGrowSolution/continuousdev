# [Issue #8] Bootstrap calendar app shell with MainScreen showing today's date

> **Status:** Draft
> **Issue:** #8
> **Date:** 2026-04-28

---

## Context

The project is a freshly bootstrapped Compose + Material 3 scaffold. `MainActivity` currently hosts a `Greeting("Android")` placeholder. Before any calendar feature work (grid, navigation, event storage) can begin, we need a stable host screen — `MainScreen` — that future issues can mount the calendar grid, navigation, and event UI onto. This issue establishes the feature package layout (`ui/main/`), the stateless-screen + stateful-wrapper convention from CLAUDE.md, and the date-formatting baseline.

---

## Goal

When the app launches, the user sees a Material 3 screen titled with today's date in the format `Tuesday, 28 April 2026`, hosted by `MainScreen` under `ui/main/`, with `MainActivity` no longer rendering the `Greeting` placeholder.

---

## Non-goals

- Navigation Compose / type-safe routes (deferred to a later issue).
- Hilt setup / `ViewModel`s (this screen has no state to hoist beyond a `LocalDate` injected at the call site, so a `ViewModel` would be premature per CLAUDE.md "don't add abstractions beyond what the task requires").
- Room, DataStore, or any event-storage layer.
- Calendar grid, month navigation, or event UI.
- Unit / Compose UI tests (issue explicitly states none needed for this trivial composable).
- New dependencies — Compose + Material 3 already in `libs.versions.toml` are sufficient.

---

## Architecture choice

**Chosen approach:**

Follow CLAUDE.md's "Composables" rule literally: ship two composable overloads in `ui/main/MainScreen.kt`.

1. `MainScreen(today: LocalDate)` — the stateless, preview-friendly version. It renders a Material 3 `Scaffold` with the formatted date as a `Text` styled via `MaterialTheme.typography.headlineMedium` (or similar token) and `MaterialTheme.colorScheme.onBackground`. No I/O, no clocks, no side effects.
2. `MainScreen()` — a thin wrapper that calls `LocalDate.now()` and forwards to the stateless overload. This is the only place a non-deterministic clock read happens.

Date formatting: `DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")` applied inside the stateless composable. Formatter is built once at the top of the file as a `private val` to avoid re-allocating per recomposition. Locale is left unspecified for now — see Open questions.

`MainActivity` is updated to call `MainScreen()` (no arg) inside `MyApplicationTheme { Scaffold { ... } }`. The existing `Greeting` and `GreetingPreview` are deleted (CLAUDE.md: "delete unused code completely; no `// removed` comments").

`@Preview` coverage: two previews wired with `uiMode = UI_MODE_NIGHT_NO` and `uiMode = UI_MODE_NIGHT_YES`, both passing a fixed `LocalDate.of(2026, 4, 28)` so previews are deterministic across machines and dates. Each preview wraps the call in `MyApplicationTheme`.

No `ViewModel`, no `StateFlow`, no event sealed interface — there is no state to mutate and no events to handle. CLAUDE.md's MVVM rule is about screens with state; introducing a ViewModel here would violate the "don't add abstractions beyond what the task requires" guidance. When a future issue needs mutable state (selected day, event list), the stateless `MainScreen(state, onEvent)` overload will be added then.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Introduce `MainViewModel` + `MainUiState` data class now to "future-proof" the screen | Adds abstraction with no current state to hold. CLAUDE.md explicitly forbids designing for hypothetical future requirements. The next issue that adds real state can introduce the ViewModel together with the state it owns. |
| Pass a `Clock` (or `() -> LocalDate`) into `MainScreen()` to make the wrapper testable | The issue calls for "no tests needed for this trivial composable." A `Clock` parameter would be premature ceremony. The stateless `MainScreen(today: LocalDate)` already gives full determinism for previews and any future tests. |
| Keep date-formatting logic in a separate `domain/` use case | One-line `formatter.format(today)` does not warrant a `domain/usecase/` file. CLAUDE.md: "Three similar lines is better than a premature abstraction." |
| Use `Text` with a hardcoded `fontSize = 24.sp` | Forbidden by CLAUDE.md ("No hardcoded `dp`, `sp`, or color literals"). Use `MaterialTheme.typography` instead. |
| Render inside a top-level `Column` instead of `Scaffold` | `Scaffold` is already established in `MainActivity`. Adding a second one inside `MainScreen` would double-pad. Decision: `MainActivity` keeps its outer `Scaffold` and passes `innerPadding` through; `MainScreen` accepts a `Modifier` and renders a `Box` or `Column` filling the available space. |

---

## Implementation notes

Files to create:

- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` — contains:
  - `private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")`
  - `@Composable fun MainScreen(modifier: Modifier = Modifier)` — wrapper, calls `LocalDate.now()`.
  - `@Composable fun MainScreen(today: LocalDate, modifier: Modifier = Modifier)` — stateless renderer.
  - `@Preview` light + dark, both with `today = LocalDate.of(2026, 4, 28)`.

Files to modify:

- `app/src/main/java/com/example/myapplication/MainActivity.kt`:
  - Replace `Greeting(name = "Android", modifier = Modifier.padding(innerPadding))` with `MainScreen(modifier = Modifier.padding(innerPadding))`.
  - Delete the `Greeting` composable and `GreetingPreview`.
  - Remove the now-unused `androidx.compose.material3.Text` import if nothing else in the file uses it.

No changes to:

- `libs.versions.toml`
- `build.gradle.kts`
- `ui/theme/*`
- `AndroidManifest.xml`
- `strings.xml` — see Open questions; the formatted date is dynamic content rendered from `java.time`, not a fixed UI string.

Expected diff size: well under the ~80-line cap. Roughly 50–60 lines of new Kotlin in `MainScreen.kt` plus a ~5-line edit to `MainActivity.kt`.

---

## Open questions

Assumptions made (flag if any are wrong):

- [ ] **Package name.** Assumed `com.example.myapplication` based on the existing `MainActivity.kt`. The path in CLAUDE.md uses `<package>` as a placeholder. Confirm this is the intended long-term package or if it should be renamed before more code lands.
- [ ] **Locale.** `DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")` without a `Locale` argument uses the device default. The example output in the issue ("Tuesday, 28 April 2026") is English. Plan: use the device default so the date localises naturally on non-English devices, since the pattern itself is locale-friendly. Confirm this is acceptable, or whether we should pin `Locale.ENGLISH` for now.
- [ ] **strings.xml.** CLAUDE.md says "every user-facing string lives in `res/values/strings.xml`." The only user-facing string here is the formatted date, which is generated at runtime from `java.time` — there is no static literal to extract. Plan: do not add anything to `strings.xml` for this issue. Confirm.
- [ ] **Typography token.** Plan to use `MaterialTheme.typography.headlineMedium` for the date. Open to a different token (e.g. `displaySmall`, `titleLarge`) if the design intent is different. No mockup was attached to the issue.
- [ ] **Layout.** Plan: centre the date both horizontally and vertically in the available space (a `Box` with `contentAlignment = Alignment.Center`). The issue is silent on layout; this seemed the most useful default for a placeholder shell. Confirm or specify alignment.
- [ ] **Preview file location.** Previews live in the same `MainScreen.kt` file (CLAUDE.md convention). Confirm this rather than a separate `MainScreenPreview.kt`.

---

## Acceptance criteria

Copied from the issue:

- [ ] App launches to MainScreen (replace any current Greeting placeholder).
- [ ] MainScreen is in `app/src/main/java/<package>/ui/main/MainScreen.kt`.
- [ ] Uses `MaterialTheme.typography` and `MaterialTheme.colorScheme` — no hardcoded colours, fonts, or dp values.
- [ ] Today's date computed with `java.time.LocalDate.now()`, formatted with `DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")`.
- [ ] Stateless composable: `MainScreen(today: LocalDate)`. A wrapper `MainScreen()` calls `LocalDate.now()` and passes it in.
- [ ] `@Preview` (light + dark) with a fixed sample date so the preview is deterministic.
- [ ] `MainActivity` calls `MainScreen()` inside the existing theme block.

Plus from CLAUDE.md:

- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green locally / in CI.
- [ ] No new dependencies added to `libs.versions.toml`.
- [ ] Diff is ≲ 80 lines of Kotlin.

---

## Out of scope

Copied from the issue:

- Navigation Compose
- Hilt
- Room / event storage
- Calendar grid
- Tests (issue explicitly: "No tests needed for this trivial composable.")
- New dependencies

---

## Closes-plan-of #8
