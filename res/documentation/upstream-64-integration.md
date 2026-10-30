# StreetComplete 64 integration

Current verified status (2026-10-30): the merge conflicts are resolved and the merge is still open.
The current Android host suite passes 2,552 tests with one skipped and no failures/errors
(2,553 cases in 293 suites, repeated after the Android fix in run 18b). Common metadata passes
with the current source. The current release R8 path passes in run 19 with debug features
disabled (6m 10s). The earlier isolated regression run passed 31 tests in seven suites.

Run 17 successfully compiled common metadata, Kotlin iOS device/simulator sources and
simulator test sources on Windows (16m 2s, including the Android
tests). This is source/KLIB compilation, not Swift application building or iOS execution.
Run 18 rebuilds the Android APK with the Android-specific startup fix; common/iOS sources
were unchanged from run 17 at that checkpoint. Run 20 successfully recompiles the current
iOS device/simulator main and simulator test sources after the periodic-cleanup correction
described below (4m 25s). Android sources are unchanged from run 18.
The APK is SCEE Dev, package `de.westnordost.streetcomplete.expert.debug`, version 64.0/6405,
min SDK 25/target 37. Signature verification, ZIP alignment and 16-KB ELF checks pass for all
eight 64-bit native libraries. APK SHA-256:
`2917412783B94D8CF51CCF86B425C2133649B8B741E3D0D71324C10BDB989176`.

Run 15 passes Android assembly and common metadata compilation (6m 33s). Its Android 37/16-KB
runtime check passes map rendering/downloads, quest-pin opening, settings/preset persistence,
expert mode, zoom controls, GPX import/display, the surfaces overlay and form, SCEE element
actions/raw tag editor, and local GPX-note saving. Settings list 257 quest types. The first run
exposed an invalid POI-label `text-allow-overlap` zoom comparison; replacing it with a top-level
`step` preserves the threshold of 21 and eliminates the style warnings. No OSM account or
upload was used; both OSM edit queues were checked empty after the test.

The targeted run-17 hardware check confirms volume-up zooms 18 to 19 and volume-down returns
to 18. Its overloaded emulator cold start also exposed a JobService ANR: the main thread was
waiting in `ConnectivityManager.getActiveNetwork` from the network-capabilities flow. The
Android-only flow now runs on `Dispatchers.IO`, including callback registration/cleanup.
Run 18 builds successfully (4m 34s); run 18b repeats all 2,553 tests without failures/errors
(1m 40s). Its fresh-process start after reboot succeeds in 14.072 seconds under AVD boot load.
The map, settings and volume zoom respond; no SCEE ANR/crash events or map-style errors occur
in the observation window. The AVD still reports system/Google-app ANRs. The dedicated emulator
was shut down after testing. See `.gradle/android-smoke/startup-result-18.json`.

A real SCEE 63.4 database, initialized by its official checksum-verified APK, was seeded with
synthetic test data and copied into the debug app. Migration from schema 20 to 22 passes the
integrity check: the selected preset, SCEE quest visibility, expert/zoom preferences and legacy
camera values survive; obsolete upstream quest rows are removed. This is a data migration
test across package names, not a signed in-place production APK upgrade.

The iOS zoom adaptation uses the existing on-screen control preference: iOS/desktop show this
control instead of an inert volume-key option. Importing an enabled Android volume-key
preference enables it while retaining the hardware preference for a return export. Unrelated
imports preserve the user's choice. All four regression tests pass in run 17; generated
Android/iOS platform values were checked. The iOS UI still needs execution on macOS.

An additional source audit found that iOS scheduled cleanup only once, while Android uses
recurring daily work. The iOS handler now schedules the next request before cleaning or
reporting completion, retaining the existing pending-request check and mutex so reopening
the app does not postpone an already queued request. Expiration cancels the coordinator and
the independent cleaner IO job, and waits for the current batch to stop before reporting
failure. The expiration/completion handlers are installed before the coordinator starts.
Apple documents that each background request launches only once and that a recurring task
must schedule its successor in the handler; see the
[background request scheduling demo](https://developer.apple.com/videos/play/wwdc2019/707/).
Real iOS validation remains required: launch the cleanup task, inspect its pending successor,
then simulate expiration during a database batch and confirm unsuccessful completion after
the cleaner stops. Source compilation alone does not prove these scheduler behaviors.

Swift/application building, iOS execution and the remaining behavioral audit are pending.
The user approved publishing this integration on the separate `codex/streetcomplete-v64`
branch of `wielandb/StreetComplete`, and explicitly approved the iOS on-screen zoom fallback.
No SSH Mac is available. The user explicitly clarified that this separate branch should be
published and iOS CI executed. Pushing this branch enables the prepared Android and Mac CI
checks. No main/default/modified branch update is authorized.
This is not release-ready.

The detailed notes below include historical checkpoints; later verified results supersede earlier
pending statuses. The required integration checklist retains the complete original scope.
## Scope and source revisions

Integrate the complete stable StreetComplete 64.0 release into SCEE, preserving
the SCEE maintainer's functionality and porting it where upstream APIs changed.
This is an integration in progress, not a statement that the port works yet.

- SCEE starting point: `bb01e6c49e` (`scee/pre-streetcomplete-v64`).
- Last stable SCEE functionality reference: `v63.4`, commit
  `7afb57d6240879cf78a5d37a4e981bc71a01f386` (2026-08-05).
- Last common ancestor: `d4899bc45c788940b0abc0d18177deeddf1f2a9a` (2026-09-22).
- Upstream target: `6c88a654cad86a149583c7cb6b409e021f76b9c5`
  (`streetcomplete/v64.0`, released 2026-10-19).
- Integration branch: `codex/streetcomplete-v64`.
- 427 upstream commits are missing from the starting point. Compared with the
  common ancestor, upstream changes 1,752 files and SCEE changes 1,493 files.
- The user explicitly confirmed Android **and iOS**, including all SCEE extras.
  The existing SCEE build is Android-only. Common code must therefore lose its
  Android/JVM dependencies, with platform-specific integrations supplied on iOS.

## Required integration work

- [ ] Merge the complete upstream history and resolve each conflict using both
  upstream behavior and the original SCEE changes as evidence.
- [ ] Audit SCEE-only files and all overlapping upstream/SCEE changes, including
  files that merge automatically, for lost behavior and outdated APIs.
- [ ] Compare against stable SCEE 63.4 as well: the starting development revision
  already has incomplete Compose ports, including commented-out restriction
  overlay forms. Preserving only currently active development code is insufficient.
- [ ] Port SCEE's map interaction to the upstream Compose map and application
  shell, including navigation, quest/overlay selection, edit history, downloads,
  positioning, additional controls and map backgrounds.
- [ ] Preserve SCEE custom quests, external quests, quest selection, dynamic
  creation, extra answers, element filters, presets, resurvey settings and quest
  visibility/priority settings.
- [ ] Preserve tag editing, node creation/movement/deletion, additional element
  actions, custom and additional overlays, level filtering and geometry display.
- [ ] Preserve GPX/GeoJSON display, personal notes, custom quests from notes,
  photo handling, note filtering and nearby-quest notifications.
- [ ] Preserve preferences, import/export, recent answers, database migrations,
  upload semantics, additional permissions and the SCEE application identity.
- [ ] Include all upstream new and changed quests, map/UI fixes, performance
  improvements, data, resources, translations and dependency updates through
  the target release.
- [ ] Adapt SCEE dependencies to the upstream version catalog and source sets.
- [ ] Port every SCEE extra to shared code or provide Android and iOS adapters;
  audit platform imports, file handling, notifications and background operation.
- [ ] Repair CI build/artifact paths for the `androidApp` module and exercise
  the integration on pull requests / the integration branch as appropriate.
- [ ] Keep SCEE release metadata distinct from upstream release metadata.

The original `README.md` at `scee/pre-streetcomplete-v64` is the user-facing
feature checklist. The complete diff from the common ancestor to that revision
is the authoritative additional input for behavior not documented in the README.

## Verification required before completion

- [ ] The upstream release commit is an ancestor of the final integration commit.
- [x] No unresolved index entries or conflict markers remain.
- [x] All overlapping changes have been reviewed; removed SCEE functionality is
  either ported to its new location or documented as an upstream equivalent.
- [x] `:app:testAndroidHostTest` passes, including the applicable upstream tests
  and meaningful regression tests for changed SCEE behavior.
- [x] `:androidApp:assembleDebug -Papp.streetcomplete.debug=true` succeeds.
- [x] The release compilation/shrinking path is checked without requiring the
  maintainer's private signing keys.
- [x] Shared metadata compiles without Android/JVM dependencies.
- [x] Kotlin iOS device/simulator sources and simulator test sources compile.
- [ ] iOS framework/application builds and simulator/device execution run on macOS.
- [x] Representative Android runtime checks pass on an isolated Android 37/16-KB AVD.
- [ ] Runtime checks cover map startup/navigation, representative upstream and
  SCEE quests, settings/presets, tag editing, overlays and GPX/note workflows.
- [x] Existing user data/preferences remain usable; upgrade behavior is tested
  where schemas or stored representations change.

## Current environment and findings

- The first emulator run found the POI-label layout expression incompatibility above. The
  MapLibre specification requires camera-dependent layout/paint expressions to use an outer
  `step` or `interpolate`; the existing overlay-label layer already uses the valid `step` form.
  See https://maplibre.org/maplibre-style-spec/expressions/#camera-expressions.
- The dedicated emulator was shut down normally with `adb shell reboot -p` before rebuilding;
  the created preset survived restart and APK replacement. No OSM account was used, and no
  edits or notes were uploaded. OSM element/note edit queues both contain zero rows.
- Runtime evidence is in `.gradle/android-smoke/runtime-result.json`, screenshots/layouts in
  the same directory, and `.gradle/upgrade-test/migration-result.json`. The native compiler
  check uses `.gradle/upstream-volume-fallback-checks-17.log`; current Android tests and R8 use
  `.gradle/upstream-android-tests-18b.log` and `.gradle/upstream-release-final-checks-19.log`.
- The latest APK and cold-start follow-up are recorded in `.gradle/apk-verification-18.json`
  and `.gradle/android-smoke/startup-result-18.json`. Run 19 minifies current release code with
  `app.streetcomplete.debug=false`; its release DEX is 8,283,364 bytes.
- The iOS workflow has a branch-push trigger for `codex/streetcomplete-v64`, a PR trigger
  and manual dispatch. YAML/structure checks pass. No remote branch or CI run was created.
- The test Android user directory initially caused run 15 to use another debug key. Its
  runtime APK was re-signed with the original debug certificate, verified against the
  installed run-14 APK, so the preset/persistence check retained the data. The local build
  helper now uses the original Android user directory. The runtime variant's SHA-256 is
  `C89AD66405B7C2004724EBAE06DA2B2BED020B6155C2327A0A573DB4144BADCA`.
- Run 16's original-key Gradle APK has SHA-256
  `2D673100659F61866AA044208D26CD8FD28B8548AC51DB3907CD7784E6C94E5C`.
  Signature, 16-KB ZIP alignment and all eight 64-bit ELF libraries pass. Payload equivalence
  with the runtime APK was verified independently of signing metadata; see
  `.gradle/apk-payload-equivalence-16.json`.
- iOS map zoom uses on-screen controls. Public volume-button camera events require an active
  capture session; the map does not open a camera to intercept audio controls. The Android
  volume preference retains its existing behavior. Reference:
  https://developer.apple.com/documentation/avkit/avcaptureeventinteraction.
- The run-17 startup ANR trace is `.gradle/android-smoke/apk17-scee-anr-trace.txt`; its subject
  is a CleanerWorker job-start timeout, and the main stack contains the synchronous network
  Binder call. Several Google/system apps also timed out during this AVD boot. The observed
  app-side call is moved off the UI thread to keep delayed Binder replies from blocking UI work.
  References: https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs and
  https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flow-on.html.
- Upstream latest-release API checked again on 2026-10-30: stable `v64.0`, published
  2026-10-19; no newer stable target supersedes this integration.
- `updateStreetCompleteData --dry-run` passes (24 seconds). The aggregate no longer
  depends on SCEE's removed store-description updater; no data-update action was run.
- Run 13 release DEX/R8 output is current with the shared daylight code and removed
  JVM solar dependency. Its overall failure came only from the two native main tasks.
- The emulator start was deferred because only 1.5 GiB of physical RAM was available
  during R8. Builds and emulator runtime checks must stay sequential on this host;
  do not stop unrelated user processes to free memory.

- Native run 12 cleared the notification, toast and QR binding errors, then identified
  a missing iOS implementation of SCEE's daylight predicate. It is now shared code using
  solar altitude and civil twilight (-6 degrees), based on the USNO solar-coordinate and
  sidereal-time equations. It handles polar day/night without parsing absent event times,
  and removes the old Android Calendar month-index error and JVM-only solar dependency.
  Seven new common cases cover civil twilight, real months, polar conditions and the date line.
  These cases and both native targets are being checked in run 13.
- The Windows host's WHPX acceleration check passed. A separate Pixel 6 AVD under
  `.gradle/android-avd` uses Android 37.0, x86_64 and a 16-KB-page Google APIs system image.
  It has been created but not booted. No existing user AVD was changed.
- iOS workflow syntax and basic structure were parsed successfully with the cached
  YamlBeans Java library; this does not establish a successful native CI run.

- Android run 6 built and verified the debug APK. Production changes since then
  (safe smoothness highlighting and the upload-retry callback) need a fresh APK build.
- Android run 9 executed 73 selected tests: 71 passed. The final two failures were
  expectations for the old single-argument edit listener, now corrected. A full
  test run with the default release-mode build constant is next.
- `.github/workflows/ios-build.yml` prepares native simulator tests and a signing-free
  Swift application/framework build on `macos-26` (ARM64). It is not executed yet.
- iOS uses the existing on-screen zoom controls instead of a nonfunctional hardware-key
  option; import adaptation and four regression tests are included in run 17. Physical
  volume-key interception remains Android-specific. Apple's public capture-button API
  only delivers events during an active capture session. This UI adaptation still needs
  its iOS runtime check: https://developer.apple.com/documentation/avkit/avcaptureeventinteraction.

- Windows workspace: `E:/Dokumente/Programmieren/scee-update`.
- Apple framework/application builds cannot execute on this Windows host. A
  macOS validation environment is still needed; no iOS success is claimed.
- The upstream `buildSrc` requires Java 25. Temurin 25.0.5+8 was downloaded from
  Adoptium, its SHA-256 was verified against the official package metadata, and
  it was unpacked under `.gradle/jdks/temurin-25/jdk-25.0.5+8`.
- Gradle 9.7.1 is installed. The merged configuration check
  `gradlew.bat help --console=plain` passed (5m 57s), logged to
  `.gradle/upstream-configure.log`. This is not an app compilation or test run.
- `:app:dependencies --configuration androidCompileClasspath` passed in 56s,
  with no unresolved dependencies (`.gradle/upstream-dependencies.log`).
- `local.properties` (ignored) points to a project-local SDK under
  `.gradle/android-sdk`. SDK packages are build environment files, not source.
- The Android CLI names the API 37 platform `platforms/android-37.0`.
- API 37.0, Build Tools 37.0.0 and platform-tools are installed locally.
- Before the merge, the debug/release APK workflows still used the obsolete
  `app/build/outputs/apk` path instead of `androidApp/build/outputs/apk`.

Record actual commands, failures and successful verification here as the port
progresses. A successful textual merge or compilation alone does not establish
that all SCEE functionality has been preserved.

## Resolution notes

- Merge started against the complete v64.0 release; 58 paths initially have
  unresolved index entries. Deleted Android map components need behavioral
  ports, not wholesale deletion of their SCEE modifications.
- Step count: adopt the upstream removal of the `flat_steps` exclusion and keep
  SCEE's configurable maximum geometry length. Regression tests cover both.
- Shop type: preserve recognition/cleanup of `amenity=shop|shopping`, with the
  upstream private/no-access exclusion applied to both selection alternatives.
- Logs: use the new upstream snapshot/conflated flow interface with a consistent
  SCEE temporary-or-persistent source for initial reads and listener lifetime.
- Privacy screen: retain SCEE's ban-list URL with upstream asynchronous HTML
  rendering. Retain SCEE maintainer attribution alongside updated upstream credits.
- Keep the SCEE README and update `README_StreetComplete.md` from the target.
- Move SCEE dependency versions into the upstream catalog and restrict JitPack
  resolution to the GPX parser group. Preserve Android resource-copy bridges
  while their remaining consumers are being ported.
- APK workflows now use Java 25, explicit `androidApp` tasks and output paths.
  Unit-test CI also checks a debug APK and includes the SCEE/integration branches
  and pull requests.
- New iOS/desktop targets are present from upstream. Their SCEE compatibility is
  not yet established; enabling a target in Gradle is not evidence of a working
  port.
- Note forms retain GPX/OSM choice, full-size photo copies, close-note and
  temporary-hide actions in the new upstream form layout and map-click flow.
  The outside-downloaded-area confirmation now runs its automatic callback in
  a Compose effect, not during composition; pending note submissions are consumed
  once. The main form also guards against repeated submissions.
- Quest and overlay form transitions clear their map decorations through the
  upstream callbacks, including SCEE tag/access/construction actions. External
  quest forms now use the same map overlay and click APIs. Node insertion uses a
  map-anchored crosshair and geometry markers for connected ways.
- Bottom-sheet loading preserves dynamic and external quests and can restore
  add-POI/insert-node selections. Next-quest requests pass through the new sheet
  state and do not replace another form opened while an edit was being saved.
- Edit-history automatic selection still honors `SELECT_FIRST_EDIT`; an open
  sidebar can have no selected edit. SCEE's explicit history refresh remains.
- Survey-confirmation submissions now retain the SCEE edit context, extra-answer
  flag and external/dynamic quest key. Dynamic pin hiding happens after the edit
  is saved, including the confirmation path.
- Added `MainSheetStateTest` and `MainSheetSelectionTest` for these state changes.
  They have not run yet; source conflict resolution and compilation are pending.
- Kotlin PSI syntax checks passed for the first 21 ported form/state files and
  all 379 changed Kotlin files without unresolved markers at that point. This
  checks parsing only, using the locally cached Kotlin 2.4.0 parser; it is not
  compilation against the target Kotlin 2.4.20 dependencies.
- The new Compose map shell now exposes SCEE's add-POI/insert-node context-menu
  actions, recent POI choices, quick settings and queued downloads. The level
  picker consumes the map center directly. Legacy Activity/Fragment behavior
  still needs a complete audit/port before those files can be removed.
- Menu layouts retain SCEE's grid and preset options while using the upstream
  separate login/profile actions and overlay tutorial callback.
- Android startup retains early SCEE preferences, database-backup compatibility
  checks and memory logging, while using the upstream initializer/locale handler.
  Database schema 22 only retires two charging-capacity quests, so its unchanged
  backup table format is accepted by the import/export version guard. Full
  upgrade/import verification remains pending.
- The Android entry point now uses the upstream `ComponentActivity`/`App` shell.
  SCEE volume/menu key actions are routed to the active map, file import dialogs
  survive recreation through saved state, and the nearby-quest service binding
  follows the original background lifecycle. FileKit initialization supports
  `ComponentActivity`. iOS incoming-file handling and native runtime validation still
  need porting; this Android adapter is not cross-platform feature completion.
- Location sampling interval, optional solved animations and the setting that
  disables navigation mode are wired into the shared map screen.
- Sheet highlighting now includes external quests, complete selected ways,
  directions, localized tree names and nearby quests. It retains upstream's
  per-quest level-filter switch and SCEE's bridge exception. Nearby selection
  replaces the current sheet as in SCEE, and result state is scoped to that
  sheet so late computations cannot recolor a different sheet.
- Nearby-quest grouping uses actual keys rather than synthetic hash-derived
  element keys. New `QuestHighlightsTest` regressions cover grouping, collisions,
  color cycling and retention of marker metadata. Tests have not run yet.
- Contrast/night palettes and raster imagery URL templates are ported to the
  Compose map. Imagery and label settings update through observable preferences;
  hiding base-map labels leaves overlay labels visible. The legacy JSON style
  generator was removed; remaining legacy map components still need auditing.
  One-way arrows use the selected palette through the new SDF image API.
- The latest PSI syntax check passed for 391 changed/new Kotlin files. No
  application compilation, unit-test pass or device result is claimed yet.
- Quest-pin generation now includes external quest keys, POI dots/labels and
  optional quest geometries. Reverse order, day/night priority and user-defined
  downloaded-data retention are wired into the shared map sources. Long-way pins
  retained across viewport changes also receive updated priority values.
- The shared renderer now separates POI dots from clustered pins, retains their
  labels/colors, and renders colored/directional surrounding-element markers.
  Way-direction arrows and the pin-offset correction observe their preferences.
  `QuestPinKeysTest` covers all key types through a GeoJSON property round trip.
  The next syntax check passed for 395 Kotlin files, still without compilation.
- The unused `SHOW_3D_BUILDINGS` preference has no consumer in stable SCEE 63.4
  or the starting revision; the old JSON extrusion layer is commented out.
  This does not represent an active SCEE 3D-building feature being removed.
- GPX display/import now uses a shared XML parser built on upstream's xmlutil
  dependency, with tests for segments, namespaces and malformed coordinates.
  The Android-only GPX parser and its sole JitPack repository were removed.
- GPX and GeoJSON file imports validate before replacing a working file and
  notify shared map state, replacing Activity-resume flags. GeoJSON's picker
  accepts JSON/GeoJSON rather than GPX. The shared renderer preserves GPX segment
  gaps and displays custom geometry with its name labels. Downloading along a
  GPX processes its segments independently. These paths still need build/runtime
  verification; removed legacy fragments reference old helpers until their
  remaining behavior has been audited and they can be deleted.
- The first real `compileCommonMainKotlinMetadata` run reached the compiler and
  failed on remaining Android/JVM dependencies and changed APIs. Diagnostics are
  recorded in `.gradle/upstream-metadata-compile.log`; a passing syntax check did
  not prove platform compatibility. The second run finished with 204 diagnostics in 25 files (down from 545), still failing on remaining platform dependencies.
- SCEE quest/UI/display/note/data settings now have serializable Navigation 3
  routes and entries. The old settings Activity/NavHost were removed after their
  destinations were transferred to the shared navigation graph.
- `SnapshotSettings` retains primitive value types for cross-platform backups,
  observes new custom-overlay preference keys, and exposes existing Android
  preferences through a legacy snapshot. Global SCEE preferences are initialized
  by the shared Preferences instance, not an Android SharedPreferences field.
  Quest-name migration now writes the replacement before removing the old key.
  Backup serialization additionally supports doubles. Tests cover reopening,
  legacy Android values, numeric types, removal and listener lifetime; an isolated
  JVM run passed all five tests (0 failures/errors/skips, Kotlin 2.4.20). Full application tests are still pending.
- OSM DAOs now use upstream Kotlin serialization while retaining SCEE's tag-string
  sharing and query optimizations. String sharing uses the JVM pool on Android
  and a bounded, locked pool on native. Edit/cache locks and the temporary logger
  use common locks; the logger now notifies its existing live-log listeners.
- POI icon shortcuts, the extended crossing-marking chooser and copying an error
  report use common Compose resources/APIs. Android density conversion remains
  in the Android source set. No native UI runtime pass is claimed yet.
- Tag-editor suggestions and all four bundled tree catalogs are packaged as
  common resources. The suggestion generator writes to that shared location.
  The tag editor uses common date formatting, resource loading and back handling;
  its back button preserves the discard confirmation. Tree import accepts a
  PlatformFile, refreshes the catalog revision, and keeps file pickers on Main.
  Tree search normalization has Android/JVM and iOS adapters, which still require
  platform build verification. The incoming-file chooser on iOS remains pending.
- Additional legacy-map audit: offline cache cleanup already passes the SCEE
  retention time in days converted to milliseconds through the shared Cleaner.
  The new overlay source now applies SCEE level filtering on reload and updates; the existing level-change overlay notifications trigger reloads.
  MapLibre 0.20 exposes `TransformRotateBuilder.startAngle` and `allowDuringZoom`,
  which can carry the two remaining gesture preferences into the shared map.


- The remaining gesture preferences now use MapLibre's shared transform bindings:
  `allowDuringZoom` preserves the default false value and `startAngle` keeps the
  SCEE degree threshold (default 1.5). Both preferences update the map live.
- External-source quest listeners use the common locked listener collection.
  Restriction/custom-overlay/building colors no longer depend on Android APIs;
  selection colors retain the SCEE light/dark accent values. EVSE QR URL parsing
  uses Ktor and closes redirect HTTP clients after use.
- Brewery and cuisine suggestions are now common resources, loaded off Main.
  Construction dates use kotlinx-datetime and HTML help uses shared HtmlText.
  Upload ban-check warnings travel through the existing upload listener layer
  to the shared main-screen toast, preserving successful uploads.
- Dialog toasts have Android, iOS and desktop adapters. The iOS implementation
  attaches a non-interactive native view above presented dialogs. Native build,
  accessibility and device verification are still required.
- GPX track output now contains ISO timestamps and correctly closed track points,
  including the zero-elevation case. A shared writer and two regression tests
  cover XML escaping, coordinates and timestamps. These tests are not run yet.
  GPX note writes and exports share a mutex; FileKit directory access is lazy.
- ZIP exports use the streaming `no.synth:kmp-zip-kotlinx:2.0.0` adapter, which also
  supports ZIP64, instead of Java-only streams. File pickers stay on Main, and
  originals are removed only after the output archive has successfully closed.
  Source: https://github.com/henrik242/kmp-zip (MPL-2.0).
- Filter diffs use `io.github.petertrr:kotlin-multiplatform-diff:1.4.0`, the common
  port of java-diff-utils, retaining inline word highlighting and HTML escaping.
  Computation runs off Main. Source:
  https://github.com/petertrr/kotlin-multiplatform-diff (Apache-2.0).
- Gradle resolved both pinned libraries and their source archives successfully.
  An isolated JVM run is testing ZIP interoperability with java.util.zip, error
  propagation on input/output failure, filter diffs and the settings regressions.
  All 11 tests passed (5 settings, 3 diffs, 3 ZIP), including Java ZIP reader interoperability. Full app tests and both device builds remain pending.
- Additional platform audit fixed the Android nearby-monitor coordinate adapter
  (latitude was incorrectly copied from longitude) and made the iOS appearance
  switch cover SCEE's high-contrast dark theme. The iOS toast uses Compose 1.12's
  `uikit.LocalUIViewController` API. Native compilation remains outstanding.
### QR scanning, background monitor and download queue

- The third common metadata compile reduced diagnostics from 204 in 25 files to
  54 in two Android-bound forms. The QR form and monitor settings now use shared
  interfaces; a fourth compiler run is pending.
- QR scanning preserves Android's external scanner contract and adds an iOS
  AVFoundation camera preview with cancellation, camera authorization, one-shot
  results and session cleanup. Native compilation and camera/device tests are
  still required. Camera denial and unavailable hardware have distinct messages.
- Both the monitor row and switch now request the required native permissions.
  Android requests foreground access first, explains the background option on
  Android 11+, then checks notification permission. Radius must be finite and
  positive. iOS uses foreground authorization for a session started before
  backgrounding, as documented by Apple, and requests notification authorization.
- The iOS monitor starts location updates before backgrounding, scans only while
  backgrounded, clears its notification on return, and opens the closest quest
  through the existing geo URI route. It keeps the existing radius, accuracy,
  visibility, data-retention and optional-download behavior. iOS selects its own
  location providers; its accuracy control replaces Android's separate GPS and
  network-provider controls. Location callbacks, notification response routing
  and background energy use still require device validation.
- Downloads now append through Android WorkManager or the shared serial queue
  used by iOS/desktop. Explicit replacement cancels the entire old queue. GPX
  segment downloads use the controller on every platform instead of an Android
  worker's in-memory list. Four targeted tests cover ordering, keeping existing
  work, replacing running/queued work and continuing after a failed job. All 15 isolated tests pass, including the four queue cases. iOS expiration cancels queued work as well as the active job.

Platform references:
- https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services
- https://developer.android.com/develop/sensors-and-location/location/permissions/background
- https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications
- https://developer.apple.com/documentation/avfoundation/avcapturemetadataoutput

- Legacy map review found and restored three additional SCEE behaviors: hidden
  external quests now produce history pins that round-trip the source and issue
  ID correctly; point overlays retain their custom icon colors; restricted roads
  retain dark access dashes even when an overlay covers the background map.
  Five regression tests were added; these require the full app test build and
  have not run yet. Their obsolete Android implementations are removed after this review; copies remain in the ignored audit folder and the pre-merge backup ref.

- The fourth common metadata compile completed successfully (`BUILD SUCCESSFUL`,
  17m 57s). It covers the QR/permission interfaces and the source snapshot read by
  that compiler invocation. Subsequent map changes still need the Android build.
- The final 13 legacy map conflicts are resolved by removing the replaced
  Android classes. Shared sources/layers cover their preferences, geometry,
  rendering and event paths. Native Android color resources remain available.
  The obsolete `Quest.pins` model cache is removed; `MapQuestPinsSource` owns the
  shared cache. This does not complete the stable-SCEE feature audit.
- The iOS notification delegate is retained from Swift app initialization so
  notification links also reach the shared URI holder on a cold start. This
  follows Apple's requirement to assign the delegate before launch finishes:
  https://developer.apple.com/documentation/usernotifications/unusernotificationcenter/delegate

### Remaining runtime blockers found in the stable-feature audit

- `RestrictionOverlay.Form` still contains the pre-existing alpha TODO. Both
  restriction editor implementations remain block-commented legacy fragments.
  These must be ported before the restriction overlay can be considered usable:
  stop/give-way node placement and direction, turn relation selection/editing,
  weight limits, conditional rules, exceptions, deletion and relation details.
  Shared map callbacks and `OverlayForm` are available for this migration.
- The common metadata success is a compilation milestone, not evidence that all
  SCEE features are restored. The stable 63.4 behavior comparison and full
  Android/iOS verification are still open.

### Restriction sign editor port in progress

The previously commented Android node form now uses the shared Compose overlay form, map snapping,
markers, direction selection and common node-edit actions. Give-way signs are not offered for junction
vertices; stop signs placed at junctions use the all-way encoding. Converting an existing all-way stop
back to an ordinary stop now removes `stop=all` (the old implementation retained it). Seven common
regression tests cover the sign-tag transitions and legacy encodings; these new tests have not yet
run. The turn/weight form still requires porting and its `Way` route remains unfinished. The node
changes were written before the running Android build reached Kotlin compilation; build success
has not yet been established.

### Turn and weight editor: first shared implementation

The `Way` route now opens a Compose editor instead of throwing `NotImplementedError`. The editor
loads existing turn/weight restrictions, creates turn relations by selecting a connected road,
allows role swapping on new relations, edits signed/implicit status, vehicle classes, exceptions
and conditions, and uses the upstream country-specific weight-sign input. It supports conditional-
only weight limits, relation details/raw tags, confirmed deletion and relation-specific notes.
Restriction draft state is serializable. Missing relation members are displayed without dereferencing
missing elements. Relations without a via member are no longer classified as supported.

Overlay edit actions now optionally carry the edited geometry and raw-tag-edit flag; relation notes
carry the target element and position through the container. This keeps history pins and note context
attached to the edited relation. The node form also supplies its snapped position. Conditional input
validation now accepts underscores and decimal/unit values, required for turn and weight conditions.
Nine pure tag-operation regression tests were added; the sixth isolated test run is pending. Seven
node-sign tests and full form compilation/runtime checks are still pending. This is an implementation
checkpoint, not confirmation that the restriction editor is ready for release. The ongoing first
Android build had already generated shared string accessors before four editor strings were added;
its source/resource snapshot is therefore not sufficient to validate these additions.

The common test preference helper now uses `MapSettings`, which supplies getters, setters and
listeners on every target. Obsolete Android `SharedPreferences` setup was removed from the card and
crossing tests; step-count, sheet selection and note-controller tests set real preferences instead
of stubbing a non-mock `Preferences` instance. These common tests still await the full test build.
Conditional input validation was extracted with four new regression tests for turn names, decimal
weights/units, incomplete conditions and the 255-character boundary; these four tests are also pending.
The sixth isolated run failed in the sandbox while fetching Gradle (TLS socket aborted), before any
test was executed. The seventh run retries with access to the already used host Gradle cache.

### iOS application identity

The Xcode product and display name are now SCEE, with the Android-aligned bundle identifier
`de.westnordost.streetcomplete.expert` and version `64.0` / `6405`. Product references and the shared
scheme use `SCEE.app`; the Kotlin framework remains `StreetComplete`. Background cleanup uses the
matching SCEE identifier in both code and Info.plist. The upstream developer team was removed;
local signing must use the builder's own `TEAM_ID`. English/German camera permission descriptions
include QR scanning. The plist and scheme XML and the localized string-catalog JSON were parsed
successfully. Xcode/native build, signing, the iOS app icons and incoming file-URL import still need
verification/work. The inherited OAuth URL scheme has not been changed.

### External document import on Android and iOS

A shared `IncomingFileImportDialog` now imports GPX display tracks, GeoJSON geometry, custom quests
and tree data through the existing common importers. GPX/geometry imports enable the corresponding
map layer. Failed imports remain in the dialog for retry; coroutine cancellation is propagated.
Android routes `content:`/`file:` intents to this dialog and declares the relevant text/XML/JSON/GPX
MIME types. URI/deep-link handling remains separate in `MainNavHost`.

The iOS bridge copies incoming file URLs into a UUID directory under the app's temporary storage.
It acquires/relinquishes security-scoped access and coordinates the read before passing a private
path to Kotlin. Copies are queued and removed after import/cancellation, with cleanup restricted to
the copier's own directory structure. Copy failures show an import error. GPX, GeoJSON and text
file types are declared in Info.plist. This follows Apple's [security-scoped URL documentation](https://developer.apple.com/documentation/Foundation/NSURL)
and [external document access guidance](https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/DocumentPickerProgrammingGuide/AccessingDocuments/AccessingDocuments.html).
The Android/iOS manifest XML parses successfully; native/device import tests are still required.

### Further SCEE preservation fixes

- Osmose multi-element highlighting now uses shared geometry markers, including the existing shared
  way-direction layer. Form state is keyed by quest, the element selection is serializable, and tag
  editing can use the quest geometry when a relation geometry is unavailable. This restores behavior
  confirmed in stable SCEE 63.4; runtime verification is pending.
- Tag suggestions parse/intern both bundled JSON datasets before filling their caches. Cancellation
  is rethrown, allowing a cancelled or failed load to be retried without a half-filled cache.
- GPX note tracks store positive finite horizontal accuracy in a SCEE extension with explicit meter
  units, independently of elevation. The local note-track writer no longer labels meter accuracy
  as HDOP. Two regression cases were added (four GPX-writer tests total), still pending execution.
  The extension placement follows the [GPX 1.1 schema](https://www.topografix.com/GPX/1/1/).
  Newly created GPX note files include the required version/creator attributes. The separate upstream
  `TracksSerializer` upload path has not been changed by this correction.
- Legacy Android settings imports decode the raw Double bits for `map.latitude`, `map.longitude`,
  `map.rotation2`, `map.tilt2` and `map.zoom2`. These names were checked in stable SCEE 63.4 and the
  current preferences; the encoding was checked against the actual 1.3.0 Android settings sources.
  Other Long values stay Long. Three regression cases cover camera values, ordinary longs and a
  corrupt batch; execution is pending.

### iOS icon assets

The iOS light/dark/tinted PNG assets now use the existing SCEE vector artwork rather than the
StreetComplete icon. All three rendered files are 1024x1024 RGB with no alpha channel. All three variants were visually inspected. Regeneration is optional and does not affect app builds:

```sh
npm install --prefix .gradle/icon-tools sharp
node "res/graphics/app icon/render_ios_icons.cjs" .gradle/icon-tools/node_modules/sharp
```

### Current verification checkpoint

The first Android compile failed before Kotlin source compilation because the Google Maven TLS
handshake failed while downloading `androidx.lifecycle:lifecycle-common-jvm:2.11.0`. The exact JAR
was subsequently fetched from Google's official repository with the Windows HTTP client, matched
against Google's SHA-1 checksum and placed in the corresponding Gradle cache location. The first
build therefore does not validate the new source changes. A subsequent Android build is required.

The seventh isolated test run was deliberately stopped before producing any test output after the
host had only 650-830 MB of free physical memory; it is not a test failure result. Builds/tests now
run sequentially. The eighth isolated run includes 31 tests (the prior 15, nine restriction-tag
cases, four conditional-input cases and three legacy-settings cases) and is still in progress.
An available Mac/Xcode host has been requested for native iOS validation; no iOS build success has
been established. The integration and stable-feature audit remain incomplete.

The eighth isolated run has now **passed all 31 tests**. JUnit XML confirms seven suites, 31 tests,
zero failures and zero errors (`.gradle/shared-port-tests-8-summary.json`). This validates the listed
shared utility/tag/settings logic; it does not validate the Compose forms or native platform code.
The GPX-writer, restriction-sign and map regression cases still require the full project test build.

The first Android process subsequently also reported that its Gradle daemon disappeared; its daemon
log showed severe host memory pressure. It is terminal. The second Android compile is running offline
against the augmented cache, with two workers, a 2 GiB Gradle heap and a 3 GiB Kotlin daemon heap.
No success for that build has yet been established.

The full staged whitespace check found inherited trailing spaces in upstream documents/resources
and extra blank EOF lines in a few Kotlin files. Details are recorded in the local review directory;
cleanup is deferred until the current Android compile finishes so its source inputs stay stable.
These findings do not represent a compilation result.

This worktree's integration code and diagnostics were produced with OpenAI Codex assistance in
response to the user's complete Android/iOS port request. No upstream contribution or maintainer
message has been submitted. Any future contribution must follow the repository's `AI_POLICY.md`.

### Stable feature audit and Android dependency follow-up

The verified overlap against the selected **stable** upstream tag contains 220 paths. The older
222-path inventory was made against upstream master. The stable upstream delta remains 1,752
paths. The local audit now keeps the stable overlap separately to avoid mixing these scopes.
All 64 active SCEE-specific `EE_QUEST_OFFSET` registrations from the original SCEE delta remain
verbatim with their IDs. All 121 overlapping store-metadata paths retain the SCEE input blob or
deletion; no upstream marketing image/text has replaced the SCEE choice in the index.

Source review also confirmed the additional building types/categories/icons and overlay colors,
database tables/migrations, configurable data retention, automatic-download switch, generic
settings access, extended quest actions, menus, GPX note buttons, background switching, feature
icon shortcuts and SCEE information pages. This is source-level review, not runtime validation.
The hardware menu shortcut is already attached at the `MainMenuDialog` call site in `MainScreen`.

Two issues found during the stable-63.4 comparison were corrected:

- `AddSidewalk` again advertises its settings and reads the per-preset highway selection when
  creating its filter. The Compose development revision retained the dialog but had lost both
  connections. Three new tests cover excluding unselected roads, including service roads and
  selecting the correct preset. These project tests have not run yet.
- Wheelchair description edits are now carried by an immutable `WheelchairAccessAnswer`, rather
  than a mutable field on shared enum constants. The form creates an answer-local snapshot and
  resets its state when the element changes. All four description-capable quests consume it.
  The separate toilets-inside-a-place form, which also lacked a description editor in stable
  63.4, no longer reads another answer's stale description. Four new project tests cover language
  preservation/deletion and cross-element isolation; they have not run yet.

Android run 2 failed in offline artifact resolution despite the manually copied, checksum-verified
JAR. A direct Java HEAD request to the official Google artifact URL failed with the default Java
trust store, but succeeded with HTTP 200 using `Windows-ROOT` and `trustStore=NONE`. Run 3 uses
that verified Windows trust configuration in its Gradle JVM, with certificate validation enabled,
two workers and bounded heaps. This is a local invocation setting, not a repository change or a
TLS-validation bypass. The orphaned daemon from the completed failed run 1 was stopped only after
its PID, executable, command line, creation date and failure log were checked. No current or
unrelated build was stopped. Native iOS validation and the complete project test suite remain
pending.

Every path in the 220-path stable overlap now has a source/metadata choice or a mapped shared-port
review entry in the local audit. The SCEE-only/stable-feature audit and runtime validation remain
open. The data-update aggregate also no longer depends on the upstream store-description task
that SCEE removed; its dry-run is pending. Seven extra EOF blank lines introduced in modified
Kotlin files were removed after run 3 ended. Inherited upstream whitespace findings remain.

The preference-consumer scan found two further controls already disconnected in stable 63.4.
Node and vertex creation now pass `CREATE_NODE_SHOW_KEYBOARD` into the common feature dialog;
turning it off avoids initial focus/automatic keyboard display. Other search dialogs retain their
upstream default. `SEARCH_MORE_LANGUAGES` extends feature search with country/subdivision official
languages, preserving the upstream app/system locale order, English and unlocalized fallback.
Three common language-selection regression cases were added and remain pending.

Invocation 4 failed during task selection because PowerShell split the unquoted dotted `-P`
argument; it did not compile code or run tests. Invocation 5 quotes the property and requests both
`:app:testAndroidHostTest` and `:androidApp:assembleDebug`, with one worker and the verified trust
configuration. No successful Android build/test result is claimed yet.

Run 5 completed `compileAndroidMain`, produced the shared library JARs and continued through
Android resource/manifest and native-library processing. It failed at two later compiler tasks:

- Test registry construction needed the SCEE external-quest dependencies and initialized
  preferences; step-count test geometry needed its new explicit center. The sheet state tests
  now use small ViewModel doubles because Lifecycle's inherited final bookkeeping methods cannot
  be mocked by Mokkery.
- The Android entry module now declares FileKit Core and Compose Resources directly for APIs it
  consumes. Its quest monitor uses the shared `Preferences` facade and public resource API, plus
  Android's standard Toast API. Notification/toast text now uses the shared locale-aware resources.

Run 6 requests the same complete tests/APK tasks after these fixes. The application library was
not changed by the fixture/entry-module repairs. The added-Kotlin-file inventory contains 177
SCEE additions: 173 remain at their original path; the four removed paths are the old map-style
creator/styles, custom-geometry component and pin component, whose behavior moved to shared map
styles/layers/models. This presence check complements rather than completes the behavioral audit.
