# CLAUDE.md

This file is read by Claude Code on every run. It defines the stack, architecture, and conventions for this Android project. Follow it strictly. When in doubt, ask in the PR description rather than improvise.

## Stack (non-negotiable)

- **Language:** Kotlin (latest stable). No Java.
- **UI:** Jetpack Compose with Material 3. No XML layouts, no AppCompat.
- **Min SDK:** 26. **Target / Compile SDK:** latest stable.
- **JDK:** 17.
- **Build:** Gradle Kotlin DSL (`build.gradle.kts`). Use the version catalog (`libs.versions.toml`).
- **Architecture:** MVVM with a unidirectional data flow. UI observes `StateFlow` from `ViewModel`. Events go up via lambdas; state comes down as immutable data classes.
- **DI:** Hilt. No service locators, no manual singletons for things that should be injected.
- **Async:** Kotlin Coroutines + Flow. No RxJava. No `Thread`, no `AsyncTask`, no `runBlocking` in production code.
- **Networking:** Retrofit + OkHttp + kotlinx.serialization. Suspend functions, not `Call`.
- **Local storage:** Room for relational, DataStore (Preferences or Proto) for key-value. **No `SharedPreferences` directly.**
- **Image loading:** Coil 3. Not Glide, not Picasso.
- **Navigation:** Jetpack Navigation Compose, type-safe routes (Kotlin Serialization-based).
- **Testing:** JUnit 4, MockK, Turbine, kotlinx-coroutines-test for unit tests. Compose UI test for screen-level tests.

If a feature requires a new dependency, add it to `libs.versions.toml` and justify it in the PR description.

## Project structure

```
app/
  src/main/java/<package>/
    MainActivity.kt              # entry point, sets up theme + nav graph
    di/                          # Hilt modules
    data/
      remote/                    # Retrofit services, DTOs
      local/                     # Room DAOs, entities, DataStore
      repository/                # repository interfaces + impls
    domain/
      model/                     # domain models (NOT DTOs, NOT entities)
      usecase/                   # use cases / interactors
    ui/
      theme/                     # Color.kt, Type.kt, Theme.kt
      common/                    # reusable composables
      <feature>/
        <Feature>Screen.kt       # composables for the screen
        <Feature>ViewModel.kt    # state holder
        <Feature>UiState.kt      # data class for screen state
  src/test/                      # unit tests (jvm)
  src/androidTest/               # instrumentation + Compose UI tests
```

One feature = one package under `ui/`. Don't create cross-feature dependencies; share via `domain/` or `ui/common/`.

## Architecture rules

### Composables

- Composables are **stateless** by default. State is hoisted to the ViewModel.
- A screen-level composable has exactly two stateful versions:
  - `FooScreen(viewModel: FooViewModel = hiltViewModel())` — collects state, wires events.
  - `FooScreen(state: FooUiState, onEvent: (FooEvent) -> Unit)` — pure, easy to preview and test.
- No business logic, no I/O, no `runBlocking`, no direct repository calls inside a composable.
- Every screen-level composable has an `@Preview` (light + dark, with sample data).

### ViewModels

- One `ViewModel` per screen. Expose `StateFlow<FooUiState>` (single source of truth).
- Events come in via a single `onEvent(event: FooEvent)` function backed by a sealed interface.
- Use `viewModelScope`. **Never** `GlobalScope`.
- Inject dependencies via Hilt constructor injection. Don't use `@HiltViewModel` without `@Inject constructor`.
- Use `SavedStateHandle` for state that must survive process death (selected IDs, search queries, etc.).

### Coroutines & Flow

- Repository functions are `suspend` or return `Flow`. They never expose `LiveData`, `Call`, or `RxJava` types.
- Dispatchers are injected, not hardcoded. Define a `DispatcherProvider` interface and inject it; tests substitute `TestDispatcher`.
- Long-running collection: `viewModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`.
- In composables, collect with `collectAsStateWithLifecycle()`, not `collectAsState()`.

### Resources & strings

- Every user-facing string lives in `res/values/strings.xml`. Format args use positional placeholders (`%1$s`, `%2$d`).
- No hardcoded `dp`, `sp`, or color literals in composables. Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and dimension resources or theme tokens.
- Icons go through `Icons.*` (Material) or are vector drawables in `res/drawable/`. No raster assets unless unavoidable.

### Accessibility

- Every clickable/icon needs a `contentDescription` or explicit `Modifier.semantics { contentDescription = ... }`.
- Decorative icons: `contentDescription = null`.
- Touch targets: minimum 48dp. Use `Modifier.minimumInteractiveComponentSize()` if in doubt.
- Test with TalkBack mentally: can the screen be operated without sight?

### Theming

- Material 3 dynamic color enabled on Android 12+, fall back to a defined palette below that.
- Both light and dark themes must work. Every preview includes both.
- Edge-to-edge: enable in `MainActivity` with `enableEdgeToEdge()`. Handle insets explicitly per screen.

## Testing rules

- ViewModels: unit tested with `kotlinx-coroutines-test`. Use `Turbine` for `Flow` assertions. Substitute fake repositories — don't mock `Flow` directly when a fake is simpler.
- Use cases: unit tested in isolation.
- Repositories: tested with fakes for the data sources; don't hit the network or a real Room DB in unit tests.
- Compose UI tests: only for non-trivial UI (state transitions, conditional rendering, complex gestures). Skip for cosmetic-only changes.
- Tests are deterministic — no `Thread.sleep`, no real time, no real network, no real database.
- A passing test must actually exercise the behavior. No `assertTrue(true)` placeholders.

## Things to never do

- ❌ Block the main thread. Period.
- ❌ Store an `Activity` or `View` reference outside its lifecycle.
- ❌ Use `GlobalScope` or `Dispatchers.Unconfined` in production.
- ❌ Catch `Throwable` or swallow exceptions silently.
- ❌ Use `!!` on values that could plausibly be null. Use `?:`, `requireNotNull(...)`, or refactor.
- ❌ Commit secrets, API keys, or signing keys. Use `local.properties` (gitignored) and read via `BuildConfig`.
- ❌ Add a third dependency for a problem the standard library or Compose foundation already solves.
- ❌ Disable lint rules to make the build pass. Fix the underlying issue.
- ❌ Mark tests `@Ignore` to make CI green. Either fix or delete the test.
- ❌ Use `LiveData` in new code. Use `StateFlow`.

## Build & verification

Before opening any PR, the following must pass locally:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

If any of those fails, the PR is not ready. The CI workflow runs the same commands, so this saves a round trip.

## Commits & PRs

- Conventional commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- One logical change per commit when reasonable.
- PR body must include:
  - `Closes #<issue>`
  - Screens/components added or changed
  - Assumptions made
  - Anything intentionally skipped (with reason)
- Do not merge your own PRs. A human reviews and merges.

## When the issue is ambiguous

If the issue doesn't specify something concrete (e.g. "add a calendar app" without saying month/week/day view, with or without events, local-only or synced), make a reasonable choice and **list it explicitly under "Assumptions" in the PR body**. Do not silently pick.

If a choice is large enough to lock in architecture (e.g. local-only vs. backend-required, auth vs. no-auth), open a small `SPEC.md` PR first proposing the approach, and wait for it to be merged before implementing.
