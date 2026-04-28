# [Issue #16] Add a bottom navigation bar with Home and Settings tabs

> **Status:** Draft
> **Issue:** #16
> **Date:** 2026-04-28

---

## Context

The app currently has a `Greeting("Android")` placeholder in `MainActivity`. Two earlier features are *planned* but not yet merged at impl-time: `MainScreen` (issues #10/#12) and `SettingsScreen` plus the platform scaffolding — Hilt, Navigation Compose with type-safe routes, DataStore, kotlinx.serialization, `DispatcherProvider` (issue #14). Both plan PRs are merged; both impl PRs are still pending.

#14's plan introduces a Navigation Compose graph with two destinations (`Main`, `Settings`) and reaches `SettingsScreen` from `MainScreen` via a gear `IconButton` in `MainScreen`'s `TopAppBar`, with a back arrow on `SettingsScreen` to return. That's the right shape for a *modal* settings detail screen reached from a parent.

This issue (#16) reframes the relationship: Settings becomes a *peer* top-level destination reached via a bottom navigation tab. The gear-icon entry and the back-arrow exit in #14's plan are no longer the right pattern — peer destinations don't have parent/child back-stack semantics. So #16 builds on top of #14's nav graph and DI scaffolding, but **supersedes** #14's `MainScreen` gear icon and `SettingsScreen` back arrow. See "Open questions" for the coordination plan.

The issue also says explicitly: *"No back-stack management needed — each tab replaces the current destination."* That's a design constraint, not a side note: tab switching pops the back stack, so pressing system back from any tab exits the app.

---

## Goal

When the app launches, a Material 3 `NavigationBar` is pinned to the bottom of every screen showing two tabs (Home, Settings). Tapping a tab makes its destination the only entry on the back stack and visually highlights its `NavigationBarItem`; the same effect is achieved by programmatically navigating to the corresponding type-safe route, so `navController.navigate(Settings)` from anywhere in the app lands on the Settings tab with the bar reflecting the selection.

---

## Non-goals

- Adding a third tab or any non-tab destination (no detail screens, no modal flows).
- Bottom-bar visibility logic (showing/hiding the bar on selected destinations) — both destinations are top-level so the bar is always visible.
- Per-tab nested back stacks (`saveState` / `restoreState` on `popUpTo`). The issue explicitly waives back-stack management.
- External URI deep links (`navDeepLink { uriPattern = ... }`). "Deep-linking via route" is interpreted as in-app type-safe `navigate(Settings)` — see Open questions.
- Animated tab transitions, badges, custom indicator shapes.
- A `NavigationRail` for large-screen / landscape layouts (would belong in a follow-up adaptive-layout issue).
- Persisting tab selection across process death (the route on the back stack already does this via `SavedStateHandle`).
- Re-litigating #10/#12's `MainScreen` content or #14's dark-mode persistence — both stay as planned.
- Changing the package (`com.example.myapplication`) or theme palette.
- Adding new screens to host the tabs (the existing `MainScreen` and `SettingsScreen` are the destinations).

---

## Architecture choice

**Chosen approach:** A single root composable `CalendarApp` owns a `Scaffold` whose `bottomBar` slot renders a stateless `CalendarBottomBar`. The `Scaffold`'s content slot is the `NavHost` from #14's plan, with one `composable<T> { … }` entry per top-level destination. Tab selection observes `navController.currentBackStackEntryAsState()` and compares the current `NavDestination` against each top-level destination's type-safe route via `NavDestination.hasRoute<T>()`. Tab navigation uses `popUpTo(graph.findStartDestination().id) { inclusive = true }` plus `launchSingleTop = true`, producing a single-entry back stack on every switch.

Layered like this:

1. **Top-level destination model** — `ui/navigation/CalendarTopLevelDestination.kt` defines a sealed interface listing the bottom-bar tabs:
   ```kotlin
   sealed interface CalendarTopLevelDestination {
       val route: Any                 // serializable route object passed to navController.navigate
       val routeClass: KClass<*>      // used with NavDestination.hasRoute(routeClass)
       @get:StringRes val labelRes: Int
       val icon: ImageVector

       data object Home : CalendarTopLevelDestination { … route = Main; icon = Icons.Default.Home; … }
       data object Settings : CalendarTopLevelDestination { … route = SettingsRoute; icon = Icons.Default.Settings; … }
   }
   ```
   `Main` and `SettingsRoute` are the `@Serializable data object` route markers from #14's `ui/navigation/CalendarDestinations.kt` (see "Implementation notes" for the rename).

2. **Bottom bar** — `ui/common/CalendarBottomBar.kt` exposes a single stateless composable:
   ```kotlin
   @Composable
   fun CalendarBottomBar(
       destinations: List<CalendarTopLevelDestination>,
       currentDestination: NavDestination?,
       onNavigateToTopLevelDestination: (CalendarTopLevelDestination) -> Unit,
       modifier: Modifier = Modifier,
   )
   ```
   It renders a Material 3 `NavigationBar { destinations.forEach { NavigationBarItem(...) } }`. Each item:
   - `selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true` (the `hierarchy` walk handles nested graphs cleanly even though we don't have any today).
   - `onClick = { onNavigateToTopLevelDestination(destination) }`.
   - `icon = { Icon(destination.icon, contentDescription = null) }` — decorative, the visible label provides the semantic.
   - `label = { Text(stringResource(destination.labelRes)) }` — always visible (never set `alwaysShowLabel = false`).
   The composable is pure — no `NavController`, no business logic — so it's trivial to preview with both selections.

3. **App root** — `ui/CalendarApp.kt`:
   ```kotlin
   @Composable
   fun CalendarApp(navController: NavHostController = rememberNavController()) {
       val backStackEntry by navController.currentBackStackEntryAsState()
       val currentDestination = backStackEntry?.destination
       Scaffold(
           bottomBar = {
               CalendarBottomBar(
                   destinations = CalendarTopLevelDestination.entries,
                   currentDestination = currentDestination,
                   onNavigateToTopLevelDestination = { destination ->
                       navController.navigate(destination.route) {
                           popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                           launchSingleTop = true
                       }
                   },
               )
           },
       ) { innerPadding ->
           CalendarNavGraph(
               navController = navController,
               modifier = Modifier.padding(innerPadding),
           )
       }
   }
   ```
   Each individual screen still manages its own `TopAppBar` inside its own `Scaffold` (the standard nested-Scaffold pattern Material samples use). That keeps screen chrome localised in the screen.

4. **Tab-switch back-stack semantics** — `popUpTo(startDestinationId) { inclusive = true }` removes every entry, including the start destination, before navigating. With `launchSingleTop = true`, the result is exactly one entry on the back stack at all times. System back from either tab exits the app, satisfying the issue's "no back-stack management needed" clause.

5. **Integration with #14's nav graph** — `ui/navigation/CalendarNavGraph.kt` (planned in #14) becomes responsible only for the `NavHost` itself, not for the surrounding chrome (no internal `Scaffold`). Its signature is `fun CalendarNavGraph(navController: NavHostController, modifier: Modifier = Modifier)` — the surrounding `Scaffold` lives in `CalendarApp`. Its body is unchanged from #14:
   ```kotlin
   NavHost(navController, startDestination = Main, modifier) {
       composable<Main> { MainScreen(...) }
       composable<SettingsRoute> { SettingsScreen(...) }
   }
   ```

6. **Removal of #14's parent/child entry/exit** — once Settings is a peer tab, the gear `IconButton` in `MainScreen`'s `TopAppBar` and the back arrow in `SettingsScreen`'s `TopAppBar` are wrong (they imply a parent/child relationship that no longer holds). Both are removed:
   - `MainScreen`'s `TopAppBar.actions` slot loses the gear icon. The `onNavigateToSettings` parameter is removed. The screen still has a `TopAppBar` with the title.
   - `SettingsScreen`'s `TopAppBar.navigationIcon` slot loses the back arrow. The `onNavigateBack` parameter is removed.
   - The corresponding strings (`settings_top_bar_action_label`, `settings_back_action_label`) become unused and are deleted from `strings.xml`.

7. **`MainActivity` change** — replaces the call to `CalendarNavGraph(...)` (planned by #14) with a call to `CalendarApp()`. The `ThemeViewModel` collection (also from #14) stays exactly where it is, *outside* `CalendarApp` and *inside* `setContent { MyApplicationTheme(darkTheme = ...) { CalendarApp() } }`.

8. **Selection comparison via `hasRoute`** — `androidx.navigation:navigation-compose` exposes `NavDestination.hasRoute(KClass<*>)` for type-safe routes. We use the `KClass` form (`hasRoute(destination.routeClass)`) rather than the reified `hasRoute<T>()` because the destinations are iterated dynamically over the sealed interface's `entries`. The `currentDestination?.hierarchy?.any { ... }` walk is the standard now-recommends-it pattern; with no nested graphs it's equivalent to a direct compare, but costs nothing and is correct under future graph nesting.

9. **Deep-link interpretation** — "Deep-linking into Settings tab works via route" is read as: any `navController.navigate(SettingsRoute)` call from anywhere in the app navigates to the Settings tab and the bottom bar reflects that selection. Because the bottom bar's selection is derived from `currentBackStackEntryAsState()`, this falls out automatically. External URI deep links (declared via `navDeepLink { uriPattern = ... }` on the `composable<T>` block) are explicitly out of scope — the issue does not specify a URI scheme. See Open questions.

10. **Accessibility** — Tab labels are always visible, so the icon's `contentDescription = null` is correct (the `Text` provides the semantic). `NavigationBarItem` itself supplies the `Selected` semantics and the 48dp+ touch target. TalkBack reads "Home, tab, 1 of 2, selected" / "Settings, tab, 2 of 2" out of the box.

11. **Theming** — `NavigationBar` uses `MaterialTheme.colorScheme.surface` and the active item's indicator pulls from `secondaryContainer`. Inactive tints come from `onSurfaceVariant`; selected from `onSecondaryContainer`. No custom colour overrides — Material 3 defaults satisfy "active tab is highlighted; inactive tabs show unselected tint".

12. **Tests** — There is no new ViewModel, repository, or use case in this issue; nothing CLAUDE.md's testing rules force us to cover. The bottom bar's logic is in the navigation closure and is best validated by a Compose UI test (tap tab, assert destination changed, assert selection state). Per CLAUDE.md ("Compose UI tests: only for non-trivial UI"), this is borderline non-trivial and worth one test class. **Default:** add `app/src/androidTest/.../CalendarBottomBarTest.kt` with two tests — (a) tapping the Settings tab navigates to `SettingsScreen` content; (b) the selected `NavigationBarItem` matches the current destination after navigation. See Open questions if reviewers prefer to skip.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Keep the gear `IconButton` in `MainScreen`'s `TopAppBar` and add the bottom bar in addition. Two routes to Settings. | Two visible affordances doing the same thing is a UX smell; the gear was a child-screen entry, the tab is a peer-screen entry, and the back-arrow on `SettingsScreen` becomes nonsensical when Settings is a peer. Cleaner to commit to the peer model: drop the gear and the back arrow. |
| Put the `Scaffold(bottomBar = ...)` inside `CalendarNavGraph` so the nav graph owns the chrome. | The `Scaffold` is screen-shell chrome, not graph-shape concern. Putting it inside `CalendarNavGraph` couples graph composition to chrome composition and makes it harder to test the graph in isolation later. |
| Put the `Scaffold` (with bottom bar) inside *each* screen (`MainScreen`, `SettingsScreen`). | Duplicates the bottom-bar wiring. Tab-switch logic would need to be re-supplied at every destination. The issue says "persistent bottom navigation bar"; one root Scaffold matches that wording. |
| Drop each screen's own `Scaffold/TopAppBar` and use only the root `Scaffold` with a top-level `topBar` slot whose content is route-dependent. | Forces the root composable to know about every screen's title. Standard Material samples use *nested* Scaffolds — outer for bottom bar, inner per-screen for top bar — for exactly this reason. Less coupling. |
| `popUpTo(start)` with `inclusive = false` (keep start in stack). Switching from Settings to Home pops once; switching from Home to Settings adds one. | Result: stack can grow to size 2; pressing back from Settings returns to Home. The issue explicitly says "each tab replaces the current destination" — that wording is incompatible with a 2-deep stack. |
| Per-tab nested back stacks via `saveState = true` / `restoreState = true`. | Standard for apps where each tab has its own multi-screen flow (e.g. a Browse tab with a list+detail). Both tabs here are single screens, so save/restore state is dead weight. The issue waives back-stack management explicitly. |
| Use an `enum class CalendarTopLevelDestination(...)` instead of a sealed interface with `data object` variants. | Enums can't have a typed `route: Any` whose runtime type drives `navigate(...)` to the correct serializable route. A sealed interface with `data object` per tab pairs the tab metadata with the matching `@Serializable` route object cleanly. Both work; sealed interface is closer to idiomatic Compose-Nav usage with type-safe routes. |
| Custom `NavigationBar` with hand-rolled `Row { … }` of clickable boxes. | Reinvents Material 3 a11y, ripple, indicator, and motion. CLAUDE.md: "Don't add a third dependency for a problem the standard library or Compose foundation already solves." `NavigationBar` + `NavigationBarItem` *is* the foundation solution. |
| Render the `NavigationBar` with `alwaysShowLabel = false` (only label the selected tab). | Material 3 default is `true` and the issue's bullet "Tab labels in strings.xml" implies the labels are visible. Hiding inactive labels reduces a11y signal too. |
| Separate `NavigationBarItem` content descriptions for the icon (`stringResource(R.string.bottom_nav_home_content_description)` etc.) in addition to the visible label. | Redundant — `NavigationBarItem` derives semantics from the visible label; an extra `contentDescription` on the icon would have TalkBack announce the label twice. Set the icon's `contentDescription = null`. |
| Reuse `R.string.settings_screen_title` (from #14) for the Settings tab label. | Tying the tab label to the screen-header title looks cute but they're conceptually separate strings with separate translation contexts. A future translator might shorten the tab label ("Set." in space-constrained locales) without wanting to change the on-screen header. Keep them as separate keys. |
| External URI deep links (`navDeepLink { uriPattern = "https://example.com/settings" }`). | The issue does not specify a URI scheme or domain. Adding one without a stakeholder ask is speculative. If a real deep-link target is desired, file a follow-up issue. |
| Add a `BottomBarViewModel` to centralise selection state. | Selection is fully derived from the `NavController`'s back stack — `currentBackStackEntryAsState()` is single source of truth. A ViewModel would only duplicate that state and add a sync bug surface. |
| Hide the bottom bar via per-destination flags (`showBottomBar: Boolean`). | Both destinations are top-level so the bar is always visible. When a future detail screen lands, add the flag on the destination model then. Speculative now. |
| Use `NavigationRail` for landscape / large screens. | Adaptive layout is not in scope — issue says "bottom navigation bar". Revisit in a follow-up adaptive-UI issue. |
| Add a Compose UI test that mocks the `NavController`. | The `NavController` API is concrete — fakes are awkward. The androidTest test instantiates `rememberNavController()` for real and asserts on rendered content; that's the simpler path. |

---

## Implementation notes

### Coordination & ordering

This SPEC depends on three earlier impl PRs landing first (in any order, but all before #16's execute phase):

1. **#10 or #12** — `MainScreen` exists at `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt`. (#12 is a duplicate of #10; only one impl will land.)
2. **#14** — Hilt scaffold (`CalendarApp : Application`, `@AndroidEntryPoint MainActivity`), Navigation Compose graph (`ui/navigation/CalendarDestinations.kt` + `ui/navigation/CalendarNavGraph.kt`), `SettingsScreen` (`ui/settings/SettingsScreen.kt`), `SettingsViewModel`, `SettingsRepository`, DataStore, `ThemeViewModel`.

If any of those is still open when #16 enters execute, the executor must **stop and post a comment** on issue #16 rather than re-implement those features inline. The risk of duplicate work is too high. Default ordering preference: #10/#12 → #14 → #16. See Open questions.

### Naming caveat — `CalendarApp`

#14's plan introduces an `@HiltAndroidApp class CalendarApp : Application()` Hilt entry point at `com.example.myapplication.CalendarApp`. This SPEC's root composable is also called `CalendarApp` (in `ui/CalendarApp.kt`). Same simple name, different fully-qualified packages — no Kotlin compilation collision, but a reader could reasonably be confused. Two options:

- **Option A (default):** keep both names; rely on package disambiguation. The composable is the only thing imported by `MainActivity`'s `setContent { }` and the `Application` is referenced only by `AndroidManifest.xml`. Confusion risk is low.
- **Option B:** rename the composable to `CalendarRoot` or `CalendarShell` to remove the overlap.

If #14's `Application` class is renamed in its execute phase (e.g. to `CalendarApplication`), this caveat goes away.

### Naming caveat — destination route classes

#14's plan introduces `@Serializable data object Settings` as the type-safe route for the settings destination. This SPEC introduces `CalendarTopLevelDestination.Settings` as the bottom-bar tab descriptor. Both are called `Settings` and both live near `ui/navigation/`. To avoid an import collision, **rename the route in #14's `CalendarDestinations.kt`** from `Settings` to `SettingsRoute` (and `Main` to `MainRoute` for symmetry). This rename happens in #16's diff because #14 has not landed yet — execute phase should rename the route declarations and every callsite at the same time. If #14's impl PR has already merged with the original names, do the rename inside #16's impl PR instead. See Open questions.

### New source files

- `app/src/main/java/com/example/myapplication/ui/CalendarApp.kt` — the root `Scaffold(bottomBar = …)` + `CalendarNavGraph` composition described above.
- `app/src/main/java/com/example/myapplication/ui/navigation/CalendarTopLevelDestination.kt` — sealed interface with `Home` and `Settings` `data object`s, each carrying `route: Any`, `routeClass: KClass<*>`, `labelRes: Int`, `icon: ImageVector`. Provides a `companion object { val entries: List<CalendarTopLevelDestination> = listOf(Home, Settings) }` for iteration.
- `app/src/main/java/com/example/myapplication/ui/common/CalendarBottomBar.kt` — stateless `CalendarBottomBar` composable plus a `@Preview` (light + dark, with each tab selected once).

### Modified source files

- `app/src/main/java/com/example/myapplication/MainActivity.kt` — replace the single call to `CalendarNavGraph(rememberNavController())` (planned in #14) with a single call to `CalendarApp()`. Theme observation via `ThemeViewModel` (from #14) is unchanged. The `MyApplicationTheme(darkTheme = …) { CalendarApp() }` shape stays.
- `app/src/main/java/com/example/myapplication/ui/navigation/CalendarDestinations.kt` (planned in #14) — rename `data object Main` → `MainRoute` and `data object Settings` → `SettingsRoute` to avoid collision with `CalendarTopLevelDestination.Home/Settings`. (Skip if #14's impl PR has merged with these names already; in that case do the rename inside #16's impl PR.)
- `app/src/main/java/com/example/myapplication/ui/navigation/CalendarNavGraph.kt` (planned in #14) — drop any internal `Scaffold` (#14's plan does not specify one, but any chrome introduced during #14's execute should be moved out). Body is just the `NavHost` with `composable<MainRoute> { … }` and `composable<SettingsRoute> { … }`.
- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` (planned in #14) — remove the `onNavigateToSettings: () -> Unit` parameter and the gear `IconButton` in the `TopAppBar.actions` slot. The screen still has a `TopAppBar` whose `title` is `stringResource(R.string.app_name)` (or whatever #14 settled on). Update both previews to drop the now-removed parameter.
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsScreen.kt` (planned in #14) — remove the `onNavigateBack: () -> Unit` parameter and the back-arrow `IconButton` in the `TopAppBar.navigationIcon` slot. The screen still has a `TopAppBar` whose `title` is `stringResource(R.string.settings_screen_title)`. Update both previews accordingly.
- `app/src/main/res/values/strings.xml`:
  - **Add** `<string name="bottom_nav_home_label">Home</string>`
  - **Add** `<string name="bottom_nav_settings_label">Settings</string>`
  - **Remove** `<string name="settings_top_bar_action_label">Open settings</string>` (no longer used)
  - **Remove** `<string name="settings_back_action_label">Back</string>` (no longer used)

### New test files

- `app/src/androidTest/java/com/example/myapplication/ui/CalendarBottomBarTest.kt` — Compose UI test using `createAndroidComposeRule<MainActivity>()` (or `createComposeRule()` plus a hand-built `CalendarApp` host). Two tests:
  1. `tappingSettingsTabNavigatesToSettingsScreen` — start app, assert "Dark mode" is *not* on screen, click `onNodeWithStringResource(R.string.bottom_nav_settings_label)`, assert "Dark mode" *is* on screen.
  2. `selectedTabReflectsCurrentDestination` — start app, assert Home tab has `isSelected()` semantics, click Settings tab, assert Settings tab has `isSelected()` and Home does not.
  The tests do not need Hilt overrides — they exercise the real `CalendarApp` end-to-end. If Hilt setup proves heavy, drop to a `createComposeRule()` test that wires a fake `SettingsViewModel`/`ThemeViewModel`. See Open questions.

No new unit tests — there's no new ViewModel.

### Files explicitly NOT changed

- `gradle/libs.versions.toml` — no new dependencies. `androidx.compose.material3:material3` (already present) supplies `NavigationBar` and `NavigationBarItem`. `androidx.navigation:navigation-compose` (added by #14) supplies `currentBackStackEntryAsState`, `NavDestination.hasRoute`, `NavDestination.hierarchy`. `androidx.compose.material.icons.Default.Home` and `Icons.Default.Settings` ship with the Material icons package already used by Compose previews.
- `app/build.gradle.kts` — no plugin or dep changes.
- `AndroidManifest.xml` — no changes (#14 already wires `android:name=".CalendarApp"` and `@AndroidEntryPoint`).
- `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt`, `themes.xml` — palette unchanged. `NavigationBar` colours come from the Material 3 theme tokens.
- `SettingsRepository`, `SettingsViewModel`, `ThemeViewModel`, DataStore plumbing — unchanged from #14's plan.

### Diff-size sanity

Roughly 3 new files (`CalendarApp.kt`, `CalendarTopLevelDestination.kt`, `CalendarBottomBar.kt`) plus 1 androidTest, ~150 lines of Kotlin. Modifications to 3 files (`MainActivity.kt`, `MainScreen.kt`, `SettingsScreen.kt`) totalling ~30 lines deleted + ~10 added. Plus 2 strings added and 2 removed.

---

## Open questions

Assumptions made (flagged for human confirmation; default behaviour described under each):

- [ ] **Coordination with #10/#12 and #14.** This SPEC presumes both `MainScreen` (#10/#12) and the entire #14 stack (Hilt + Navigation Compose + DataStore + `SettingsScreen`) have landed before #16's execute phase runs. If any of them is still pending, the executor must stop. **Default:** wait for the prerequisites; do not bundle them inline. If the human reviewer wants #16 to bundle, say so on this plan PR.
- [ ] **Removing #14's gear icon and back arrow.** This is the load-bearing UX call: peer-tab navigation is incompatible with the parent/child gear+back affordances #14's plan added. **Default:** drop the gear `IconButton` from `MainScreen` and the back-arrow `IconButton` from `SettingsScreen`; delete their two strings. Alternative: keep both (two ways to reach Settings) — uglier but lower-risk if reviewers want #14's UX to ship as planned and #16 only adds the bar on top. Reviewer call.
- [ ] **Tab back-stack semantics.** `popUpTo(start) { inclusive = true }` + `launchSingleTop = true` produces a single-entry stack on every switch (system back exits app). Aligns with the issue's "no back-stack management needed; each tab replaces the current destination". Alternative interpretations: (a) keep start in stack so back from Settings returns Home; (b) per-tab `saveState`/`restoreState`. Both rejected above; flag if reviewer disagrees.
- [ ] **Deep-link semantics.** Read as "in-app `navController.navigate(SettingsRoute)` lands on Settings tab and the bar reflects the selection". External URI deep links (`navDeepLink { uriPattern = ... }`) are *not* in scope — no URI scheme is specified. If reviewers want external deep linking, define the URI scheme on this plan PR and the impl will declare `deepLinks = listOf(navDeepLink { ... })` on each `composable<T>` block.
- [ ] **Route class rename.** `CalendarDestinations.kt` (planned in #14) introduces `data object Main` and `data object Settings`. To avoid a same-file collision with `CalendarTopLevelDestination.Home`/`Settings` (and to make the route-vs-tab distinction self-documenting), rename them to `MainRoute` / `SettingsRoute`. Done as part of #16's diff because #14 has not landed yet at plan time. **Default:** do the rename. If #14's impl has merged with the original names by the time #16 executes, the rename moves into #16's impl PR.
- [ ] **`CalendarApp` name overlap.** #14's Hilt entry point is `class CalendarApp : Application()`. This SPEC's root composable is also called `CalendarApp` (different package). Acceptable per Kotlin disambiguation, but readers may stumble. Alternative: rename the composable to `CalendarRoot` or `CalendarShell`. **Default:** keep `CalendarApp` for the composable. Reviewer call.
- [ ] **Sealed interface vs. enum** for `CalendarTopLevelDestination`. **Default:** sealed interface with `data object` variants — pairs the tab metadata with the matching `@Serializable` route object cleanly.
- [ ] **Compose UI test in androidTest.** The issue does not explicitly require tests for #16 (no new ViewModel; per CLAUDE.md, Compose UI tests are reserved for non-trivial UI). The bottom-bar selection logic is on the borderline of "non-trivial" (state derived from `NavController`, two-way binding via clicks). **Default:** ship one Compose UI test class (`CalendarBottomBarTest`) covering tab switch + selection state. Skip if reviewers want to defer until the test suite has more infrastructure.
- [ ] **Hilt for the UI test.** If `CalendarApp` is ultimately wired with `hiltViewModel()` (`SettingsViewModel`, `ThemeViewModel`) via real Hilt, the UI test needs `@HiltAndroidTest` + a `HiltAndroidRule` + `HiltTestApplication`. If that's heavy, fall back to a `createComposeRule()` test that drives a hand-built `CalendarApp` with stub state. **Default:** prefer the integrated path if #14's Hilt scaffolding lands cleanly; fall back to the stub if it doesn't.
- [ ] **`NavigationBar` always-visible labels.** `alwaysShowLabel = true` (Material 3 default). Both inactive and active items show their label. Alternative `alwaysShowLabel = false` saves space but reduces a11y. **Default:** keep the Material default (true).
- [ ] **Reusing `R.string.settings_screen_title` for the tab label.** Conceptually different strings (header vs. tab); kept as two distinct keys (`settings_screen_title`, `bottom_nav_settings_label`). Both currently equal "Settings" in en. Reviewer call if dedup is preferred.
- [ ] **Adaptive layout (NavigationRail).** Out of scope — issue explicitly says "bottom navigation bar". Revisit in a follow-up if/when tablet support is requested.
- [ ] **Bottom bar visibility on future detail screens.** Today both destinations are top-level so the bar is always visible. When a non-top-level destination is added later, we'll add a `showBottomBar(currentDestination)` predicate then. Speculative now; not added.
- [ ] **`Icons.Default.Home` vs. `Icons.Outlined.Home`.** Issue says `Icons.Default.Home` and `Icons.Default.Settings`. Material 3 design guidance often pairs filled (selected) and outlined (unselected) icons in a `NavigationBar`. Issue is explicit, so **default:** use `Default` for both states. If reviewers want filled/outlined pairs, add a second `ImageVector` field on `CalendarTopLevelDestination` (`unselectedIcon`).

---

## Acceptance criteria

Copied from the issue, with concrete verifiable conditions:

- [ ] A Material 3 `NavigationBar` is rendered at the bottom of the app on every screen, with two `NavigationBarItem`s (Home, Settings).
- [ ] Tapping the Home tab navigates the app to `MainScreen` (the `MainRoute` destination).
- [ ] Tapping the Settings tab navigates the app to `SettingsScreen` (the `SettingsRoute` destination).
- [ ] The currently active destination's tab is visually highlighted via Material 3's selected state; the other tab uses Material 3's unselected tint.
- [ ] Home tab uses `Icons.Default.Home`; Settings tab uses `Icons.Default.Settings`.
- [ ] Tab labels are loaded from `app/src/main/res/values/strings.xml` (`R.string.bottom_nav_home_label`, `R.string.bottom_nav_settings_label`).
- [ ] Routes are type-safe (kotlinx.serialization-based, declared via `@Serializable` route objects).
- [ ] Calling `navController.navigate(SettingsRoute)` from anywhere in the app lands on the Settings tab and the bottom bar reflects that selection.
- [ ] Tab switching uses `popUpTo(graph.findStartDestination().id) { inclusive = true }` + `launchSingleTop = true`, producing a single-entry back stack — the system back button from either tab exits the app.
- [ ] MVVM architecture is unchanged: `MainScreen` and `SettingsScreen` retain their respective ViewModels (`SettingsViewModel`, `ThemeViewModel`) from #14; no new ViewModels are added by #16.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green.

---

## Out of scope

- Any third tab or non-tab destination.
- Adaptive layouts (`NavigationRail` on landscape / large screens / foldables).
- Per-tab nested back stacks (`saveState` / `restoreState`).
- External URI deep links (`navDeepLink { uriPattern = ... }`).
- Animated tab transitions, badges, custom indicator shapes.
- Filled-vs-outlined icon pairs for selected/unselected states.
- Bottom-bar visibility logic for future non-top-level destinations.
- Rebuilding any of #14's settings/theme persistence stack.
- New `libs.versions.toml` dependencies.
- Renaming the application package away from `com.example.myapplication`.
- Theme palette changes.
