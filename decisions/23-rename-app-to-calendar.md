# [Issue #23] Rename app from "My Application" to "Calendar"

> **Status:** Draft
> **Issue:** #23
> **Date:** 2026-04-30

---

## Context

The project was bootstrapped from the Android Studio "Empty Compose Activity" template, which seeds `R.string.app_name` with the literal `"My Application"`. That string is rendered by the system in two user-visible places:

1. The launcher icon label on the home screen / app drawer.
2. The recent-tasks card title.

Because `AndroidManifest.xml` references `@string/app_name` on both the `<application>` and `<activity android:name=".MainActivity">` elements (verified at plan time — see `app/src/main/AndroidManifest.xml`), a single edit to `res/values/strings.xml` flips both surfaces. The trajectory of the project (per `decisions/10-bootstrap-mainscreen.md` and `decisions/20-topappbar-mainscreen.md`) is a calendar app, so the launcher label needs to match.

**Prerequisite state at plan time (2026-04-30):**

- `app/src/main/res/values/strings.xml` contains exactly one entry: `<string name="app_name">My Application</string>`.
- `app/src/main/AndroidManifest.xml` references `@string/app_name` from `<application android:label=...>` and `<activity ... android:label=...>`. No hard-coded label.
- The only other occurrence of the substring "My Application" in `app/` is the package name (`com.example.myapplication`), the theme name (`@style/Theme.MyApplication`), the Compose theme function (`MyApplicationTheme`), and the JVM test class names. None of these are user-facing — they are identifiers, not strings shown in the UI. They are explicitly out of scope for this issue (see "Non-goals").
- `MainScreen.kt` does not reference `R.string.app_name` directly; it only renders today's date.
- `decisions/20-topappbar-mainscreen.md` (#20) plans a `CenterAlignedTopAppBar` whose title comes from a *new* string `R.string.main_top_bar_title` = `"Calendar"`, deliberately separate from `app_name`. #20 is unmerged at plan time. After both #23 and #20 land, both keys will hold the value `"Calendar"` independently — no coupling, no conflict.
- `decisions/14-settings-dark-mode-toggle.md` (#14) and `decisions/16-bottom-navigation-bar.md` (#16) plan a `TopAppBar` whose title is `R.string.app_name`. After #23 lands, that title will read `"Calendar"`, which is the desired end state for those SPECs too. No coordination needed — those SPECs will pick up the renamed value automatically.

So #23 is genuinely a one-line resource change with no architectural implications.

---

## Goal

The launcher label and recent-tasks title for the installed debug APK read "Calendar" instead of "My Application", driven by the single value of `R.string.app_name`.

---

## Non-goals

Copied from the issue and expanded:

- Any UI or composable changes.
- New dependencies.
- Translations / additional locale files (no `res/values-<locale>/strings.xml` files are added; no existing locale files exist to update).
- Renaming the Gradle application ID `com.example.myapplication`. The package name is an identifier, not a user-facing string; changing it would break Hilt-generated code, test class references, and CI artifact paths. Out of scope.
- Renaming the Compose theme function `MyApplicationTheme` or the Android theme `@style/Theme.MyApplication`. Identifiers, not user-facing.
- Renaming JVM unit test classes (`ExampleUnitTest`) or the on-disk module name `app/`.
- Adding a separate `app_label` or `launcher_label` string. The single `app_name` key is the conventional Android idiom and the issue is explicit that no other files need to change.
- Updating `decisions/20-topappbar-mainscreen.md` to reuse `app_name` instead of a dedicated `main_top_bar_title`. That's a separate decision in #20's SPEC; this SPEC does not re-litigate it.
- Changing `android:icon` / `android:roundIcon` / launcher icon assets. Renaming ≠ rebranding.
- Tests. Compose UI tests don't observe the launcher label (it's rendered by the system launcher, not by the app process), and a unit test for "this string equals 'Calendar'" would just assert a tautology. Manual verification on the build artifact is sufficient (see "Acceptance criteria").

---

## Architecture choice

**Chosen approach:** edit `app/src/main/res/values/strings.xml` and change the value of the existing `app_name` resource from `"My Application"` to `"Calendar"`. No other file is touched. The `AndroidManifest.xml` references `@string/app_name` on both `<application>` and `<activity>`, so the launcher label and recent-tasks title pick up the new value automatically on the next install.

**Rejected alternatives:**

| Alternative | Why rejected |
|---|---|
| Add a new string `app_launcher_label` and point the manifest at it, leaving `app_name` untouched. | Two strings for the same concept invites drift — a future contributor will edit one and not the other. The Android convention is one `app_name` key. The issue explicitly says no other files need to change. |
| Hard-code `android:label="Calendar"` in `AndroidManifest.xml` and delete `app_name`. | Loses i18n affordance for free (today there are no translations, but if `res/values-es/strings.xml` is ever added, having `app_name` already wired through string resources is the path of least resistance). Also violates the "every user-facing string lives in `res/values/strings.xml`" rule from CLAUDE.md. |
| Rename the Gradle `applicationId` from `com.example.myapplication` to `com.example.calendar` as part of this change. | Out of scope per the issue, and high-blast-radius: renames Hilt-generated `Hilt_*` classes, requires updating every `package` declaration, every `import com.example.myapplication.*`, the `namespace` in `app/build.gradle.kts`, every test, and every CI artifact path. A user upgrading from a build with the old applicationId would get a fresh install (data loss). Not worth bundling with a string rename. |
| Bundle a rename of `MyApplicationTheme` → `CalendarTheme` (in `ui/theme/Theme.kt`) and the XML `@style/Theme.MyApplication` → `@style/Theme.Calendar`. | Identifiers, not user-facing. Touches every preview function across `MainScreen.kt` (and any future screen) and the manifest. The issue is explicit: "No other files need to change." Defer to a future cleanup if/when the package rename happens. |
| Add a `<string name="app_name" translatable="false">` attribute. | The default is `translatable="true"`, which is correct: future locale forks may want a localized launcher label. Adding `translatable="false"` would lock that out for no benefit. |

---

## Implementation notes

**Files to modify:**

- `app/src/main/res/values/strings.xml` — change the value of the `app_name` element from `My Application` to `Calendar`. The `<string name="app_name">` element and its position in the file stay the same.

**Files to create:** none.

**Files explicitly NOT to modify:**

- `app/src/main/AndroidManifest.xml` — already references `@string/app_name`.
- `app/src/main/java/com/example/myapplication/ui/main/MainScreen.kt` — does not display the app name.
- `app/src/main/java/com/example/myapplication/ui/theme/*.kt` — theme name is an identifier, not user-facing.
- `app/build.gradle.kts` — `namespace`, `applicationId`, and `versionName` are unchanged.
- Any file under `decisions/`.

**Hilt modules / navigation graph / Room entities / DataStore keys:** none introduced.

**Verification commands** (per CLAUDE.md, must pass locally before opening the impl PR):

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

**Manual verification** (post-install on emulator or device, optional but recommended):

- The launcher icon label reads "Calendar".
- The recent-tasks card title reads "Calendar".

---

## Open questions

No blocking ambiguities. The following were considered and resolved without needing human input:

- [x] Is the launcher label the only place `app_name` appears? → Yes. `grep -rn "app_name" app/` returns only `strings.xml` (declaration) and `AndroidManifest.xml` (two references on `<application>` and `<activity>`). No Kotlin code reads `R.string.app_name`. No XML drawable / theme reads it.
- [x] Does this conflict with #20's `main_top_bar_title`? → No. They are independent string keys; both will hold `"Calendar"` after both land. #20 deliberately chose a separate key (see #20's SPEC for the reasoning).
- [x] Should the rename also touch the package name `com.example.myapplication` or the theme name `MyApplicationTheme`? → No. The issue is explicit: "No other files need to change." Identifier renames are a separate, larger change.
- [x] Translations? → Out of scope per the issue. No `res/values-<locale>/strings.xml` files exist, so there is nothing to keep in sync.

---

## Acceptance criteria

Copied from the issue:

- [ ] `res/values/strings.xml`: `app_name` value is `"Calendar"` (was `"My Application"`).
- [ ] No other files in `app/` are modified by the implementation PR. `git diff master --stat` for the impl PR should show exactly one file changed: `app/src/main/res/values/strings.xml`.
- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` passes.

---

## Out of scope

Copied from the issue:

- Any UI or composable changes.
- New dependencies.
- Translations / additional locale files.
