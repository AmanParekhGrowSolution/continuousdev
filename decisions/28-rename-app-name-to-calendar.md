# [Issue #28] Rename `app_name` string from "My Application" to "Calendar"

> **Status:** Draft
> **Issue:** #28
> **Date:** 2026-04-30

---

## Context

The launcher icon label and recent-tasks card title are both rendered by the system from `R.string.app_name`. That string was seeded by the Android Studio "Empty Compose Activity" template with the literal `"My Application"` and has not yet been updated, so users currently see the placeholder label on the home screen.

**Prerequisite state at plan time (2026-04-30, branch `master` at `acbac13`):**

- `app/src/main/res/values/strings.xml` — single entry, `<string name="app_name">My Application</string>`. Verified.
- `app/src/main/AndroidManifest.xml` — references `@string/app_name` on both `<application android:label="@string/app_name">` (line 10) and `<activity android:name=".MainActivity" android:label="@string/app_name">` (line 17). No hard-coded label string. Verified.
- A repo-wide search (`grep -rn "app_name\|My Application" app/`) returns exactly three hits: the one declaration in `strings.xml` and the two manifest references above. No Kotlin source reads `R.string.app_name`. No drawable, theme, or other XML reads it.
- Other occurrences of the substring "My Application" in `app/` (the package `com.example.myapplication`, the theme `@style/Theme.MyApplication`, the Compose theme function `MyApplicationTheme`, JVM test class names) are identifiers, not user-facing strings — explicitly out of scope (see "Non-goals").
- `MainScreen.kt` does not reference `R.string.app_name`; it only renders today's date.

**Relationship to prior decisions:**

- `decisions/23-rename-app-to-calendar.md` (#23) is a previously-merged plan for the *same* rename. PR #24 merged that SPEC into `decisions/` on `4f59bcd`, but no implementation PR followed — `strings.xml` still reads `"My Application"`. Issue #28 is effectively a re-trigger of that work. This SPEC supersedes #23 in the sense that the impl PR generated from #28 will be the one that actually flips the string. No technical content is changed; the architecture and analysis from #23 hold and are reproduced here so the executor can rely on a single SPEC. After #28's impl lands, the `decisions/23-...` file should be marked `Superseded by #28` in a follow-up housekeeping pass (out of scope here — single-line resource changes don't justify also editing two SPEC files).
- `decisions/20-topappbar-mainscreen.md` (#20) introduces a *separate* string `R.string.main_top_bar_title = "Calendar"` for the in-app `CenterAlignedTopAppBar` title. That is independent of `app_name` by design (see #20's own architecture notes); both keys end up holding `"Calendar"` after both land, with no coupling.
- `decisions/14-settings-dark-mode-toggle.md` (#14) and `decisions/16-bottom-navigation-bar.md` (#16) plan UI surfaces whose `TopAppBar` title reads `R.string.app_name`. After #28 lands, those titles will read `"Calendar"`, which is the desired end state for those SPECs too. No coordination required.

So #28 is a one-line resource change with no architectural implications and no cross-PR ordering constraints.

---

## Goal

The launcher icon label and recent-tasks card title for the installed debug APK read `"Calendar"` instead of `"My Application"`, driven by the single value of `R.string.app_name`.

---

## Non-goals

Copied from the issue and expanded:

- UI or composable changes.
- New dependencies.
- Translations / additional locale files. No `res/values-<locale>/strings.xml` files exist today, so there is nothing to keep in sync; none are added by this SPEC.
- Renaming the Gradle `applicationId` (`com.example.myapplication`) or the Kotlin package. Identifiers, not user-facing; renaming would break Hilt-generated classes, every `import com.example.myapplication.*`, the `namespace` in `app/build.gradle.kts`, every test file, and CI artifact paths.
- Renaming the Compose theme function `MyApplicationTheme` or the Android theme `@style/Theme.MyApplication`. Identifiers; touching them would force a manifest edit and changes to every `@Preview` across the codebase.
- Renaming JVM unit test classes (e.g. `ExampleUnitTest`) or the on-disk module name `app/`.
- Adding a separate `app_label` / `launcher_label` string. The single `app_name` key is the conventional Android idiom and the issue is explicit that no other files need to change.
- Changing `android:icon` / `android:roundIcon` / launcher icon assets. Renaming ≠ rebranding.
- Updating `decisions/20-topappbar-mainscreen.md` to reuse `app_name`. That's #20's own decision; #28 does not re-litigate it.
- Adding tests. Compose UI tests don't observe the launcher label (it's drawn by the system launcher, outside the app process), and a unit test that asserts a string-resource value equals `"Calendar"` is a tautology that pins the literal twice. Manual verification on the build artifact is sufficient (see "Acceptance criteria").
- Editing or marking `decisions/23-rename-app-to-calendar.md` as superseded. Single-line resource changes shouldn't drag two SPEC files along; that housekeeping is a separate, optional follow-up.

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
| Mark the prior SPEC (`decisions/23-rename-app-to-calendar.md`) as `Superseded by #28` in this same PR. | Pulls a second file into a one-line change for no functional reason. The prior SPEC's content is technically still correct; superseding metadata is a documentation-only concern that can be a follow-up if anyone cares. Keeping the impl PR's diff to exactly one file makes the acceptance check trivial. |

---

## Implementation notes

**Files to modify (exactly one):**

- `app/src/main/res/values/strings.xml` — change the value of the existing `<string name="app_name">` element from `My Application` to `Calendar`. Element name, position, attributes, and surrounding `<resources>` wrapper stay the same.

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
- Any file under `decisions/` (including `23-rename-app-to-calendar.md`).
- Any file under `.github/`.

**Hilt modules / navigation graph / Room entities / DataStore keys:** none introduced.

**Verification commands** (per CLAUDE.md, must pass locally before opening the impl PR):

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

**Manual verification** (post-install on an emulator or device, optional):

- The launcher icon label reads `"Calendar"`.
- The recent-tasks card title reads `"Calendar"`.

---

## Open questions

No blocking ambiguities. The following were considered and resolved at plan time without needing human input:

- [x] Is the launcher label the only place `app_name` appears? → Yes. `grep -rn "app_name" app/` returns three hits: one declaration in `strings.xml` and two references in `AndroidManifest.xml`. No Kotlin source, drawable, or theme reads it.
- [x] Does this conflict with #20's `main_top_bar_title`? → No. Independent string keys; both will hold `"Calendar"` after both land. #20 deliberately chose a separate key.
- [x] Should the rename also touch the package `com.example.myapplication` or the theme name `MyApplicationTheme`? → No. The issue is explicit ("No other files need to change"). Identifier renames are a separate, larger change.
- [x] Translations? → Out of scope per the issue. No `res/values-<locale>/` directories exist.
- [x] Why is there already a `decisions/23-rename-app-to-calendar.md` for this same rename? → #23's plan PR (#24) merged at `4f59bcd`, but the implementation PR for #23 was never opened (the autonomous pipeline had a broken `execute` trigger that was only fixed in #27 / `acbac13`). #28 re-triggers the work under the now-fixed pipeline. Treating #28 as a fresh plan + impl is simpler than trying to retroactively kick off #23's execute job, and an impl PR generated from #28 will close out the user-visible work either way.
- [x] Should this PR also mark `decisions/23-rename-app-to-calendar.md` as `Superseded by #28`? → No (see "Rejected alternatives"). Keeping the impl PR's diff to exactly one file is a cleaner acceptance signal.

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
