# [Issue #30] Rename `app_name` string from "My Application" to "Calendar"

> **Status:** Draft
> **Issue:** #30
> **Date:** 2026-04-30

---

## Context

The launcher icon label and recent-tasks card title are both rendered by the system from `R.string.app_name`. The value was seeded by the Android Studio "Empty Compose Activity" template as the literal `"My Application"` and has never been updated, so users currently see the placeholder label on the home screen and recent-apps switcher.

**Prerequisite state at plan time (2026-04-30, branch `master` at `d5b254c`):**

- `app/src/main/res/values/strings.xml` — single entry, `<string name="app_name">My Application</string>`. Verified by reading the file.
- `app/src/main/AndroidManifest.xml` — references `@string/app_name` on both `<application android:label="@string/app_name">` (line 10) and `<activity android:name=".MainActivity" android:label="@string/app_name">` (line 17). No hard-coded label string. Verified.
- A repo-wide search (`grep -rn "app_name\|My Application" app/`) returns exactly three hits: the one declaration in `strings.xml` and the two manifest references above. No Kotlin source reads `R.string.app_name`. No drawable, theme, or other XML reads it.
- Other occurrences of the substring "My Application" / "myapplication" in `app/` (the package `com.example.myapplication`, the theme `@style/Theme.MyApplication`, the Compose theme function `MyApplicationTheme`, JVM test class names) are identifiers, not user-facing strings — explicitly out of scope (see "Non-goals").
- `MainScreen.kt` does not reference `R.string.app_name`; it only renders today's date.

**Relationship to prior decisions (this is the third plan for the same rename):**

- `decisions/23-rename-app-to-calendar.md` (#23) — plan PR (#24) merged at `4f59bcd`. No implementation PR followed because the autonomous pipeline's `execute` trigger was broken at that time (later fixed in #27 / `acbac13`).
- `decisions/28-rename-app-name-to-calendar.md` (#28) — plan PR (#29) merged at `d5b254c` *after* the trigger fix. Despite that, no implementation PR has landed yet either: `strings.xml` still reads `"My Application"`. Whatever blocked #28's `execute` (or its impl PR) has not been diagnosed in the public record at plan time, but the resource change itself has nothing wrong with it — both #23 and #28 chose the same one-file edit and that approach is still correct. Issue #30 is a re-trigger to actually land the user-visible change.
- This SPEC supersedes #23 and #28 in the operational sense that the impl PR generated from #30 will be the one that flips the string. We deliberately do **not** edit either prior SPEC's `Status:` field in this PR — see "Rejected alternatives" below.
- `decisions/20-topappbar-mainscreen.md` (#20) introduces a *separate* string `R.string.main_top_bar_title = "Calendar"` for the in-app `CenterAlignedTopAppBar` title. That key is independent of `app_name` by design (see #20's own architecture notes); both end up holding `"Calendar"` after both land, with no coupling.
- `decisions/14-settings-dark-mode-toggle.md` (#14) and `decisions/16-bottom-navigation-bar.md` (#16) plan UI surfaces whose `TopAppBar` title reads `R.string.app_name`. After #30 lands, those titles will read `"Calendar"`, the desired end state. No coordination required.

So #30, like #23 and #28 before it, is a one-line resource change with no architectural implications and no cross-PR ordering constraints.

---

## Goal

The launcher icon label and recent-tasks card title for the installed debug APK read `"Calendar"` instead of `"My Application"`, driven by the single value of `R.string.app_name`.

---

## Non-goals

Copied from the issue's "Out of scope" and expanded:

- UI or composable changes.
- New dependencies.
- Translations / additional locale files. No `res/values-<locale>/strings.xml` files exist today, so there is nothing to keep in sync; none are added by this SPEC.
- Renaming the Gradle `applicationId` (`com.example.myapplication`) or the Kotlin package. Identifiers, not user-facing; renaming would break Hilt-generated classes, every `import com.example.myapplication.*`, the `namespace` in `app/build.gradle.kts`, every test file, and CI artifact paths. Also breaks update-in-place for any installed build, which would orphan local data.
- Renaming the Compose theme function `MyApplicationTheme` or the Android theme `@style/Theme.MyApplication`. Identifiers; touching them would force a manifest edit and changes to every `@Preview` across the codebase.
- Renaming JVM unit test classes (e.g. `ExampleUnitTest`) or the on-disk module name `app/`.
- Adding a separate `app_label` / `launcher_label` string. The single `app_name` key is the conventional Android idiom and the issue is explicit that no other files need to change.
- Changing `android:icon` / `android:roundIcon` / launcher icon assets. Renaming ≠ rebranding.
- Updating `decisions/20-topappbar-mainscreen.md` to reuse `app_name`. That is #20's own decision; #30 does not re-litigate it.
- Adding tests. Compose UI tests don't observe the launcher label (it's drawn by the system launcher, outside the app process), and a unit test that asserts a string-resource value equals `"Calendar"` is a tautology that pins the literal twice. Manual verification on the build artifact is sufficient (see "Acceptance criteria").
- Marking `decisions/23-rename-app-to-calendar.md` or `decisions/28-rename-app-name-to-calendar.md` as `Superseded by #30`. Single-line resource changes shouldn't drag two SPEC files along; that's a separate, optional housekeeping pass and dragging it in here would also expand the impl PR's diff beyond the trivial one-file change that makes acceptance trivial to verify.
- Investigating *why* #28's implementation never landed. That is a CI/pipeline question, not a code question; the resource change itself is independently correct. If #30 also fails to produce an impl PR, that is the signal to dig into the autonomous workflow — out of scope for this SPEC.

---

## Architecture choice

**Chosen approach:** edit `app/src/main/res/values/strings.xml` and change the value of the existing `<string name="app_name">` element from `My Application` to `Calendar`. No other file is touched. The `AndroidManifest.xml` already references `@string/app_name` on both `<application>` and `<activity>`, so the launcher label and recent-tasks title pick up the new value automatically on the next install.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Add a new string `app_launcher_label` and point the manifest at it, leaving `app_name` untouched. | Two strings for the same concept invites drift — a future contributor will edit one and not the other. The Android convention is one `app_name` key, and the issue explicitly says no other files need changing. |
| Hard-code `android:label="Calendar"` in `AndroidManifest.xml` and delete `app_name`. | Violates the CLAUDE.md rule "every user-facing string lives in `res/values/strings.xml`". Also throws away free i18n affordance: if `res/values-<locale>/strings.xml` is added later, having the label already wired through string resources is the path of least resistance. |
| Bundle a rename of the Gradle `applicationId` from `com.example.myapplication` to `com.example.calendar`. | Out of scope per the issue, and high blast radius: renames Hilt-generated `Hilt_*` classes, every `package` declaration, every `import com.example.myapplication.*`, the `namespace` in `app/build.gradle.kts`, every test, and every CI artifact path. A user upgrading from a build with the old `applicationId` would get a fresh install (data loss). Not worth bundling with a one-line string change. |
| Bundle a rename of `MyApplicationTheme` → `CalendarTheme` and `@style/Theme.MyApplication` → `@style/Theme.Calendar`. | Identifiers, not user-facing. Would force an edit to the manifest and every `@Preview` across `MainScreen.kt` and any future screens. The issue is explicit: "No other files need to change." Defer to a future cleanup if/when the package rename happens. |
| Add `translatable="false"` to the `app_name` element. | The default (`translatable="true"`) is the correct behavior — future locale forks may want a localized launcher label. Adding `translatable="false"` would lock that out for no benefit, and it's not in scope. |
| Bundle SPEC-housekeeping (mark `decisions/23-...` and `decisions/28-...` as `Superseded by #30`) into this PR. | Pulls two extra files into a one-line change for no functional reason. The prior SPECs' content is technically still correct; superseding metadata is a documentation-only concern that can be a follow-up. Keeping the impl PR's diff to exactly one file (`app/src/main/res/values/strings.xml`) makes the acceptance check trivial and matches the "exactly one file changed" check in "Acceptance criteria". |
| Re-run #28's plan PR's executor manually instead of opening #30. | Not available to the planner — re-triggering a closed plan PR's `execute` job is a pipeline-level operation outside this SPEC's scope. Issue #30 is the user's chosen mechanism for re-triggering, so this SPEC honors that and produces a fresh plan PR. |

---

## Implementation notes

**Files to modify (exactly one):**

- `app/src/main/res/values/strings.xml` — change the value of the existing `<string name="app_name">` element from `My Application` to `Calendar`. Element name, position, attributes, and the surrounding `<resources>` wrapper stay identical.

  After the edit, the file should read:
  ```xml
  <resources>
      <string name="app_name">Calendar</string>
  </resources>
  ```

**Files to create:** none.

**Files explicitly NOT to modify:**

- `app/src/main/AndroidManifest.xml` — already references `@string/app_name`; will pick up the new value at build time.
- `app/src/main/java/com/example/myapplication/MainActivity.kt` — does not reference `app_name`.
- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` — does not display the app name.
- `app/src/main/java/com/example/myapplication/ui/theme/*.kt` — theme name is an identifier, not user-facing.
- `app/build.gradle.kts` — `namespace`, `applicationId`, `versionName` are unchanged.
- Any file under `decisions/` (including `23-rename-app-to-calendar.md` and `28-rename-app-name-to-calendar.md`).
- Any file under `.github/`.

**Hilt modules / navigation graph / Room entities / DataStore keys:** none introduced.

**Verification commands** (per CLAUDE.md, must pass locally before opening the impl PR):

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

**Manual verification** (post-install on an emulator or device, optional — not required for CI):

- The launcher icon label reads `"Calendar"`.
- The recent-tasks card title reads `"Calendar"`.

---

## Open questions

No blocking ambiguities. The following were considered and resolved at plan time without needing human input:

- [x] Is the launcher label the only place `app_name` appears? → Yes. `grep -rn "app_name\|My Application" app/` returns three hits: one declaration in `strings.xml` and two references in `AndroidManifest.xml`. No Kotlin source, drawable, or theme reads it.
- [x] Does this conflict with #20's `main_top_bar_title`? → No. Independent string keys; both will hold `"Calendar"` after both land. #20 deliberately chose a separate key.
- [x] Should the rename also touch the package `com.example.myapplication` or the theme name `MyApplicationTheme`? → No. The issue is explicit ("No other files need to change"). Identifier renames are a separate, larger change with high blast radius.
- [x] Translations? → Out of scope per the issue. No `res/values-<locale>/` directories exist.
- [x] Why are there already two prior plan SPECs (#23, #28) for the same rename? → #23's impl never ran because the `execute` trigger was broken (fixed in #27). #28's plan landed after that fix but still produced no impl PR for reasons not visible in the merged record. #30 is the user's third re-trigger; the SPEC content is unchanged because the architecture was correct both prior times.
- [x] Should this PR mark `decisions/23-...` and `decisions/28-...` as `Superseded by #30`? → No (see "Rejected alternatives"). Keeping the impl PR's diff to exactly one file is a cleaner acceptance signal. SPEC housekeeping is an optional, separate follow-up that the user can request explicitly.
- [x] Should the implementer investigate *why* #28's impl PR never appeared? → No. That is a pipeline/CI question, not a code question; out of scope for this SPEC. If #30's impl also fails to materialize, that is the signal to dig into the workflow.

---

## Acceptance criteria

Copied from the issue:

- [ ] `app/src/main/res/values/strings.xml`: `app_name` value is `"Calendar"` (was `"My Application"`).
- [ ] No other files in `app/` are modified by the implementation PR. `git diff master --stat` for the impl PR should show exactly one file changed: `app/src/main/res/values/strings.xml`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` passes.

---

## Out of scope

Copied from the issue:

- UI or composable changes.
- New dependencies.
- Translations.
