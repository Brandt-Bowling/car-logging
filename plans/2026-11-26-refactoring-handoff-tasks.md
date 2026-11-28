# Refactoring Handoff Task List

Source of truth: [`plans/2026-11-24-android-architecture-refactoring.md`](2026-11-24-android-architecture-refactoring.md).
Before each task, read its **Global Constraints** and the matching **Task** section.

## Rules for the executing agent

- Branch: `refactor/android-architecture-modularization`. Never commit to `main`.
- Work directly, one task at a time. Do not use multi-agent orchestration (budget constraint).
- After **each** task, run `./gradlew testDebugUnitTest lintDebug assembleDebug`. CI
  (`.github/workflows/ci.yml`) already runs `testDebugUnitTest lintDebug` on PRs, so never skip lint.
  Then commit only the files you touched (never `git add -A` / `git add .`). Use small commits, one per
  extracted collaborator or file, so a failing step is easy to bisect and revert.
- Refactor-only rule: each decomposition commit is a pure move/extract with no behavior changes.
  The only intended behavior changes are the 2 confirmed fixes (`MaintenanceRecordDialog` delete
  in Task 9, `generativeai` SDK removal in Task 13). Commit those separately with their own tests.
- Before extracting logic that has no tests, add characterization tests on the original code first,
  then extract.
- PR cadence: open a PR for user review at each phase boundary instead of one giant PR at the end.
  The boundaries are after item 3 (Phase 1A), item 10 (Phase 1B), item 14 (Phase 2), and item 17 (Phase 3).
  Ask the user whether to merge each one before continuing.
- The agent cannot run the app on a device. Before each PR, list the affected screens for the user's
  manual smoke test (see the source plan's final checklist). This matters most for UI decompositions
  (items 8–14), which have no UI tests.
- Never change Room schema v6, DataStore preference key strings, or ADR 0001/0002 behavior.
- Never delete or weaken an existing test assertion. When splitting large suites, move tests; don't drop them.
- On a fresh clone or worktree, run `./scripts/setup_worktree.sh` first (see `AGENTS.md`).
- If you see untracked `.agents/`, `PROJECT.md`, `TEST_INFRA.md`, or `e2e/`, they are leftovers from an
  earlier multi-agent run. Keep them out of commits.

## Status

- [x] **Task 1, part 1 (`M1a`)**: `di/AppContainer.kt` (single shared `OkHttpClient`; the only
      `OkHttpClient.Builder(` in `app/src/main`), `data/local/UriContentReader.kt`,
      `services/SyncSchedulerController.kt`, `CarLoggerApplication.container`, all 3 workers resolve
      dependencies from the container (with a fallback for test contexts), JVM test seams on
      `GoogleDriveService` (internal Play-Services-free ctor) and `VehicleImageRepository`
      (`internal constructor(imagesDir, uriContentReader, carDao)`). Test helper:
      `app/src/test/kotlin/android/net/TestUri.kt` (`testUri("content://...")`). 195/195 unit tests passing.

## Tasks (in order)

### 1. Finish Task 1: plain ViewModels + `di/AppViewModelFactories.kt`

- [x] Convert `HomeViewModel`, `VehicleDetailsViewModel`, and `AddCarViewModel` to plain `ViewModel` with
      constructor-injected dependencies. `HomeViewModel` should use `UriContentReader` in place of the 4
      `getApplication<Application>().contentResolver` sites (~L265–352). No ViewModel calls the schedulers
      today. `SettingsTab` calls `DailyPollScheduler` / `ChargerSyncScheduler` / `WorkChargingSyncScheduler`
      directly from Composables (~L818–1640), so `SyncSchedulerController` is first used by
      `SettingsViewModel` in Task 5 (item 8).
- [x] Create `di/AppViewModelFactories.kt`. Every `viewModel(...)` call in `MainActivity` gets an explicit `factory`.
- [x] Unit test the factories. `AppContainer(ContextWrapper(null))` is safe on pure JVM because everything is lazy.
- [x] Verify: `rg -n "AndroidViewModel" app/src/main/kotlin` returns nothing.
- Gotchas:
  - Implement factories as `object : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T }`.
    `InitializerViewModelFactory` throws `UnsupportedOperationException` on the 1-arg `create(Class)`.
  - In `MainActivity`, write `private val appContainer get() = (application as CarLoggerApplication).container`
    as a getter. `application` is null during `<init>`.
  - `val addCarViewModel: AddCarViewModel = viewModel()` (~L159) will crash at runtime once
    `AddCarViewModel` no longer has an `(Application)` ctor. Pass the factory.
  - `VehicleContainerTransformHost`'s `SessionViewModelStoreOwner` supplies empty `CreationExtras`,
    so factories must not depend on extras.
  - Keep the public `preferencesManager` / `googleDriveService` / `odometerSyncService` vals on
    `HomeViewModel`. `HomeScreen` (~L352) and `MainActivity` (~L137/205) read them until Tasks 5 and 12.
  - Leave the `VehicleDetailsViewModel.syncDriveFolder(...)` signature and `MaintenanceRecordDialog.onDelete`
    unchanged here. Tasks 12 and 9 handle them.

### 2. Task 2: `collectAsStateWithLifecycle` + `StateFlow` encapsulation

- [x] Replace all 79 `collectAsState(` calls (`MainActivity` 1, `HomeScreen` 14, `SettingsTab` 37,
      `VehicleDetailsScreen` 14, `GloveboxTab` 1, `AddCarModalSheet` 12). Import
      `androidx.lifecycle.compose.collectAsStateWithLifecycle` and remove the stale
      `androidx.compose.runtime.collectAsState` import.
- [x] Encapsulate the 22 public `MutableStateFlow`s (`HomeViewModel` 6, `VehicleDetailsViewModel` 4,
      `AddCarViewModel` 12) as `private val _x` + `val x: StateFlow<T> = _x.asStateFlow()`. Add intent
      setters, and update the 6 `.value =` writes in `AddCarModalSheet.kt`.
- [x] Replace the 3 redundant `.combine(currentTab)` calls (`cars`, `allRecords`, `hasUrgentService`) in
      `HomeViewModel` with `.map`.
- [x] Verify: `rg -n "collectAsState\(" app/src/main/kotlin` returns nothing.

### 3. Task 3: `ExpressiveThemeTokens`

- [x] Follow Task 3 in the plan. Hex values (light / dark):
      savingsContainer `0xFFD3F5DC` / `0xFF0F4020`, savingsAccent `0xFF0B5F2A` / `0xFF81E6A1`,
      savingsText `0xFF072711` / `0xFFC6F7D4`, workIconContainer `0xFFDFF7E8` / `0xFF0A2E1A`,
      workIconTint `0xFF15803D` / `0xFF4ADE80`.

### 4–17. Remaining decompositions and gates

For each item: follow the matching Task in the source plan, verify, then commit.

- [x] 4. **Task 11**: `PreferencesManager` → 4 stores in `data/local/prefs/` (facade keeps every `KEY_*`/`DEFAULT_*`).
- [x] 5. **Task 4**: `OdometerSyncService` → `TelemetryModels.kt` + `services/telemetry/*`; split
      `OdometerSyncServiceTest`. This is the largest task. Spread it over several commits: models first,
      then one collaborator at a time.
- [x] 6. **Task 7**: `InsightsCalculationEngine` → pure calculators; split `EvElectricityCostAnalysisTest`.
- [x] 7. **Task 13**: `GeminiAiService` → `services/ai/*`; remove the `generativeai` SDK (Gradle, version catalog, ProGuard).
- [x] 8. **Task 5**: `SettingsTab` → `ui/screens/home/tabs/settings/*` + `SettingsViewModel`.
- [x] 9. **Task 6**: `VehicleInsightsBentoGrid` → `components/bento/*`.
- [x] 10. **Task 8**: `EvElectricityCostCard` → `insights/electricity/*`.
- [ ] 11. **Task 9**: `HomeViewModel` split + `MaintenanceRecordDialog` delete fix.
- [ ] 12. **Task 10**: `ChargerUsageReviewSheet` / `UtilityBillReviewSheet` → `insights/review/*`.
- [ ] 13. **Task 12**: `VehicleDetailsScreen` / ViewModel split + `DriveReceiptSyncUseCase`.
- [ ] 14. **Task 14**: `InsightsTab` + `InsightsActions` + previews.
- [ ] 15. **Task 15**: `scripts/check_architecture_gates.sh` + `checkArchitectureGates` Gradle task
      (includes the 120-line `@Composable` cap).
- [ ] 16. **Task 16**: Lint hardening + Slack `compose-lint-checks`.
- [ ] 17. **Task 17**: CI (`testDebugUnitTest lintDebug checkArchitectureGates`) + `AGENTS.md` guardrails.
      **Detekt was dropped (2026-11-26) as redundant with Tasks 15/16. Do not add it.**
- Advisory: `HomeViewModel` (12 deps) and `VehicleDetailsViewModel` (13) have long constructors. Consider
  grouping their DAOs during Tasks 9/12 (guidance only, not enforced).

### 18. Final

- [ ] Run the source plan's **Final Verification Checklist**, then open a PR (`gh pr create`) for user review.
