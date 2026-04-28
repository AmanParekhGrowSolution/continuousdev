# [Issue #14] Add a settings screen with dark mode toggle

> **Status:** Draft
> **Issue:** #14
> **Date:** 2026-04-28

---

## Context

The app currently has a single screen (the calendar `MainScreen` from #10/#12). There is no Settings surface, no persisted preferences, no DI graph, no navigation graph, and no `ViewModel` in the codebase. This issue is the first feature with hoisted state, async I/O, and persistence — so it triggers the introduction of most of the conventions described in `CLAUDE.md` that are still listed as "not yet in `libs.versions.toml`".

Concretely, the user wants:
- A gear icon in `MainScreen`'s top bar that navigates to a new `SettingsScreen`.
- A single Material 3 `Switch` on `SettingsScreen` that toggles dark mode.
- The chosen mode is persisted (DataStore Preferences) and applied at launch.
- The theme switches *immediately* when the toggle changes (no app restart).

This is therefore both a feature SPEC (settings screen) and a one-time platform scaffolding SPEC (Hilt, Navigation Compose, DataStore, kotlinx.serialization, MockK, Turbine, `DispatcherProvider`). The scope is big on purpose — splitting "introduce Hilt" / "introduce Navigation Compose" / "introduce DataStore" into separate prep issues was considered and rejected because each is unmotivated without a feature using it; combining them keeps the SPEC honest about what the feature actually requires.

**Coordination with #10 / #12:** `decisions/10-bootstrap-mainscreen.md` (and the duplicate `decisions/12-bootstrap-mainscreen.md`) describes adding `MainScreen` with no top bar. The latest plan-PR merge (`6a19e18`) was for #12; the impl PR has not landed at the time of writing — `MainActivity.kt` still hosts `Greeting("Android")`. This SPEC assumes `MainScreen` from #10/#12 will land *first*. If it does not, the execute phase for #14 must add the minimal `MainScreen` itself rather than implementing on top of `Greeting`. See "Open questions".

---

## Goal

A user can tap a gear icon on `MainScreen`'s top bar to open `SettingsScreen`, flip a Material 3 `Switch` labelled "Dark mode", and watch the app theme change instantly; the choice survives process death because it is stored in DataStore Preferences, and the same value seeds the theme on the next launch before any UI is composed.

---

## Non-goals

- Any settings other than dark mode (notifications, account, language, etc.).
- A tri-state setting (System default / Light / Dark) — issue says a single on/off toggle.
- Animated theme transitions or crossfades — Compose handles recomposition; no extra animation work.
- Theming the gear icon, splash screen, or system bars beyond what `enableEdgeToEdge()` already does.
- Migrating away from the existing `MyApplicationTheme` palette (Purple40/80 etc.) or introducing dynamic-color toggles.
- Tablet / large-screen layouts for `SettingsScreen`.
- Per-feature settings architecture beyond what dark mode requires (no generic "preferences" abstraction).
- Changing the `com.example.myapplication` package name.

---

## Architecture choice

**Chosen approach:** introduce the full convention scaffolding (Hilt + Navigation Compose + DataStore + kotlinx.serialization + DispatcherProvider + MockK + Turbine) and use it to build the smallest correct settings feature.

Layered like this:

1. **Persistence layer** — a single DataStore-Preferences instance owned by an `@Singleton` provider in a Hilt module. Boolean key `dark_mode_enabled`.
2. **Repository layer** — `SettingsRepository` interface in `domain/repository/` with two members:
   - `val darkMode: Flow<Boolean>` — emits `false` when the key is absent (i.e. light mode is the default until the user opts in; see Open questions).
   - `suspend fun setDarkMode(enabled: Boolean)` — writes through `edit { }`.
   The impl (`data/repository/SettingsRepositoryImpl.kt`) takes the `DataStore<Preferences>` via constructor injection.
3. **ViewModel** — `SettingsViewModel` (`@HiltViewModel`) collects `repository.darkMode` and exposes `StateFlow<SettingsUiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), SettingsUiState.Loading)`. It accepts events through a single `onEvent(SettingsEvent)` function backed by a sealed interface (`ToggleDarkMode(enabled: Boolean)`). Writes go through the injected `DispatcherProvider.io`.
4. **UI** — two composables in `ui/settings/SettingsScreen.kt`:
   - `SettingsScreen(viewModel: SettingsViewModel = hiltViewModel(), onNavigateBack: () -> Unit)` — collects state with `collectAsStateWithLifecycle()` and wires events.
   - `SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onNavigateBack: () -> Unit)` — pure stateless overload. Renders a `Scaffold` with a `TopAppBar` (back arrow) and a Material 3 `ListItem` whose `headlineContent` is the "Dark mode" label and whose `trailingContent` is a `Switch`. The whole row is clickable so taps anywhere on the row toggle the switch.
   Two `@Preview`s (light + dark, each with `enabled=true` and `enabled=false` sample states — four previews total, matching CLAUDE.md's "every screen has a preview" rule).
5. **Theme observation at the app root** — the dark mode preference must be applied *before* any composition that reads `MaterialTheme`, including `MainScreen`. We do this with an app-level `ThemeViewModel` (`@HiltViewModel`) that exposes `StateFlow<ThemePreference>` where `ThemePreference` is `sealed interface { data object FollowSystem; data class Override(val darkMode: Boolean) }`. `MainActivity` collects it inside `setContent { }`, falls back to `FollowSystem` while the first emission is pending, and passes the resolved boolean into `MyApplicationTheme(darkTheme = ...)`. The repository emits `null` (a sentinel) until the key is written, but the simpler choice (see Open questions) is: the boolean is `false` by default → light mode → toggle to `true` → dark mode. Same `SettingsRepository` is reused.
6. **Navigation** — Navigation Compose with type-safe routes (kotlinx.serialization `@Serializable` objects). Two destinations: `Main` (start) and `Settings`. Single `NavHost` in `MainActivity`. `MainScreen` receives an `onNavigateToSettings: () -> Unit` lambda; `SettingsScreen` receives `onNavigateBack: () -> Unit`. Back navigation uses the system back stack (no custom plumbing — `popBackStack()`).
7. **`MainScreen` modification** — wrap its existing content in a `Scaffold` with a Material 3 `TopAppBar` whose `actions` slot contains an `IconButton` showing `Icons.Default.Settings` with `contentDescription = stringResource(R.string.settings_top_bar_action_label)`. Tapping it calls the `onNavigateToSettings` lambda. The body of `MainScreen` (the date `Text`) is unchanged. Its existing previews stay; one is updated to also show the top bar, but the date-rendering invariants from #10's SPEC are preserved.
8. **Hilt entry points** — new `@HiltAndroidApp class CalendarApp : Application()` registered as `android:name` in `AndroidManifest.xml`. `MainActivity` annotated `@AndroidEntryPoint`. The Application class lives at `com.example.myapplication.CalendarApp`.
9. **Dispatchers** — `core/dispatchers/DispatcherProvider.kt` defines the interface (`val main: CoroutineDispatcher`, `val io: CoroutineDispatcher`, `val default: CoroutineDispatcher`) and a `DefaultDispatcherProvider` implementation. Hilt provides the default; tests substitute a `TestDispatcher` wrapper.
10. **Tests** — `SettingsViewModelTest` in `src/test/java/...`. Uses `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`), Turbine for `StateFlow` assertions, and a hand-rolled `FakeSettingsRepository` (a Turbine-friendly `MutableSharedFlow`-backed fake — *not* a MockK mock; CLAUDE.md says "Substitute fake repositories — don't mock `Flow` directly when a fake is simpler"). Verifies: (a) initial state reflects the repository's first emission; (b) `ToggleDarkMode(true)` calls `repository.setDarkMode(true)`; (c) the `StateFlow` re-emits when the repository changes.

The choice to put theme observation at the *Activity* level (rather than passing it through every screen) is what makes the "instant theme change" requirement fall out for free: any change to the DataStore key flows to `MyApplicationTheme`, which recomposes its descendants.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Skip Hilt for now; use `viewModel(factory = …)` with manual factories. | CLAUDE.md is explicit: "Inject dependencies via Hilt constructor injection. No service locators, no manual singletons for things that should be injected." A manual factory would be undone in the next feature anyway, doubling the work. |
| Skip Navigation Compose; use `var screen by remember { mutableStateOf<Screen>(Main) }` for a two-screen app. | Saves a dependency but contradicts CLAUDE.md ("Navigation: Jetpack Navigation Compose, type-safe routes"). The next feature needing navigation would have to retrofit it. Two screens is exactly the size at which Navigation Compose stops being overkill. |
| Use `SharedPreferences` directly. | CLAUDE.md: "**No `SharedPreferences` directly.**" |
| Tri-state preference (System / Light / Dark) with three radios. | Better UX, but the issue specifies "single toggle" and "Material 3 Switch". Re-litigate in a follow-up issue if desired; the repository's `Boolean` shape can be widened later without a data migration if we move to a `Settings` Proto. |
| Store the boolean and *also* a `followSystem` boolean. | Same effect as tri-state; same reason to defer. |
| Read DataStore synchronously at activity start (`runBlocking`). | CLAUDE.md: "no `runBlocking` in production code". And it would block the main thread on disk I/O. Compose handles the async hydration (initial composition uses `FollowSystem`, which is indistinguishable from the user's last choice on the first frame). |
| Make `SettingsViewModel` *also* drive the theme (i.e. one ViewModel for both `SettingsScreen` and `MainActivity`). | Couples the activity to a feature ViewModel and conflates "the screen's UI state" with "app-wide theme state". Two ViewModels, both reading the same repository, keeps responsibilities clean. The repository is the single source of truth. |
| Use `androidx.lifecycle.ProcessLifecycleOwner` to seed the theme from a `runBlocking` first emission. | Cute, but `runBlocking` is banned and the user-visible flicker (one frame of system theme before the override applies) is acceptable per "no restart needed" — that requirement is about not requiring a *user-initiated* restart, not about avoiding all transient mismatch on cold start. |
| Inject `DataStore<Preferences>` directly into the ViewModel and skip the repository layer. | CLAUDE.md prescribes a `data/repository/` layer between data sources and ViewModels. The repository is also where we can later add caching, mapping, or merging multiple keys without churning every consumer. |
| Use MockK to mock the repository in tests. | A 20-line fake is clearer than a MockK mock for a repository that exposes a `Flow` and a single `suspend` setter. We add MockK to the version catalog (other features will want it) but use a fake here. |
| Put settings under `ui/main/` since it's reachable only from `MainScreen`. | Violates "One feature = one package under `ui/`". `ui/settings/` is correct. |
| Use `LiveData`. | CLAUDE.md: "Use `LiveData` in new code" is an explicit "never". |

---

## Implementation notes

### New dependencies (`gradle/libs.versions.toml`)

Add the following versions and library/plugin entries (latest stable at time of writing — execute phase pins exact versions):

- `hilt` — `com.google.dagger:hilt-android` and `com.google.dagger:hilt-android-compiler` (KSP), plus the Gradle plugin `com.google.dagger.hilt.android`.
- `androidx-hilt-navigation-compose` — `androidx.hilt:hilt-navigation-compose` for `hiltViewModel()`.
- `ksp` plugin — `com.google.devtools.ksp` (Kotlin 2.2.10-compatible).
- `kotlinx-serialization` — `org.jetbrains.kotlinx:kotlinx-serialization-core` and the `org.jetbrains.kotlin.plugin.serialization` plugin (matched to `kotlin = "2.2.10"`).
- `androidx-navigation-compose` — `androidx.navigation:navigation-compose` (latest type-safe-route-supporting version).
- `androidx-datastore-preferences` — `androidx.datastore:datastore-preferences`.
- `androidx-lifecycle-runtime-compose` — `androidx.lifecycle:lifecycle-runtime-compose` (provides `collectAsStateWithLifecycle`).
- `androidx-lifecycle-viewmodel-compose` — already pulled transitively by `hilt-navigation-compose` but explicit is better.
- `kotlinx-coroutines-test` — `org.jetbrains.kotlinx:kotlinx-coroutines-test` (testImplementation).
- `turbine` — `app.cash.turbine:turbine` (testImplementation).
- `mockk` — `io.mockk:mockk` (testImplementation; not used by this issue's tests but added now per CLAUDE.md scaffold note).

### Plugin wiring (`app/build.gradle.kts`)

- Apply `com.google.devtools.ksp`, `com.google.dagger.hilt.android`, `org.jetbrains.kotlin.plugin.serialization`.
- Add `ksp(libs.hilt.android.compiler)` to the dependencies block.

### New source files

- `app/src/main/java/com/example/myapplication/CalendarApp.kt` — `@HiltAndroidApp class CalendarApp : Application()`.
- `app/src/main/java/com/example/myapplication/core/dispatchers/DispatcherProvider.kt` — interface + `DefaultDispatcherProvider` object.
- `app/src/main/java/com/example/myapplication/data/local/SettingsPreferencesKeys.kt` — `val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")`.
- `app/src/main/java/com/example/myapplication/data/repository/SettingsRepositoryImpl.kt` — `class SettingsRepositoryImpl @Inject constructor(private val dataStore: DataStore<Preferences>, private val dispatchers: DispatcherProvider) : SettingsRepository`.
- `app/src/main/java/com/example/myapplication/domain/repository/SettingsRepository.kt` — interface as described above.
- `app/src/main/java/com/example/myapplication/di/AppModule.kt` — `@Module @InstallIn(SingletonComponent::class)` providing the singleton `DataStore<Preferences>` (built from `preferencesDataStore("settings")` in `applicationContext`), binding the dispatcher and repository implementations.
- `app/src/main/java/com/example/myapplication/ui/navigation/CalendarDestinations.kt` — `@Serializable data object Main` and `@Serializable data object Settings`.
- `app/src/main/java/com/example/myapplication/ui/navigation/CalendarNavGraph.kt` — `@Composable fun CalendarNavGraph(navController: NavHostController, modifier: Modifier = Modifier)` containing the `NavHost` with `composable<Main> { … }` and `composable<Settings> { … }`.
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsUiState.kt` — `data class SettingsUiState(val darkModeEnabled: Boolean = false, val isLoading: Boolean = true)` (or a sealed interface; see Open questions).
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsEvent.kt` — `sealed interface SettingsEvent { data class ToggleDarkMode(val enabled: Boolean) : SettingsEvent }`.
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsViewModel.kt` — described in "Architecture choice". Uses `SavedStateHandle` only if a future event makes it necessary; for a single boolean fed by DataStore, the ViewModel needs no `SavedStateHandle`.
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsScreen.kt` — stateless + wrapper composables with previews.
- `app/src/main/java/com/example/myapplication/ui/theme/ThemeViewModel.kt` — `@HiltViewModel class ThemeViewModel @Inject constructor(repository: SettingsRepository) : ViewModel()` exposing `val darkMode: StateFlow<Boolean?>` (null while loading; `MainActivity` falls back to `isSystemInDarkTheme()` until non-null).

### Modified source files

- `app/src/main/java/com/example/myapplication/MainActivity.kt`:
  - Annotate `@AndroidEntryPoint`.
  - In `setContent { }`, obtain a `ThemeViewModel` via `hiltViewModel()`, collect its `darkMode` flow with `collectAsStateWithLifecycle()`, resolve to a `Boolean` (fall back to `isSystemInDarkTheme()` while null), and pass into `MyApplicationTheme(darkTheme = …)`.
  - Replace the inner `Scaffold { Greeting(...) }` (or `MainScreen()` if #10/#12 has merged) with a single call to `CalendarNavGraph(navController = rememberNavController())`.
  - Delete `Greeting` and `GreetingPreview` if still present.
- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` (assumes #10/#12 has merged — see Open questions):
  - Add an `onNavigateToSettings: () -> Unit` parameter to both stateless and wrapper overloads.
  - Wrap the body in a `Scaffold` with a `TopAppBar` whose `title = stringResource(R.string.app_name)` and whose `actions = { IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_top_bar_action_label)) } }`.
  - Update both previews to pass an `onNavigateToSettings = {}` no-op.
- `app/src/main/java/com/example/myapplication/ui/theme/Theme.kt`:
  - Keep the existing API but document that `darkTheme` is now driven by the resolved preference at the call site. No code change required (the parameter already defaults to `isSystemInDarkTheme()`); `MainActivity` simply passes a non-default value.
- `app/src/main/AndroidManifest.xml`:
  - Add `android:name=".CalendarApp"` to the `<application>` tag. No other changes.
- `app/src/main/res/values/strings.xml`:
  - `<string name="settings_screen_title">Settings</string>`
  - `<string name="settings_dark_mode_label">Dark mode</string>`
  - `<string name="settings_top_bar_action_label">Open settings</string>` (gear `IconButton` content description on `MainScreen`)
  - `<string name="settings_back_action_label">Back</string>` (back arrow `IconButton` content description on `SettingsScreen`)

### New test files

- `app/src/test/java/com/example/myapplication/ui/settings/SettingsViewModelTest.kt`:
  - `@Before` installs a `StandardTestDispatcher` via `Dispatchers.setMain(...)` (with `@After` `Dispatchers.resetMain()`).
  - Uses a `FakeSettingsRepository` that exposes `MutableStateFlow<Boolean>` for `darkMode` and records the last `setDarkMode(...)` call.
  - Test 1: initial `StateFlow` value matches the repository's initial emission (`false`).
  - Test 2: emitting `true` from the repository advances the `StateFlow` to `darkModeEnabled = true` (asserted via Turbine).
  - Test 3: dispatching `SettingsEvent.ToggleDarkMode(true)` calls `repository.setDarkMode(true)` exactly once.
- `app/src/test/java/com/example/myapplication/data/repository/SettingsRepositoryImplTest.kt` — *optional* per acceptance criteria; defer unless it falls out for free. The `DataStore<Preferences>` itself is hard to fake without `androidTest`, and the issue only requires "ViewModel tested with TestDispatcher".

### Files explicitly NOT changed

- `ui/theme/Color.kt`, `ui/theme/Type.kt` — palette unchanged.
- `themes.xml` — Material theme XML unchanged.
- Any existing strings.

### Diff-size sanity

Roughly 12 new files, 4 modified files, ~350–500 lines of Kotlin + a ~30-line test. The bulk is plumbing (Hilt module, Navigation graph, repository); the actual settings feature is ~80 lines.

---

## Open questions

Assumptions made (flagged for human confirmation; default behaviour described under each):

- [ ] **Coordination with #10 / #12.** This SPEC assumes the `MainScreen` impl PR for #10 (or its duplicate #12) lands *before* the impl PR for #14. If both are still open when #14's execute phase runs, the executor should either (a) wait, (b) rebase #14 onto #10's branch, or (c) include the minimal `MainScreen` from #10's SPEC inline in #14. **Default:** wait for #10/#12 to merge first. If a human wants #14 to bundle the bootstrap, say so on the plan PR.
- [ ] **Default value when the DataStore key is absent.** `false` (light mode is the default) vs. `isSystemInDarkTheme()` (follow the system until the user opts in). Issue is silent. **Default chosen here:** the *Settings toggle* shows `false` by default (it's a Boolean), but the *theme* falls back to `isSystemInDarkTheme()` until the user toggles, by exposing the repository as `Flow<Boolean?>` where `null` means "not set". This is asymmetric on purpose: the toggle is a pure on/off control (issue says "single toggle"), but the *visible* theme on first launch should match the device. If reviewers prefer one consistent default (e.g. always start in light mode regardless of system), simplify the repo to `Flow<Boolean>` returning `false` when unset.
- [ ] **Tri-state preference (System / Light / Dark).** Issue specifies a single Switch; tri-state would need a different control. Sticking with Boolean. Flag if you want tri-state; it would change `SettingsRepository`, `SettingsUiState`, and the Switch into a segmented button or three radios.
- [ ] **Hilt + Navigation Compose + DataStore + serialization + KSP introduction in one PR.** Per CLAUDE.md scaffold note these are introduced "when the first feature that needs them is implemented", and this is that feature. The PR will be larger than typical because of the platform scaffolding. Alternative: split into a "infra: introduce DI/nav/DataStore" PR first, then a thin feature PR. **Default:** one PR.
- [ ] **Top bar on `MainScreen`.** #10's SPEC explicitly does *not* add a `TopAppBar` (it says: "future issues that add a `TopAppBar`, FAB, or bottom nav can wire them in one place"). This issue is that future issue, but the change touches `MainScreen.kt`. Confirm this is fine; the alternative is a separate "add MainScreen TopAppBar" issue ahead of #14, which adds friction with no architectural benefit.
- [ ] **`ListItem` vs. custom `Row`.** Material 3 `ListItem` with `headlineContent = Text("Dark mode")` and `trailingContent = Switch(...)` is the idiomatic Material 3 pattern and gives correct touch targets, padding, and accessibility for free. Alternative is a hand-rolled `Row { Text(...); Spacer; Switch(...) }`. **Default:** `ListItem`.
- [ ] **Whole-row clickable.** Should tapping the row label (not just the Switch thumb) toggle the value? **Default:** yes — set `Modifier.clickable { onEvent(ToggleDarkMode(!state.darkModeEnabled)) }` on the `ListItem`. Improves accessibility (bigger hit target than the Switch alone). The `Switch` still works on its own.
- [ ] **Theme `ViewModel` vs. inline collection in `MainActivity`.** A separate `ThemeViewModel` is cleaner but means two ViewModels read the same repository. Alternative: collect the repository directly inside `setContent { }` via an injected entry point. **Default:** dedicated `ThemeViewModel` for testability and to avoid coupling `MainActivity` to the repository.
- [ ] **Dark/light flicker on cold start.** Because the first emission from DataStore is async, the very first frame after `setContent { }` will use `isSystemInDarkTheme()`; if the user previously chose the *opposite* of the system theme, they will see a one-frame mismatch. Acceptable per "no restart needed" (the requirement targets user-visible workflow restarts, not first-frame paints). If this is unacceptable, the fix is to use the splash-screen API and gate `setContent` on a `runCatching { dataStore.data.first() }` inside a `lifecycleScope.launch` — adds complexity. **Default:** accept the flicker.
- [ ] **`DispatcherProvider`.** Introduce now (CLAUDE.md mandates injectable dispatchers and the test uses `TestDispatcher`) vs. defer to when something actually does heavy work off the main thread. **Default:** introduce now; the test substitution is the immediate justification.
- [ ] **Use `MockK` for tests.** CLAUDE.md lists MockK in the convention; this issue's tests use a hand-rolled fake instead. MockK is added to `libs.versions.toml` for future features but not used yet. Confirm.
- [ ] **DataStore name.** `"settings"` for `preferencesDataStore("settings")`. Single namespace for now. If a future feature wants its own DataStore (e.g. `"events"`), splitting is trivial.
- [ ] **`@Singleton` scope for the repository.** Single instance, app-scoped. Standard for repositories backing a singleton DataStore.
- [ ] **Type-safe routes via kotlinx.serialization.** Requires the kotlin-serialization plugin. Alternative: string-keyed routes (`"main"`, `"settings"`). **Default:** type-safe per CLAUDE.md ("type-safe routes (Kotlin Serialization-based)").
- [ ] **`SettingsUiState` shape.** Plain `data class` with `isLoading: Boolean` field, vs. a `sealed interface` with `Loading` / `Loaded(darkMode)`. **Default:** `data class` — only one settings, the ViewModel emits `Loaded` immediately after the first repository emission, and a sealed interface is overkill for a single field. If more settings get added, revisit.
- [ ] **`MainActivity` package-rename.** Same as #10's SPEC: keep `com.example.myapplication`. Treat package rename (e.g. to `com.example.calendar`) as a separate concern.

---

## Acceptance criteria

Copied from the issue, with concrete verifiable conditions:

- [ ] Tapping the gear `IconButton` in `MainScreen`'s top bar navigates to `SettingsScreen` via the Navigation Compose graph.
- [ ] `SettingsScreen` shows a Material 3 `Switch` labelled "Dark mode" inside a Material 3 `ListItem`.
- [ ] Toggling the `Switch` calls `SettingsRepository.setDarkMode(...)`, which writes to DataStore Preferences.
- [ ] The toggle's reflected state comes from `SettingsRepository.darkMode` (Flow), so external writes to DataStore re-render the screen.
- [ ] At app launch, `MyApplicationTheme(darkTheme = …)` is seeded from the persisted preference (with `isSystemInDarkTheme()` as the pre-emission fallback).
- [ ] Toggling dark mode on `SettingsScreen` updates `MainScreen`'s theme without an app restart and without navigating away.
- [ ] Pressing the system back button (or the `TopAppBar`'s back arrow) on `SettingsScreen` returns to `MainScreen`.
- [ ] All user-facing strings (`Settings`, `Dark mode`, gear `contentDescription`, back-arrow `contentDescription`) live in `app/src/main/res/values/strings.xml`.
- [ ] `SettingsViewModelTest` exists, uses `kotlinx-coroutines-test` `StandardTestDispatcher`, asserts initial state, repository-driven state changes, and that `ToggleDarkMode` events propagate to `repository.setDarkMode(...)`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green.

---

## Out of scope

- Settings other than dark mode.
- Tri-state (System / Light / Dark) UX.
- Theme migration or palette redesign.
- Splash-screen-driven elimination of the first-frame theme flicker.
- Animated theme transitions.
- Tablet / foldable / multi-window layouts for Settings.
- Per-feature DataStores or a Proto DataStore.
- Migrating existing `SharedPreferences` (none exist).
- Renaming the `com.example.myapplication` package.
- A generic "preferences" framework for future settings (build it when the second setting arrives, not before).
- Compose UI tests for `SettingsScreen` — the unit test on `SettingsViewModel` covers the contract; the screen is small enough that a UI test would mostly verify the `Switch` calls its `onCheckedChange`. Add when conditional rendering or state transitions are non-trivial (per CLAUDE.md: "Compose UI tests: only for non-trivial UI").
