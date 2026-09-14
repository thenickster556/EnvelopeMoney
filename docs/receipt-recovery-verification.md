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
