# [Issue #20] Add TopAppBar to MainScreen with app title

> **Status:** Draft
> **Issue:** #20
> **Date:** 2026-04-30

---

## Context

Issue #20 asks for a `CenterAlignedTopAppBar` on `MainScreen` showing the title "Calendar". The issue summary refers to "the `MainScreen` composable added in issue #1" — there is no issue #1 in `decisions/`; the bootstrap `MainScreen` is planned in `decisions/10-bootstrap-mainscreen.md` (and its byte-identical duplicate `decisions/12-bootstrap-mainscreen.md`). I am treating "issue #1" in the body of #20 as a reference to those bootstrap plans.

**Prerequisite state at plan time (2026-04-30):**

- `MainActivity.kt` still hosts the original Android Studio `Greeting("Android")` placeholder.
- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` **does not exist yet** — #10 and #12 have plan PRs merged but no impl PR landed.
- `decisions/14-settings-dark-mode-toggle.md` (#14) plans a `TopAppBar` on `MainScreen` with a gear `IconButton` for navigation to `SettingsScreen`. Title source in #14: `R.string.app_name`. Plain `TopAppBar`, not `CenterAlignedTopAppBar`. No impl landed.
- `decisions/16-bottom-navigation-bar.md` (#16) supersedes #14's gear icon — Settings becomes a peer tab. #16 explicitly keeps a `TopAppBar` on `MainScreen` with title `R.string.app_name` and drops the gear icon. Plain `TopAppBar`, not `CenterAlignedTopAppBar`. No impl landed.
- `decisions/18-bootstrap-counter-screen.md` (#18) replaces `Greeting` with a `CounterScreen` instead of a `MainScreen` and abandons the calendar trajectory. No impl landed.
- `app/src/main/res/values/strings.xml` contains only `<string name="app_name">My Application</string>`. There is no `"Calendar"` string yet.

So #20 is **stacked on top of #10/#12's MainScreen impl**, which is itself unmerged. The execute phase of #20 cannot start until #10/#12 has landed (or until #20 is explicitly authorised to bundle the MainScreen bootstrap inline). It also conflicts in spirit with #14, #16, and #18, all of which touch the same `MainScreen` / `MainActivity` surface area in ways that overlap with this SPEC. See "Open questions" for the coordination plan.

The user-visible deltas vs. #10/#12 are small and crisp:

1. The bare `Scaffold { Box { Text(...) } }` from #10 grows a `CenterAlignedTopAppBar` slot.
2. A new string `R.string.main_top_bar_title` = `"Calendar"` is added.
3. The `Box` content gets `Modifier.padding(innerPadding)` so it respects the bar's inset (this is also what #10's plan already specifies).
4. Both light + dark previews are updated to render the bar.

---

## Goal

Launching the app shows a Material 3 `CenterAlignedTopAppBar` titled "Calendar" pinned to the top of `MainScreen`, with today's date `Text` centred in the content area below the bar and respecting the `Scaffold`'s `innerPadding`; both `@Preview` annotations (light + dark) render the bar identically using only `MaterialTheme.colorScheme` tokens.

---

## Non-goals

Copied from the issue and expanded:

- Navigation icons or action buttons in the app bar — no `navigationIcon`, no `actions` slot used.
- New dependencies — `androidx.compose.material3` already supplies `CenterAlignedTopAppBar`.
- A scroll behaviour (`TopAppBarDefaults.pinnedScrollBehavior` / `enterAlwaysScrollBehavior`) — `MainScreen`'s content is a single line of text with nothing to scroll. Speculative now.
- Any change to `MainActivity` — the issue is explicit that no changes are needed there.
- Subtitle, overline, or supporting text in the bar.
- Renaming `R.string.app_name`. The new string is a separate, dedicated key for the top-bar title.
- Adding a `TopAppBar` to any other screen.
- Changes to the date format, the `LocalDate.now()` wrapper, or the stateless/wrapper overload pattern from #10.
- Theme palette changes in `Color.kt`, `Type.kt`, or `Theme.kt`.
- Tests — #10 explicitly waived unit/UI tests for `MainScreen`; this SPEC inherits that waiver since it adds chrome only and `CenterAlignedTopAppBar` is a Material primitive that needs no behavioural test.
- Bundling the #10/#12 `MainScreen` bootstrap inline — that is its own SPEC and impl PR.

---

## Architecture choice

**Chosen approach:** modify the stateless `MainScreen(today: LocalDate, modifier: Modifier = Modifier)` composable from #10/#12 so its existing `Scaffold` declares a `topBar` slot containing a `CenterAlignedTopAppBar`. Title is a single `Text` reading `stringResource(R.string.main_top_bar_title)`. Bar colours come from `TopAppBarDefaults.centerAlignedTopAppBarColors()` (Material 3 default — pulls `containerColor` from `MaterialTheme.colorScheme.surface` and `titleContentColor` from `MaterialTheme.colorScheme.onSurface`); we do **not** override them. The wrapper `MainScreen()` and the date formatter are unchanged.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(today: LocalDate, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.main_top_bar_title)) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = today.format(DATE_FORMATTER),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
```

Key shape decisions:

1. **`CenterAlignedTopAppBar` is `@ExperimentalMaterial3Api`.** We add `@OptIn(ExperimentalMaterial3Api::class)` at the function level (or file level — see Open questions). This is the standard, blessed Material 3 pattern; opting in is not a workaround.
2. **No explicit `colors = TopAppBarDefaults.centerAlignedTopAppBarColors(...)` parameter.** The default already resolves to `MaterialTheme.colorScheme.surface` (container) and `MaterialTheme.colorScheme.onSurface` (title). The acceptance criterion "Background and title colours use `MaterialTheme.colorScheme` tokens only — no hardcoded values" is satisfied by *not* overriding. Passing the defaults explicitly would be a no-op tautology.
3. **`title = { Text(...) }` not `Text(text, style = MaterialTheme.typography.titleLarge)`.** `CenterAlignedTopAppBar` already styles its title slot with `MaterialTheme.typography.titleLarge`. Re-applying the style would be redundant.
4. **`stringResource(R.string.main_top_bar_title)`** — new key. CLAUDE.md is explicit ("Every user-facing string lives in `res/values/strings.xml`. … no hardcoded `dp`, `sp`, or color literals in composables"). The new key is `main_top_bar_title`, value `"Calendar"`. Reusing `R.string.app_name` (currently `"My Application"`) would either (a) rename the launcher label to "Calendar" — a side effect outside this issue's scope — or (b) keep "My Application" as the title, which contradicts the acceptance criterion of literal "Calendar". A dedicated key sidesteps both.
5. **`Box` with `contentAlignment = Alignment.Center`** for the content slot. #10's plan describes "a `Box` (or `Column`) filling max size with the date `Text` centred". `Box` + `contentAlignment` is a one-liner; `Column` would need `verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally`. Either works; `Box` is the smaller diff. The `Modifier.padding(innerPadding)` is required (acceptance criterion).
6. **Previews** — both `MainScreenLightPreview` and `MainScreenDarkPreview` are updated to render the bar simply by virtue of calling the stateless `MainScreen(today = …)` composable, which now contains the bar. No structural preview change is needed; the existing previews from #10/#12 will pick up the bar automatically. We re-verify the previews look right after the impl PR lands but the SPEC does not require new preview annotations. (Per CLAUDE.md, both light and dark previews already exist.)
7. **`MainActivity` is not touched.** Acceptance criterion. `MainActivity` already calls `MainScreen()` (after #10/#12 lands).
8. **Stateless / wrapper split preserved.** The bar lives inside the stateless overload — there is no I/O or async work to hoist out. Wrapper `MainScreen()` is unchanged.
9. **Accessibility.** `CenterAlignedTopAppBar`'s `title` slot inherits accessibility from the `Text`; no `contentDescription` needed because it's not an icon. No clickables added. Touch targets satisfied by the bar's intrinsic sizing.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Use plain `TopAppBar` instead of `CenterAlignedTopAppBar`. | Issue says explicitly "Material 3 `CenterAlignedTopAppBar`". Not a judgement call. |
| Rename `R.string.app_name` from "My Application" to "Calendar" and reuse it as the title. | Side effect: launcher label and any future `getString(R.string.app_name)` reader (e.g. notifications, About screen) would also flip to "Calendar". Issue's scope is the bar title only. A dedicated `main_top_bar_title` key keeps concerns separate; if a future issue wants to align the app name, that's a separate decision. |
| Override `colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)` explicitly. | Material 3's default already returns these exact tokens. The override is a no-op redundant call that just adds noise to the diff. Reviewer-friendlier to rely on the default and document it here. |
| Hardcode the title string `"Calendar"` directly in the composable. | Violates acceptance criterion ("pulled from `res/values/strings.xml`") and CLAUDE.md ("Every user-facing string lives in `res/values/strings.xml`"). |
| Put the `TopAppBar` in `MainActivity` and have `MainScreen` render only the inner content. | Acceptance criterion is explicit: `MainScreen` uses a `Scaffold` with the bar; `MainActivity` requires no changes. Also contradicts #10's "Scaffold lives inside MainScreen" decision. |
| Add a `scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())`. | `MainScreen`'s body is a single `Text` — there is nothing to scroll. Adding scroll plumbing now is speculative. Trivial to add later when a scrollable content slot lands. |
| Add a `navigationIcon` (e.g. an app logo). | Issue's "Out of scope" bullet explicitly excludes navigation icons. |
| Add an `actions` slot (e.g. a settings or about icon). | Issue's "Out of scope" bullet explicitly excludes action buttons. |
| Make the title `style = MaterialTheme.typography.titleLarge` explicitly inside the `Text`. | `CenterAlignedTopAppBar`'s `title` slot already applies `titleLarge`. Re-applying duplicates and risks divergence if Material 3 changes the default later. |
| File-level `@file:OptIn(ExperimentalMaterial3Api::class)`. | Function-level opt-in narrows blast radius — if a future composable in the same file accidentally uses an experimental API, it has to opt in deliberately. Matches what #14's plan implies ("Material 3 `Switch` … `TopAppBar`"). Reviewer call — see Open questions. |
| Remove the `Box` and put the date `Text` directly in the `Scaffold` content slot with `Modifier.fillMaxSize().padding(innerPadding).wrapContentSize(Alignment.Center)`. | Equivalent at runtime, but `Box` + `contentAlignment` reads more clearly to a future reviewer asking "why is this text centred?". Aesthetic call; either is acceptable. |
| Pull both `containerColor` and `titleContentColor` from `MaterialTheme.colorScheme.primaryContainer` / `onPrimaryContainer` for a more "branded" bar. | The default `surface`/`onSurface` is the Material 3 spec for a top app bar in a single-pane layout. Choosing `primaryContainer` would be a design decision the issue doesn't authorise. |
| Add a Compose UI test that asserts the bar exists and the title reads "Calendar". | #10 explicitly waived UI tests for `MainScreen`. Per CLAUDE.md, "Compose UI tests: only for non-trivial UI". Asserting a static title on a chrome-only change is busywork. |

---

## Implementation notes

### Files modified

- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` — modify the stateless `MainScreen(today: LocalDate, modifier: Modifier = Modifier)` per the snippet under "Architecture choice". Specifically:
  - Add `@OptIn(ExperimentalMaterial3Api::class)` to the stateless `MainScreen` function (and to the two `@Preview` functions if they directly construct any experimental Material 3 widget, which they don't — they call `MainScreen(...)` so the opt-in propagates through that call site; in practice the Kotlin compiler may still demand the annotation on the preview functions, see Open questions).
  - Add the `topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.main_top_bar_title)) }) }` parameter to the existing `Scaffold`.
  - The content lambda (`{ innerPadding -> Box(Modifier.fillMaxSize().padding(innerPadding), …) { Text(…) } }`) is unchanged — `padding(innerPadding)` was already part of #10's plan.
  - New imports: `androidx.compose.material3.CenterAlignedTopAppBar`, `androidx.compose.material3.ExperimentalMaterial3Api`, `androidx.compose.runtime.OptIn` *(actually `kotlin.OptIn`)*, `androidx.compose.ui.res.stringResource`, `com.example.myapplication.R` if not already imported.
- `app/src/main/res/values/strings.xml` — add `<string name="main_top_bar_title">Calendar</string>`. Keep `app_name` unchanged.

### Files NOT modified

- `app/src/main/java/com/example/myapplication/MainActivity.kt` — acceptance criterion. Already (post #10/#12) calls `MainScreen()`.
- `gradle/libs.versions.toml` — no new dependencies. `CenterAlignedTopAppBar` is in `androidx.compose.material3:material3` which #10/#12 already depends on (transitively via `androidx.activity:activity-compose` + `androidx.compose.bom`).
- `app/build.gradle.kts` — no plugin or version changes.
- `AndroidManifest.xml`, `themes.xml`, `Color.kt`, `Type.kt`, `Theme.kt` — palette and theme unchanged.

### Diff sketch

Approximately +12 lines, -1 line in `MainScreen.kt` (assuming #10's body has the `Box`/`Text` already wired). +1 line in `strings.xml`. Total ~13 net lines of code, plus the SPEC.

### Coordination & ordering

This SPEC presupposes #10 (or #12) has merged. If #20 enters execute phase before #10/#12 has landed:

- **Default — block:** post a comment on issue #20 saying "blocked on #10/#12 impl PR" and stop. The autonomous pipeline can re-trigger #20 once #10/#12 lands.
- **If reviewer overrides:** bundle a minimal `MainScreen` bootstrap into #20's impl PR (i.e. do #10's work as a prerequisite within #20). This is *not* the default because it duplicates #10's plan and risks divergence; flag clearly in the PR description.

This SPEC also conflicts with #14, #16, and #18:

- **#14** plans a plain `TopAppBar` (not `CenterAlignedTopAppBar`) with title `R.string.app_name` plus a gear `IconButton`. If #14 lands first, #20's executor must:
  1. Swap `TopAppBar` → `CenterAlignedTopAppBar`.
  2. Swap title source from `R.string.app_name` to `R.string.main_top_bar_title`.
  3. Preserve #14's gear `IconButton` in the `actions` slot — #20 does not say to remove it; only #16 removes it. The `CenterAlignedTopAppBar` `actions` slot exists and accepts the gear `IconButton` unchanged.
- **#16** plans the same plain `TopAppBar` with title `R.string.app_name` and *no* gear icon (peer-tab navigation supersedes the gear). If #16 lands before #20, then #20 only does steps 1+2 above — the bar is already gear-free.
- **#18** abandons the calendar direction entirely and replaces `Greeting` with a `CounterScreen`. If #18 lands first, there is no `MainScreen` to add a `TopAppBar` to and #20 is moot. Flag this on the plan PR — see Open questions.

The cleanest landing order from this SPEC's perspective is #10/#12 → #20 → #14 → #16 (and #18 is unrelated / probably wants closing as superseded by the calendar direction). Reviewer call — see Open questions.

---

## Open questions

Assumptions made (flagged for human confirmation; default behaviour described under each):

- [ ] **Prerequisite #10/#12 has not merged yet.** This SPEC modifies `MainScreen.kt`, which does not exist at plan time. **Default:** the executor blocks on #10/#12 and posts a comment. Alternative: bundle a minimal `MainScreen` bootstrap inline into #20's impl PR. Reviewer call.
- [ ] **Conflict with #14.** #14 plans a plain `TopAppBar` with `R.string.app_name` and a gear `IconButton` for navigation to `SettingsScreen`. If both #20 and #14 land in either order, the second to land must reconcile: title goes to `R.string.main_top_bar_title`, bar type goes to `CenterAlignedTopAppBar`, gear icon stays (added by #14, kept here). **Default:** preserve #14's gear `IconButton` in the `actions` slot if #14 lands first. If reviewers want #20 to land *first* and #14 to layer onto it, the change is symmetric. Pushback welcome.
- [ ] **Conflict with #16.** #16 supersedes #14's gear icon (Settings becomes a peer tab). #16 also keeps `TopAppBar` (plain) with `R.string.app_name`. If #16 lands first, #20 swaps the bar type and title source only; no gear concerns. **Default:** treat #16 as taking precedence on the gear-icon question.
- [ ] **Conflict with #18.** #18 redirects to a counter screen and removes `MainScreen` from the `MainActivity` setContent path. If #18 lands first, #20 has nothing to modify. **Default recommendation:** if #18 has landed at execute time, post a comment on #20 asking whether #20 should be closed as superseded. Do not implement.
- [ ] **String key name.** Chose `main_top_bar_title`. Alternatives: `top_bar_title_calendar`, `calendar_title`, `app_top_bar_title`. **Default:** `main_top_bar_title` — pairs with `MainScreen`, future-extensible if a similar pattern (`settings_top_bar_title`) emerges. Reviewer call.
- [ ] **Reuse of `R.string.app_name`.** `app_name` is currently "My Application". Reusing it would either flip the launcher label to "Calendar" (cross-cutting side effect) or keep "My Application" as the bar title (contradicts acceptance criterion). **Default:** new dedicated key. If the reviewer wants to align the app name with the bar title, file a follow-up issue and rename `app_name` there.
- [ ] **Title colour token.** Defaulting to `TopAppBarDefaults.centerAlignedTopAppBarColors()` resolves to `MaterialTheme.colorScheme.surface` (container) and `MaterialTheme.colorScheme.onSurface` (title). Acceptance criterion is satisfied; the SPEC reads "background and title colours use `MaterialTheme.colorScheme` tokens only — no hardcoded values" which the default already meets. If the reviewer wants explicit named tokens (e.g. `primaryContainer` / `onPrimaryContainer`), specify and the impl will pass them explicitly via `colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = …, titleContentColor = …)`. **Default:** rely on the Material 3 default (no `colors` param).
- [ ] **`@OptIn(ExperimentalMaterial3Api::class)` placement.** Function-level vs. file-level. **Default:** function-level on the stateless `MainScreen` (and on each `@Preview` that calls into it if the compiler still requires it — Kotlin's opt-in propagation usually means the previews inherit, but in some Compose toolchains the compiler still flags the previews; the impl phase will land on whatever the compiler accepts). Reviewer call if file-level (`@file:OptIn`) is preferred.
- [ ] **Scroll behaviour.** No `scrollBehavior` attached to the bar, since the body is a single non-scrollable `Text`. If a future issue makes `MainScreen` content scrollable (a calendar grid), the bar will need `pinnedScrollBehavior` or `enterAlwaysScrollBehavior`. Speculative now. **Default:** no scroll behaviour.
- [ ] **`Box` vs. `Column` for the centred body.** Functionally equivalent. **Default:** `Box` with `contentAlignment = Alignment.Center` (smaller diff, clearer intent). If house style prefers `Column { verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally }`, swap.
- [ ] **Tests.** No tests added — chrome-only change, #10 already waived `MainScreen` tests, and CLAUDE.md says Compose UI tests are reserved for non-trivial UI. **Default:** ship without tests. If reviewers want a smoke test that asserts the title text renders, an `androidTest` Compose UI test class would be ~10 lines using `createComposeRule()` and `onNodeWithText("Calendar").assertIsDisplayed()`. Trivial to add if asked.
- [ ] **Locale of the title string.** "Calendar" is en-US/en-GB English. CLAUDE.md mandates strings be in `res/values/strings.xml` (the en default); future locales would add `res/values-<lang>/strings.xml`. **Default:** ship the en string in `values/strings.xml` only. No translations as part of this issue.
- [ ] **Rendering across both previews.** Existing `@Preview` annotations from #10/#12 (`MainScreenLightPreview`, `MainScreenDarkPreview`) call the stateless `MainScreen(today = …)`. They will pick up the bar automatically; no preview-annotation changes needed. **Default:** rely on the existing previews; no new `@Preview` functions added. Reviewer call if a third "preview with explicit bar" is wanted.

---

## Acceptance criteria

Copied from the issue:

- [ ] `MainScreen` uses a `Scaffold` with a `TopAppBar` (Material 3 `CenterAlignedTopAppBar`).
- [ ] The app bar title is the string `"Calendar"` pulled from `res/values/strings.xml` (no hardcoded strings in composables).
- [ ] Background and title colours use `MaterialTheme.colorScheme` tokens only — no hardcoded values.
- [ ] The existing date text remains centred in the content area below the app bar, respecting `innerPadding` from the `Scaffold`.
- [ ] Light + dark `@Preview` annotations updated to reflect the new layout.
- [ ] `MainActivity` requires no changes — it already calls `MainScreen()`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` is green.

---

## Out of scope

Copied from the issue:

- Navigation icons or action buttons in the app bar (future issue).
- Any new dependencies — Compose + Material 3 already cover `CenterAlignedTopAppBar`.

Expanded:

- Renaming `R.string.app_name`.
- Scroll behaviours (`pinnedScrollBehavior`, `enterAlwaysScrollBehavior`).
- Per-locale translations of the new title string.
- Tests for the bar (UI or unit).
- Theme palette, typography, or `dp` token changes.
- Any change to the wrapper `MainScreen()` overload, the `LocalDate` formatter, or `MainActivity`.
- Bundling the #10/#12 `MainScreen` bootstrap inside this issue's impl PR.
