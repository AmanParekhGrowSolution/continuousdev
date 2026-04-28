# [Issue #18] Bootstrap a simple counter screen with increment and decrement buttons

> **Status:** Draft
> **Issue:** #18
> **Date:** 2026-04-28

---

## Context

The repo is currently the Android Studio scaffold: `MainActivity` mounts `Greeting("Android")` inside `MyApplicationTheme`. Several earlier issues (#10/#12 — `MainScreen`; #14 — settings + Hilt + Navigation Compose + DataStore; #16 — bottom navigation bar) have **plan PRs merged** but **no impl PRs landed**. `MainActivity.kt` still hosts the original `Greeting` placeholder; `gradle/libs.versions.toml` has no Hilt, no Navigation Compose, no DataStore, no Lifecycle ViewModel Compose, no `kotlinx-coroutines-test`, no Turbine, no MockK.

Issue #18 redirects the app from the calendar trajectory implied by #10/#12/#14/#16 to a *counter* feature. The asked-for surface is a single screen with a centred number and two buttons (Increment / Decrement, floor at 0); the only architectural nudge is "Counter state survives screen rotation (use `ViewModel`)" plus "Unit test for the ViewModel".

This is the first feature in the codebase with hoisted state, an event API, and a unit-tested `ViewModel`. It is therefore both:

1. A small feature SPEC (one screen, three controls, three tests), and
2. A scaffold-state inflection point (introduce the bare minimum to legitimately have a `ViewModel` per CLAUDE.md — `androidx-lifecycle-viewmodel-compose` for `viewModel()` and `androidx-lifecycle-runtime-compose` for `collectAsStateWithLifecycle()`).

Hilt, Navigation Compose, DataStore, and `DispatcherProvider` are intentionally *not* introduced by this issue. The counter `ViewModel` has zero dependencies, no async work, no I/O, and no persistence — the simplest constructor-arg–free `ViewModel` you can write. Forcing Hilt onto a ViewModel that has nothing to inject is the kind of "speculative scaffolding" CLAUDE.md warns against. If a later feature genuinely needs DI, it will introduce Hilt at that point (much like #14's plan does for settings).

**Coordination with prior plans:** none of #10/#12/#14/#16 has merged code. The counter feature replaces `Greeting` directly; `MainScreen`, `SettingsScreen`, the bottom nav bar, and the calendar direction are *not* prerequisites for #18 and are *not* modified by #18. Whether the calendar plans are still in-flight, paused, or superseded is a stakeholder question — see "Open questions".

---

## Goal

Launching the app shows a Material 3 screen with a large centred number, an "Increment" button that adds 1, and a "Decrement" button that subtracts 1 (clamped at 0); the count survives configuration changes (rotation) because it lives in a `CounterViewModel` whose `StateFlow<CounterUiState>` is the single source of truth.

---

## Non-goals

- Persistence across process death (DataStore, Room) — issue says rotation only, which `ViewModel` alone satisfies.
- A `Reset` button or any third action.
- Negative counts. Floor at 0 is explicit.
- A maximum value or overflow handling. `Int` headroom is fine for a manual tap counter.
- Hilt / DI graph — counter VM has no dependencies.
- Navigation Compose — single-screen app for this issue.
- Room, DataStore, Retrofit, OkHttp, kotlinx.serialization, MockK, Turbine.
- A `DispatcherProvider` — no async work to dispatch.
- `SavedStateHandle` plumbing — see Open questions on process-death survival.
- Theme, palette, or typography changes.
- Animated count transitions, haptics, accessibility-beyond-content-descriptions.
- Tablet / foldable / multi-window layouts.
- Compose UI tests — issue requires only a ViewModel unit test; the screen has trivial state-to-UI mapping.
- Renaming the `com.example.myapplication` package.

---

## Architecture choice

**Chosen approach:** MVVM with a no-arg `CounterViewModel`, a sealed-interface event API, an immutable `CounterUiState`, and a stateless `CounterScreen(state, onEvent)` plus a wrapper `CounterScreen(viewModel)`. ViewModel obtained via Compose's built-in `viewModel()` (from `androidx.lifecycle:lifecycle-viewmodel-compose`) — no Hilt, because the VM has no dependencies to inject.

Layered like this:

1. **State** — `ui/counter/CounterUiState.kt`:
   ```kotlin
   data class CounterUiState(val count: Int = 0)
   ```
   Single field. No `isLoading` (nothing to load), no error state (no failure modes), no sealed-interface variants. If a future feature adds persistence, the state can grow then.

2. **Events** — `ui/counter/CounterEvent.kt`:
   ```kotlin
   sealed interface CounterEvent {
       data object Increment : CounterEvent
       data object Decrement : CounterEvent
   }
   ```
   Backs the single `onEvent(CounterEvent)` entry point per CLAUDE.md.

3. **ViewModel** — `ui/counter/CounterViewModel.kt`:
   ```kotlin
   class CounterViewModel : ViewModel() {
       private val _uiState = MutableStateFlow(CounterUiState())
       val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

       fun onEvent(event: CounterEvent) {
           when (event) {
               CounterEvent.Increment ->
                   _uiState.update { it.copy(count = it.count + 1) }
               CounterEvent.Decrement ->
                   _uiState.update { it.copy(count = (it.count - 1).coerceAtLeast(0)) }
           }
       }
   }
   ```
   No `@HiltViewModel`. No constructor args. No `viewModelScope.launch` — every event is synchronous, so spawning a coroutine for a single `_uiState.update { ... }` would be ceremony for its own sake. The floor-at-0 invariant lives in the ViewModel (not the UI) so the unit test can exercise it directly. `Int` overflow is theoretically possible after ~2.1 billion taps; not worth defending against (see "Things explicitly not done").

4. **UI** — `ui/counter/CounterScreen.kt` contains:
   - `CounterScreen(viewModel: CounterViewModel = viewModel(), modifier: Modifier = Modifier)` — collects `viewModel.uiState` with `collectAsStateWithLifecycle()` and forwards to the stateless overload, passing `viewModel::onEvent` as the event sink.
   - `CounterScreen(state: CounterUiState, onEvent: (CounterEvent) -> Unit, modifier: Modifier = Modifier)` — pure stateless. Renders a `Scaffold` whose content is a `Column` filling max size, `Arrangement.Center` + `Alignment.CenterHorizontally`, with:
     - `Text(text = state.count.toString(), style = MaterialTheme.typography.displayLarge)` — the centred number.
     - A `Row` of two `Button`s, gapped via `Arrangement.spacedBy`. The `Decrement` button passes `enabled = state.count > 0` so the floor is also visible at the UI level (a disabled tap is a no-op anyway because of the VM clamp, but disabling gives the user feedback that decrement is unavailable at zero — see "Open questions" if reviewers prefer the button to stay enabled and quietly clamp).
   - Two `@Preview`s (light + dark) calling the stateless overload with a fixed sample state (e.g. `CounterUiState(count = 0)` to exercise the disabled-decrement state, and a second preview with `count = 5` to exercise the enabled state — four previews total: light/dark × zero/non-zero).
   - All user-facing strings (`"Increment"`, `"Decrement"`, screen title if any) come from `strings.xml` via `stringResource`. The number itself is rendered via `state.count.toString()` — `toString` on `Int` produces ASCII digits, which is acceptable for a counter; if locale-aware digits are wanted, switch to `NumberFormat.getInstance().format(state.count)`. Default: ASCII (see "Open questions").

5. **`MainActivity`** — replace the inner `Scaffold { Greeting(...) }` with a single call to `CounterScreen()` inside the existing `MyApplicationTheme { ... }` block. Delete `Greeting` and `GreetingPreview` (dead code per CLAUDE.md "delete unused code completely"). Keep `enableEdgeToEdge()` and the theme wrapper. Move the `Scaffold` *into* `CounterScreen` (the screen owns its chrome) so future issues that add a `TopAppBar` or FAB to this screen wire them in one place.

6. **Tests** — `app/src/test/java/com/example/myapplication/ui/counter/CounterViewModelTest.kt`. Plain JUnit 4. Three tests:
   - `incrementIncreasesCount` — initial state is `count = 0`; after `onEvent(Increment)`, `uiState.value.count == 1`; after another, `== 2`.
   - `decrementDecreasesCount` — start from `count = 3` (achieved by three `Increment`s), decrement once → `count == 2`, again → `count == 1`.
   - `decrementAtZeroIsFloored` — initial state `count = 0`; `onEvent(Decrement)` leaves `count == 0`; multiple decrements still leave `count == 0`; subsequent `Increment` correctly resumes from `0 → 1`.

   Assertions read `viewModel.uiState.value` directly — `MutableStateFlow.value` is synchronous and safe to read from a test thread for a ViewModel whose event handler does no async work. No `runTest`, no `Dispatchers.setMain`, no Turbine, no MockK. If a reviewer prefers Turbine for `Flow` testing as a forward-investment in the test toolkit, see "Open questions".

7. **Resources** — strings added to `app/src/main/res/values/strings.xml`:
   - `<string name="counter_button_increment">Increment</string>`
   - `<string name="counter_button_decrement">Decrement</string>`
   - `<string name="counter_count_content_description">Counter value: %1$d</string>` — applied via `Modifier.semantics { contentDescription = ... }` on the `Text` so TalkBack reads the value rather than spelling out digits one at a time. Uses positional placeholder per CLAUDE.md.
   - The two button labels are visible `Text` inside the buttons, providing their own semantics — no extra `contentDescription` needed.
   No new colours, dimensions, or typography tokens.

The composables stay pure (no I/O, no `runBlocking`, no repository calls, no `GlobalScope`). The `Scaffold` lives inside `CounterScreen`, not `MainActivity`. Both light and dark previews exist. `MaterialTheme.typography` and `MaterialTheme.colorScheme` provide all styling — no hardcoded `dp`/`sp`/colour literals (any spacing comes from theme-derived sizes or `Arrangement.spacedBy(MaterialTheme.spacing.*)`-equivalent constants — concretely, we'll use a `dimensionResource` for the inter-button gap, or a single small `dp` literal in `CounterScreen` if reviewers accept it as a layout constant — see "Open questions"). All async-handling rules (`viewModelScope`, `collectAsStateWithLifecycle`) are followed where applicable; for the `viewModelScope` rule, this issue's VM has no coroutines so the rule is vacuously satisfied.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| No `ViewModel` — keep state in the composable via `rememberSaveable { mutableStateOf(0) }`. | `rememberSaveable` survives configuration changes, so it would technically meet the rotation-survival acceptance criterion. But the issue explicitly says "use `ViewModel`", and CLAUDE.md mandates "One `ViewModel` per screen" with `StateFlow<UiState>`. Also: a `rememberSaveable` count couldn't be unit-tested as required ("Unit test for the ViewModel"). |
| Introduce Hilt now (`@HiltAndroidApp class App : Application()`, `@AndroidEntryPoint MainActivity`, `@HiltViewModel class CounterViewModel @Inject constructor()`). | The VM has no dependencies. Hilt would be six new pieces of plumbing (Application class, manifest entry, KSP plugin, Hilt Android plugin, Hilt module package, dependency entries) for zero injection. CLAUDE.md says "add when the first feature that needs them is implemented"; this feature does not need DI. The first feature that does (e.g. settings/persistence) will introduce Hilt. |
| Use `SavedStateHandle` so the count survives process death too. | Issue says rotation, not process death. Adding `SavedStateHandle` here is speculative scaffolding without a stated need. Trivial to add later (one line: `private val _uiState = MutableStateFlow(CounterUiState(count = handle["count"] ?: 0))` plus a `handle["count"] = it.count` write) if reviewers want it. |
| Persist the count to DataStore so it survives uninstall-reinstall / process death. | Same reason — out of scope for the stated acceptance criteria, and would force introducing DataStore + a repository layer for a single `Int`. |
| Two specialised events (`Increment`, `Decrement`) replaced by one `SetCount(Int)` event. | The UI doesn't compute the next value (the VM does, including the floor). Pushing the increment/decrement *math* into the UI duplicates the floor-at-0 invariant in two places. Two events keep the invariant in one place. |
| Three events: `Increment`, `Decrement`, `Reset`. | Issue does not ask for reset. Add when asked. |
| `var count by mutableIntStateOf(0)` inside the ViewModel (Compose-state-in-VM pattern). | CLAUDE.md says "Expose `StateFlow<UiState>`". Compose-state in VMs is a valid pattern but not the one prescribed. |
| Disable the `Decrement` button at `count == 0`. (This is the chosen default; the alternative is "always enabled, VM clamps silently".) | Trade-off discussed under "Open questions". Default chosen: button is disabled when `count == 0`, giving the user a visible cue. The VM still clamps regardless — defence in depth. |
| Floor-at-0 enforced in the UI (`if (state.count > 0) onEvent(Decrement)` inside the button's `onClick`). | Splits the invariant between UI and VM. The unit test requires the VM to enforce it (testing the UI's `onClick` would need a Compose UI test). Keeping the clamp solely in the VM keeps the test simple and the invariant authoritative. |
| Use `StateFlow<Int>` instead of `StateFlow<CounterUiState>`. | Saves a wrapper class for one field today. But CLAUDE.md prescribes `<Feature>UiState` as a data class, and the next time we add a field (e.g. `isResetEnabled`, `lastChangeAt`), we'd churn every consumer. The wrapper is two lines today and free insurance. |
| Long-press-to-repeat on the buttons. | Out of scope; issue specifies single-tap increment/decrement. |
| Wrap each event handler in `viewModelScope.launch { ... }`. | The handlers are synchronous `_uiState.update { ... }` calls. Adding a coroutine boundary serialises updates through a dispatcher hop for no benefit and complicates the test (now needs `runTest` + `Dispatchers.setMain`). |
| Use Turbine for the unit test. | Turbine excels for `Flow` ordering assertions over multi-emission timelines. Reading `MutableStateFlow.value` synchronously after each event is simpler and equally rigorous for a single-field state. Turbine adds a dependency we don't otherwise need this issue. |
| Use MockK for the unit test. | No collaborator to mock — the VM has no dependencies. |
| Move increment/decrement math into a `CountReducer` use case under `domain/usecase/`. | Use cases are warranted when the same logic is reused across ViewModels or when it has non-trivial coordination/branching. `count + 1` and `(count - 1).coerceAtLeast(0)` is two lines; extraction is over-engineering. |
| Put the `Scaffold` in `MainActivity` and have `CounterScreen` render only inner content. | Splits chrome between activity and screen, making future top-bar/FAB additions touch `MainActivity`. Putting the `Scaffold` in the screen localises chrome decisions. |
| Compute and render the count via `NumberFormat.getInstance().format(...)` for locale-aware digits. | A counter is conceptually a number, not a numeric value with locale meaning (no thousand-separators expected for typical small values). Default: `Int.toString()`. Reviewers can flip to `NumberFormat` if they want digit-localisation; see "Open questions". |
| Hardcode the inter-button gap in `dp` inside the composable. | CLAUDE.md: "No hardcoded `dp`, `sp`, or color literals in composables". Use `dimensionResource(R.dimen.counter_button_gap)` or one of Material's spacing tokens. See "Open questions" — `dimensionResource` is the safer call but also the bigger ceremony. |

---

## Implementation notes

### New dependencies (`gradle/libs.versions.toml`)

Add the bare minimum to support `viewModel()` from a composable and `collectAsStateWithLifecycle()`:

- `androidx-lifecycle-viewmodel-compose` — `androidx.lifecycle:lifecycle-viewmodel-compose` (provides the `viewModel()` function used by the wrapper composable).
- `androidx-lifecycle-runtime-compose` — `androidx.lifecycle:lifecycle-runtime-compose` (provides `collectAsStateWithLifecycle()`).

Both versioned via the existing `lifecycleRuntimeKtx` version ref or a new shared `androidxLifecycle` version (execute phase decides the cleaner shape; either works). No KSP, no compiler plugin, no Hilt, no Navigation, no DataStore, no Retrofit, no kotlinx.serialization, no kotlinx-coroutines-test, no Turbine, no MockK.

### Plugin wiring (`app/build.gradle.kts`)

No changes. No new plugins.

### New source files

- `app/src/main/java/com/example/myapplication/ui/counter/CounterUiState.kt` — `data class CounterUiState(val count: Int = 0)`.
- `app/src/main/java/com/example/myapplication/ui/counter/CounterEvent.kt` — sealed interface with `Increment` and `Decrement` `data object`s.
- `app/src/main/java/com/example/myapplication/ui/counter/CounterViewModel.kt` — `class CounterViewModel : ViewModel()` with `MutableStateFlow<CounterUiState>` backing field, `asStateFlow()` exposure, and `fun onEvent(event: CounterEvent)`.
- `app/src/main/java/com/example/myapplication/ui/counter/CounterScreen.kt` — wrapper + stateless composable + four `@Preview`s.

### Modified source files

- `app/src/main/java/com/example/myapplication/MainActivity.kt`:
  - Replace the inner `Scaffold { Greeting(...) }` with a single call to `CounterScreen()`.
  - Delete `Greeting` and `GreetingPreview`.
  - Keep `enableEdgeToEdge()` and the `MyApplicationTheme { ... }` wrapper.
- `app/src/main/res/values/strings.xml`:
  - Add `counter_button_increment`, `counter_button_decrement`, `counter_count_content_description`.
- `gradle/libs.versions.toml`:
  - Add the two `androidx.lifecycle:*-compose` libraries described above.
- `app/build.gradle.kts`:
  - Add `implementation(libs.androidx.lifecycle.viewmodel.compose)` and `implementation(libs.androidx.lifecycle.runtime.compose)` in the dependencies block. (No plugin or compileOptions changes.)

### New test files

- `app/src/test/java/com/example/myapplication/ui/counter/CounterViewModelTest.kt` — three JUnit 4 tests as described above. Reads `viewModel.uiState.value` synchronously. No `runTest`, no `Dispatchers.setMain`, no Turbine, no MockK.

### Files explicitly NOT changed

- `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt` — palette unchanged.
- `themes.xml`, `colors.xml` — unchanged.
- `AndroidManifest.xml` — no `android:name`, no `@AndroidEntryPoint`, no permissions added.
- Any of `ui/main/`, `ui/settings/`, `ui/navigation/`, `ui/common/`, `data/`, `domain/`, `di/`, `core/` — none of those directories exist yet, and #18 does not create them. They are reserved for future features (and for the prior plans that introduce them).

### Diff-size sanity

Roughly 4 new Kotlin files (~110–140 lines total), 1 modified Kotlin file (~10 lines deleted, ~3 added), 3 new strings, 2 new entries in `libs.versions.toml`, 2 new lines in `app/build.gradle.kts`, 1 new test file (~50 lines). Total: ~250 lines of churn.

---

## Open questions

Assumptions made (flagged for human confirmation; default behaviour described under each):

- [ ] **Coordination with #10/#12, #14, #16.** Those four issues' plan PRs are merged but their impl PRs have not landed; together they describe a calendar app (`MainScreen`, `SettingsScreen`, bottom nav, dark-mode persistence). Issue #18 redirects the app to a counter, replacing `Greeting` directly without any of those scaffolding pieces. **Default chosen here:** treat #18 as an independent feature that drops onto the unmodified scaffold; do not pre-introduce Hilt/Nav/DataStore on its behalf, and do not modify or remove the prior plan SPECs. If the calendar plans are now superseded, a human should mark them `Status: Superseded by #18` (or close their issues). If the calendar app is still desired alongside the counter, a human should clarify how the two coexist (counter as the start destination of the bottom nav? counter as a third tab? counter as a temporary placeholder?). Until then, the impl PR for #18 will mount the counter as the only screen.
- [ ] **Hilt deferral.** CLAUDE.md says Hilt is "required by convention but not yet in `libs.versions.toml` — add when the first feature that needs them is implemented". The counter VM has zero dependencies, so this is *not* the first feature that needs Hilt. **Default:** defer; introduce Hilt in the first feature with actual injection needs (e.g. a settings repository). Reviewer can flip this if they want Hilt scaffolded now to amortise it across future features.
- [ ] **`SavedStateHandle` for process-death survival.** Issue says "screen rotation"; `ViewModel` alone covers that. **Default:** no `SavedStateHandle`. If reviewers want process-death survival too, the change is one extra constructor arg and two lines (read the saved value as the initial `count`; write back on every `onEvent`). Note that adding `SavedStateHandle` here means the VM no longer has a no-arg constructor, which means `viewModel()` needs a custom factory or Hilt — pulling in Hilt to scope-creep a feature that doesn't need it.
- [ ] **Disabled vs. always-enabled `Decrement` button at zero.** Two valid UX patterns:
  - *Default chosen:* `enabled = state.count > 0`. Visible cue that decrement is unavailable; matches Material's affordance principle.
  - *Alternative:* always enabled; the VM silently clamps. Slightly more "robust" against state desync but gives no user feedback at zero. Reviewer's call.
  Either way, the floor invariant lives in the VM (defence in depth) so the unit test stays meaningful.
- [ ] **Inter-button gap.** CLAUDE.md forbids hardcoded `dp` in composables. **Default:** add a single dimension `<dimen name="counter_button_gap">16dp</dimen>` to `res/values/dimens.xml` and reference via `dimensionResource`. Alternative: use a Material-3-style spacing token (`MaterialTheme` doesn't expose spacing tokens by default in Compose, so this would mean adding a small theme extension — overkill for one constant). Pure-Compose token (`8.dp` constant defined in a single `Spacing` object): cleaner than littering `dp` literals but slightly speculative. Reviewer's call.
- [ ] **`Int.toString()` vs. `NumberFormat`.** Counter values are small whole numbers; ASCII digits are universally legible and don't need locale-aware separators. **Default:** `state.count.toString()`. Flip to `NumberFormat.getInstance().format(state.count)` if reviewers want Eastern-Arabic / Devanagari / etc. digits on those locales.
- [ ] **TalkBack content description for the count.** The `Text` rendering the count gets a `Modifier.semantics { contentDescription = stringResource(R.string.counter_count_content_description, state.count) }` so screen readers announce "Counter value: 5" rather than spelling "5". **Default:** include the `contentDescription`. If reviewers prefer `liveRegion = LiveRegionMode.Polite` so the value is re-announced on each change, that's an additive change.
- [ ] **Test framework.** Plain JUnit 4 + synchronous `MutableStateFlow.value` reads — no `kotlinx-coroutines-test`, no Turbine, no MockK. **Default:** keep it lean; this VM's behaviour is fully synchronous. Reviewers who want Turbine introduced for forward-compatibility (the next VM with async work *will* need it) can request it; the cost is one new `testImplementation` and a slightly longer test.
- [ ] **`viewModel()` source.** Use `androidx.lifecycle:lifecycle-viewmodel-compose`'s `viewModel()` (not `androidx.hilt:hilt-navigation-compose`'s `hiltViewModel()`). **Default:** the lifecycle-viewmodel-compose one, because there's no Hilt yet. When Hilt lands, this can switch to `hiltViewModel()` if/when the VM grows dependencies.
- [ ] **No `TopAppBar` on the counter screen.** The screen is "centre the number, two buttons below" — no title bar requested. **Default:** no `TopAppBar`. The `Scaffold` is still present (so a future issue can add a top bar by editing one composable).
- [ ] **Package name.** Keep `com.example.myapplication` (matches every prior plan's stance). Counter screen lives at `com.example.myapplication.ui.counter`.
- [ ] **`MainActivity` ViewModel obtained via `viewModel()` inside the composable, not via `by viewModels()` in the activity.** **Default:** the composable-side `viewModel()` — gives the wrapper composable a sensible default and keeps `MainActivity` ignorant of feature ViewModels (matches CLAUDE.md's pattern: `FooScreen(viewModel: FooViewModel = viewModel())`).
- [ ] **Lint rule on hardcoded strings.** All user-facing strings are in `strings.xml`; the only "hardcoded" piece is the count digits themselves, which are computed at runtime. Should pass `lintDebug` cleanly. If a stricter lint config flags the `count_content_description` formatting, the fix is to make the placeholder explicit (`%1$d`) — already done in the proposed string.

---

## Acceptance criteria

Copied from the issue, with concrete verifiable conditions:

- [ ] App launches to a single screen (replaces the current `Greeting` placeholder) showing a centred number and two buttons.
- [ ] An "Increment" button increases the displayed number by 1.
- [ ] A "Decrement" button decreases the displayed number by 1, clamped at 0 (cannot go negative).
- [ ] The displayed count survives screen rotation, demonstrating that state is held in a `ViewModel` and not in the composable.
- [ ] The screen follows Compose + Material 3 conventions per CLAUDE.md: stateless composable + wrapper, `StateFlow<UiState>` driving the UI, single `onEvent(...)` entry point, `MaterialTheme.typography` and `MaterialTheme.colorScheme` for styling, no hardcoded colours/fonts/`dp`/`sp` literals, both light and dark `@Preview`s.
- [ ] A unit test exists at `app/src/test/java/com/example/myapplication/ui/counter/CounterViewModelTest.kt` covering: increment increases the count, decrement decreases the count, decrement at zero leaves the count at zero (floor verified).
- [ ] User-facing strings ("Increment", "Decrement", count `contentDescription`) live in `app/src/main/res/values/strings.xml`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green.

---

## Out of scope

- Persistence beyond rotation (DataStore, Room, `SavedStateHandle`).
- A `Reset` button or a third action.
- Negative counts.
- Maximum-value enforcement / `Int` overflow handling.
- Hilt / DI graph.
- Navigation Compose / multi-screen routing.
- Room, DataStore, Retrofit, OkHttp, kotlinx.serialization.
- A `DispatcherProvider`.
- MockK and Turbine in the test toolkit (until a feature genuinely requires them).
- Compose UI / instrumentation tests.
- Animated count transitions, haptics, custom typography.
- Long-press-to-repeat, gesture-based increment.
- Tablet / foldable / multi-window layouts.
- Dark-mode toggle / theme settings (planned in #14, unaffected).
- Bottom navigation bar (planned in #16, unaffected).
- Calendar `MainScreen` (planned in #10/#12, unaffected).
- Renaming the application package away from `com.example.myapplication`.
- Any change to theme colours, typography, dimens beyond the one new button-gap dimen.
