# Receipt recovery verification — 2026-09-10

## Delivered behavior
Existing receipt references automatically recover from the exact `Pictures/Mountain Money` folder, without an age cutoff or recovery picture picker. The resolver reads metadata even if the original stream fails, normalizes media-image document IDs, and supplements the MediaStore inventory with accessible unindexed files. Opening an app-owned picture does not imply library access: a partial owned-only MediaStore list is treated as restricted, and launch/resume requests `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` when stored receipts exist so yesterday and older album files can be found. Folder matching accepts `RELATIVE_PATH` with or without a trailing slash. Unique filenames win; an unnamed leftover may take the only unused album file whose capture local day matches `Transaction.date`. Two unused files that day stay ambiguous. Only unique, decoded matches are persisted. Current/history records are repaired together, with concurrent-edit checks; filename metadata survives transfer mirroring and split edits. Tapping preview tries the stored URI first; if that read fails, the same matcher can persist then open. Gallery I/O for background repair runs off the main thread.

A receipt whose original identifier is lost is not zip-ordered against other same-day files. Missing, ambiguous, inaccessible and corrupt outcomes preserve the original reference and expose automatic retry. Recovery never deletes, moves, imports or overwrites an image; explicit rotation saving retains its existing write behavior.

## Protocol evidence
- Read the protocol, repository memory, schema and receipt flows before edits. Recorded intent/precheck in README and task state, then revised them when automatic recovery replaced manual selection.
- Preserved a pre-change tracked-source snapshot outside the repository for baseline comparison. Existing receipt URI restoration work remains incorporated; no web or financial-calculation changes were introduced.
- Added failing tests before implementation, including yesterday/same-day recovery, unavailable streams with readable metadata, unindexed folder files, metadata copying, and a candidate changing identity during the scan.
- Downloaded a temporary JDK 11 rather than changing the production Gradle/Android plugin versions. Updated Android test dependencies and enabled Robolectric/JaCoCo integration to run meaningful Android-provider tests.

## Automated results
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Full `:app:testDebugUnitTest`: **195 tests, 192 passed, 3 pre-existing failures**. All new recovery and photo-access tests pass.
- Shared recovery JaCoCo scope: `ReceiptAlbumMatcher`, `ReceiptReferenceResolver`, `ReceiptReferenceRepair`, and `AndroidReceiptSource`, including their nested types. **368/377 lines (97.61%) and 314/370 branches (84.86%)**. `:app:verifyReceiptRecoveryCoverage` passes both 80% gates. This is recovery-code coverage, not a claim of whole-app coverage.
- Android-provider tests cover API 28 and 33, plus an API 22 runtime-permission check. They verify preview decoding and rotation targeting as well as folder matching.
- `:app:lintDebug`: **43 existing errors**, with identical error IDs/messages in the pre-change snapshot. The old Android plugin also reports incompatible dependency lint/Kotlin metadata; no new lint errors were introduced.
- `git diff --check`: passed.

### Existing failures reproduced before this change
1. `MonthRolloverHelperTest.prepareForLaunch_recoversMalformedNumericState` — Gson rejects non-finite numeric fixture values.
2. `PondLookupTest.findByName_trimmedInputAndStoredName` — existing trim-matching assertion.
3. `ReceiptSourceDeleterTest.isMediaStoreImagesUri_trueForMediaContent` — existing media-document URI heuristic assertion.

The baseline run had additional receipt-test failures because Android APIs were run against unimplemented JVM stubs. Relevant receipt tests now use Robolectric. Existing OCR parser tests run in the unit suite; the repository's real-camera/OCR instrumentation placeholder remains ignored and is not reported as passed.

## Device and visual verification
The final APK passed the API 36 startup-backfill, restart-persistence and rendered-preview test on a clean disposable emulator. The automatic-retry dialog test encountered a window-focus failure while an emulator System UI ANR was present. Its Espresso matcher was corrected to target the dialog explicitly, and the test APK rebuilt successfully. The final rerun was blocked by automatic approval review because the account usage limit was reached; this UI check is not reported as passed. Captured preview screenshots were obscured by System UI and are not considered clean visual approval. The API 28 emulator did not boot successfully; API 28 provider behavior is covered with Robolectric. No physical-phone or real-camera verification is claimed.

## Reproducing checks
Use JDK 11 with the repository wrapper and an installed Android SDK:

```text
gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --continue
gradlew :app:receiptRecoveryCoverage :app:verifyReceiptRecoveryCoverage -x :app:testDebugUnitTest
gradlew :app:lintDebug
```

The second command consumes the completed unit-test execution data; it does not suppress or turn the three baseline test failures into passes. Reports are under `app/build/reports/tests`, `app/build/reports/jacoco/receiptRecoveryCoverage`, and `app/build/reports/lint-results-debug.html`. Run `ReceiptFolderRecoveryInstrumentedTest` with AndroidJUnitRunner on a disposable emulator for startup/restart and automatic-retry UI checks.
### Visual behavior and remaining check
Before this change, a broken reference could fail to open even while the picture remained in the gallery. After recovery, the existing zoom/rotation viewer renders the verified image; unresolved associations receive a themed automatic-search retry dialog. No fixed sizing was added. The dialog uses text-labelled Material buttons and the existing viewer keeps its existing gesture controls. A clean screenshot and the corrected retry-dialog instrumentation rerun remain required to finish visual acceptance.

---

# Matching widened for renamed/copied photos — 2026-09-14

## Root cause found
Renamed or re-copied album files could never recover: the same-day match returned by `ReceiptAlbumMatcher` was rejected in `ReceiptReferenceRepair.resolveAll` whenever the album filename differed from the stale stored name. Because the exact-filename pass had already consumed every claim whose exact name existed, every claim reaching the date pass necessarily had a different name — the veto turned all of them into MISSING. Contributing causes fixed alongside: exact case-sensitive filename compare (`.JPG`, `.jpeg`, percent-encoding, ` (1)` copy suffixes), case-sensitive `RELATIVE_PATH` folder equality, no adjacent-day tolerance for backdated entries, and no Android 14 "Select photos" partial-access support. The whole pipeline also logged nothing, so on-device failures were invisible.

## Delivered behavior (delta)
- Identity tiers bind without date evidence, each requiring a unique candidate: exact filename → normalized filename (case, `.jpeg`→`.jpg`, percent-decode, Windows ` (n)` copy suffix stripped) → shared epoch token (the ≥10-digit run inside `MountainMoney_{epochMs}.jpg`, so renames like `backup 1726150469234 copy.jpg` still match).
- The claim-name veto is replaced by a listing-vs-inspection race guard: a date match with a renamed album file persists and updates `receiptImageFileName`; only a candidate whose name changed between album listing and decode inspection is dropped.
- Date matching: the unique same-day pair rule is unchanged; a lone claim whose day holds no unused file retries one day either side. Two files in a window, or two lone claims contesting one adjacent file, are both ambiguous.
- `Pictures/Mountain Money` folder equality is path-casing-insensitive; an empty granted MediaStore index triggers one best-effort `MediaScannerConnection` rescan before the disk supplement is logged.
- Android 14 partial photo access (`READ_MEDIA_VISUAL_USER_SELECTED`, now declared) is restricted-but-listable: identity matches resolve, date matching stays off, and prompting stops. The permission reports denied on older platforms, so no version gate is needed.
- Album inventory size and restricted state now log under the `EnvelopeMoney` tag on each repair pass.

## Automated results
- New tests written first and confirmed failing (compile-level for the new helper APIs, assertion-level for the veto and permission behavior), then implemented.
- Full `:app:testDebugUnitTest`: **240 tests, 237 passed, same 3 pre-existing failures** (list above, reproduced before the change: 221 tests / 3 failures baseline).
- Recovery JaCoCo scope (matcher, resolver, repair, Android source): **518/534 lines (97.00%) and 434/516 branches (84.11%)**; `:app:verifyReceiptRecoveryCoverage` passes both 80% gates.
- `:app:lintDebug`: **43 existing errors** — unchanged count and files; the new manifest permission and receipt edits produced no new lint findings.
- `:app:assembleDebug`: passed.
- Robolectric cannot sandbox SDK 34 on the JDK 11 this toolchain requires (Gradle 6.7.1), so the partial-access logic is verified at SDK 33 using the permission grant directly; the Android 14 system dialog behavior is not exercised in JVM tests.

## On-device check (recommended)
Install the debug APK with renamed copies (e.g. `IMG_x.jpg`, `MountainMoney_... (1).jpg`) in `Pictures/Mountain Money`, tap a receipt whose stored pointer is dead, and watch `adb logcat -s EnvelopeMoney` for the album inventory line ("receipt album: N pictures ... restricted=..."). Expected: renamed unique files repair and open; ties show the retry dialog; a "Select photos" grant on Android 14 stops the prompt while still resolving identity matches. No physical-phone verification is claimed in this record.

---

# Ambiguity picker + sub-second search — 2026-09-14

## Delivered behavior (delta)
- AMBIGUOUS no longer dead-ends. The matcher attaches the competing files (`Result.alternatives`, populated for identity collisions, same-day multi-file, and adjacent-window windows/contests); the tap flow re-verifies each candidate, drops unreadable ones, auto-applies a lone survivor, and otherwise shows a picker dialog (`dialog_receipt_chooser.xml` rows: thumbnail via `ReceiptBitmapLoader.decodeSampled(…,128)` on the recovery executor, filename, `MMM d, yyyy · h:mm a` capture time). A pick is applied through the same guarded `applyReceiptResolution`, persisted, toasted ("Receipt picture updated."), and previewed; Cancel changes nothing. Session picks are remembered so two rows cannot take one picture. Restricted access still converts ambiguity to PERMISSION_REQUIRED without alternatives.
- Search speed: `inspect` verifies with one stream open + header decode instead of two opens plus a full sampled bitmap decode (the previous second decode doubled every pass with no extra corruption signal — the bounds pass already fails garbage input, which the corrupt test guards). The tap path resolves only `receiptEntriesFor(reference)`; the whole-app sweep remains in the startup repair. Each pass logs `receipt repair: N entries in Xms` and the album line now includes its duration, so any remaining slowness is attributable on-device.

## Automated results
- Tests first: alternatives attached per ambiguity kind, resolveAll pass-through, restricted strips alternatives, and a provider `opens` counter proving `inspect` opens the stream exactly once — all red before implementation (compile-level for the new field, assertion-level for the counter), green after.
- Full `:app:testDebugUnitTest`: **248 tests, 245 passed, same 3 pre-existing failures** (baseline before this change: 240/3).
- Recovery JaCoCo scope: **518/533 lines (97.19%) and 435/514 branches (84.63%)**; `:app:verifyReceiptRecoveryCoverage` passes both 80% gates.
- `:app:lintDebug`: **43 existing errors** — unchanged; the new chooser layouts only add a pre-existing-pattern Overdraw warning.
- `:app:assembleDebug`: passed.
- The chooser dialog itself (Material views, thumbnail pop-in) has no JVM test harness for `MainActivity`; it is verified by compile, resource lint, and the on-device checklist below — consistent with how prior MainActivity UI work was recorded.

## On-device check (recommended, not yet run)
Rename two copies of one receipt into `Pictures/Mountain Money`, tap the dead reference, and expect the picker within a second (logcat: `receipt repair: N entries in Xms`, `receipt album: … (Yms)`). Pick each candidate once; verify the toast, preview, and that the second ambiguous row's picker no longer offers the already-picked file. Cancel must change nothing.
