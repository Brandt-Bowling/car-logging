# Android Architecture Modernization, Large-File Refactoring & Modularity Gates Plan

> **For agentic workers:** Execute this plan task-by-task on a dedicated feature branch or git worktree (never directly on `main`). Steps use checkbox (`- [ ]`) syntax for tracking. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` after every task before committing.

**Goal:** Align **Garage Journal** (`com.example.car_logger`) with official [Android Architecture Recommendations](https://developer.android.com/topic/architecture/recommendations) and [Jetpack Compose UI Architecture](https://developer.android.com/develop/ui/compose/architecture) best practices, eliminate all >1,000-line production files (Phase 1) and 700–950-line second-tier files (Phase 2) targeting **< 450 lines per production Kotlin file**, and install automated lint rules and CI gates (Phase 3) so future features stay small, modular, and architecture-compliant.

**Architecture:**
- **Manual DI via `AppContainer`** owned by `CarLoggerApplication` (zero new Gradle plugins/dependencies): shares a single `OkHttpClient`, `AppDatabase`, `PreferencesManager`, `OdometerSyncService`, `GeminiAiService`, `GoogleDriveService`, `VehicleImageRepository`, and `AutoDevService` across ViewModels and WorkManager workers, removing `AndroidViewModel`.
- **Lifecycle-aware UDF**: Migrate all 79 `collectAsState()` calls to `collectAsStateWithLifecycle()`, encapsulate all public `MutableStateFlow`s, replace manual `LifecycleEventObserver` with `LifecycleResumeEffect`, introduce `SettingsViewModel` + `SettingsUiState`, and extract plain Compose state holders for complex bottom sheets.
- **Modular decomposition behind stable facades**: Split `OdometerSyncService` (4,020 lines) into focused collaborators under `services/telemetry/`, `InsightsCalculationEngine` (1,267 lines) into pure calculators under `data/insights/`, `PreferencesManager` (790 lines) into domain preference stores under `data/local/prefs/`, and `GeminiAiService` (720 lines) into focused services under `services/ai/` while removing the deprecated `com.google.ai.client.generativeai` SDK.
- **Preventive lint & CI gates (Phase 3)**: Three-layer enforcement combining a fast file-size & architecture check (`checkArchitectureGates`), hardened Android Lint + `com.slack.lint.compose:compose-lint-checks:1.6.1`, and CI / `AGENTS.md` guardrails. (Detekt was considered and dropped as redundant.)

**Tech Stack:** Kotlin 2.2.20, AGP 9.1.1 (built-in Kotlin), Jetpack Compose (BOM 2026.09.00, Material 3 Expressive 1.5.0-rc01), Lifecycle Runtime Compose, Room v6, DataStore Preferences, OkHttp 4, Kotlinx Serialization, WorkManager 2.10, JUnit4, Kotlinx Coroutines Test.

**Related ADRs & Rules:**
- `docs/adr/0001-variable-blended-utility-rate-and-historical-bill-matching.md`
- `docs/adr/0002-hybrid-vehicle-attribution-for-shared-ev-chargers.md`
- `AGENTS.md` (worktree setup via `./scripts/setup_worktree.sh`; verify with `./gradlew testDebugUnitTest assembleDebug`)
- `.github/workflows/ci.yml` (currently runs `./gradlew testDebugUnitTest lintDebug` on PRs & pushes to `main`)

---

## Global Constraints

1. **Git Isolation (Never Directly on `main`)**:
   - All work must happen on `refactor/android-architecture-modularization` (or a dedicated `git worktree` branched from it).
   - If using a new `git worktree`, run `./scripts/setup_worktree.sh` first so gitignored `app/google-services.json` and `local.properties` are copied before invoking `./gradlew`.
2. **Zero Data or Schema Breaking Changes**:
   - **Room schema v6** (`app/src/main/kotlin/com/example/car_logger/data/local/AppDatabase.kt`) must remain untouched.
   - **All DataStore preference key strings** in `PreferencesManager.kt` must remain byte-for-byte identical.
   - **ADR 0001** (variable blended utility rate & chronological bill matching) and **ADR 0002** (hybrid shared EV charger attribution) behavior must remain strictly identical.
3. **Zero Weakened Tests**:
   - Every existing assertion across all 22 unit test suites must continue to pass (migrated cleanly when giant test suites are split by domain).
4. **Confirmed Phase 2 Fixes Included**:
   - **Fix `MaintenanceRecordDialog` deletion** in `MainActivity.kt` by wiring `onDelete` to `homeViewModel.deleteMaintenanceRecord(recordId)`.
   - **Remove deprecated `com.google.ai.client.generativeai` SDK** by migrating `parseReceipt` onto `GeminiHttpClient.executeMultimodalJsonRequest` (also unlocking PDF receipt parsing without `BitmapFactory` memory overhead) and cleaning up `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `app/proguard-rules.pro`.

---

## Target Architecture Diagram

```mermaid
flowchart TD
    App["CarLoggerApplication"] --> Container["AppContainer (Manual DI)"]
    Container --> Http["Shared OkHttpClient"]
    Container --> DB["AppDatabase (Room v6)"]
    Container --> Prefs["PreferencesManager + 4 Domain Stores"]
    Container --> Telemetry["OdometerSyncService (~240L Facade)"]
    Container --> AI["GeminiAiService (~130L Facade)"]
    Container --> Drive["GoogleDriveService"]
    Container --> ImgRepo["VehicleImageRepository"]
    Container --> AutoDev["AutoDevService"]
    Container --> UriReader["UriContentReader"]
    Container --> Sched["SyncSchedulerController"]

    Telemetry --> TAuth["TeslaAuthClient / SmartcarAuthClient"]
    Telemetry --> TOdo["TeslaOdometerSyncer / SmartcarOdometerSyncer"]
    Telemetry --> TChg["WallConnector / Work / Supercharger / SmartcarDc Syncers"]

    Container --> Workers["WorkManager Workers (DailyOdometer, DailyCharger, WorkCharging)"]
    Container --> VMF["AppViewModelFactories"]

    VMF --> HomeVM["HomeViewModel (Slim)"]
    VMF --> SettingsVM["SettingsViewModel (New)"]
    VMF --> DetailsVM["VehicleDetailsViewModel"]
    VMF --> AddCarVM["AddCarViewModel"]

    HomeVM --> InsightsEngine["InsightsCalculationEngine + Pure Calculators"]
    HomeVM --> HomeUI["HomeScreen & Tabs (UDF + collectAsStateWithLifecycle)"]
    SettingsVM --> SettingsUI["SettingsTab + Section Composables + Dialogs"]
    DetailsVM --> DetailsUI["VehicleDetailsScreen + Bento Modules + Dialogs"]
```

---

## Target File Size Summary

| Source File | Baseline Lines | Phase / Task | Target Max Lines / File |
|---|---:|---|---:|
| `services/OdometerSyncService.kt` | 4,020 | 1B / Task 4 | ~240 (facade) + 11 modules (150–380 each) |
| `ui/screens/home/tabs/SettingsTab.kt` | 2,731 | 1B / Task 5 | ~190 (tab) + ViewModel + 8 sub-files (120–300 each) |
| `test/.../OdometerSyncServiceTest.kt` | 2,314 | 1B / Task 4 | 7 domain test files + fixtures (200–450 each) |
| `ui/screens/vehicledetails/components/VehicleInsightsBentoGrid.kt` | 1,462 | 1B / Task 6 | ~170 (grid) + 5 tile/drawer files (130–320 each) |
| `data/insights/InsightsCalculationEngine.kt` | 1,267 | 1B / Task 7 | ~260 (engine) + 4 pure calculators (80–420 each) |
| `test/.../EvElectricityCostAnalysisTest.kt` | 1,231 | 1B / Task 7 | 3 focused test suites (250–400 each) |
| `ui/screens/home/tabs/insights/EvElectricityCostCard.kt` | 1,187 | 1B / Task 8 | ~190 (card) + 4 component files (110–350 each) |
| `ui/screens/home/tabs/insights/ChargerUsageReviewSheet.kt` | 946 | 2 / Task 10 | ~210 (sheet) + state holder + 2 content files |
| `ui/screens/home/tabs/insights/UtilityBillReviewSheet.kt` | 847 | 2 / Task 10 | ~280 (sheet) + state holder + breakdown card |
| `ui/screens/home/HomeViewModel.kt` | 822 | 2 / Task 9 | ~260 (VM) + 3 coordinators/calculators |
| `data/local/PreferencesManager.kt` | 790 | 2 / Task 11 | ~200 (facade) + 4 domain stores |
| `ui/screens/vehicledetails/VehicleDetailsScreen.kt` | 721 | 2 / Task 12 | ~280 (screen) + extracted dialogs |
| `services/GeminiAiService.kt` | 720 | 2 / Task 13 | ~130 (facade) + 4 AI modules (SDK removed) |
| `ui/screens/home/tabs/InsightsTab.kt` | 704 | 2 / Task 14 | ~280 (tab) + `InsightsActions` + header controls |

---

## Phase 1A: Cross-Cutting Android Architecture Foundations

### Task 1: Manual DI (`AppContainer`) & Remove `AndroidViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/di/AppContainer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/di/AppViewModelFactories.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/local/UriContentReader.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/SyncSchedulerController.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/CarLoggerApplication.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/AutoDevService.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/DailyOdometerSyncWorker.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/DailyChargerSyncWorker.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/WorkChargingSyncWorker.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsViewModel.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/addcar/AddCarViewModel.kt`

**Steps:**
- [x] Create `UriContentReader` (interface + `AndroidUriContentReader(appContext)`) moving `resolveFileName`, `resolveMimeType`, and `readBytes` out of `HomeViewModel`.
- [x] Create `SyncSchedulerController` (interface + `AndroidSyncSchedulerController(appContext)`) wrapping `DailyPollScheduler`, `ChargerSyncScheduler`, and `WorkChargingSyncScheduler` so ViewModels never hold a `Context`.
- [x] Update `AutoDevService` constructor to accept `client: OkHttpClient = defaultClient` so it shares the container's `OkHttpClient`.
- [x] Create `AppContainer(appContext: Context)` lazily instantiating one shared `OkHttpClient`, `AppDatabase`, `PreferencesManager`, `GoogleDriveService`, `VehicleImageRepository`, `AutoDevService`, `GeminiAiService`, `OdometerSyncService`, `UriContentReader`, and `SyncSchedulerController`.
- [x] Expose `lateinit var container: AppContainer` on `CarLoggerApplication` (keeping `database` and `preferencesManager` delegating to `container`).
- [x] Update all 3 WorkManager workers to pull shared dependencies from `(applicationContext as? CarLoggerApplication)?.container` with safe fallback for `TestListenableWorkerBuilder`.
- [x] Convert `HomeViewModel`, `VehicleDetailsViewModel`, and `AddCarViewModel` from `AndroidViewModel` to plain `ViewModel` with constructor-injected dependencies, wired through `AppViewModelFactories` in `MainActivity`.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug` and `rg -n "AndroidViewModel" app/src/main/kotlin` returns zero matches.

---

### Task 2: Lifecycle-Aware State Collection & ViewModel UDF Encapsulation

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsScreen.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsViewModel.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/tabs/GloveboxTab.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/addcar/AddCarModalSheet.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/addcar/AddCarViewModel.kt`

**Steps:**
- [x] Replace all **79** `collectAsState(...)` calls across the 6 UI files with `androidx.lifecycle.compose.collectAsStateWithLifecycle(...)`.
- [x] Encapsulate all public `MutableStateFlow` properties in `HomeViewModel`, `VehicleDetailsViewModel`, and `AddCarViewModel` behind private `_field` + public `val field: StateFlow<T> = _field.asStateFlow()` with explicit intent setter methods where needed (e.g., `AddCarViewModel` form field setters).
- [x] Remove redundant `.combine(currentTab)` on `cars`, `allRecords`, and `hasUrgentService` in `HomeViewModel.kt` (replace with `.map { ... }`).
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug` and `rg -n "collectAsState\(" app/src/main/kotlin` returns zero matches.

---

### Task 3: Centralize Duplicated Semantic Theme Tokens

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/theme/Color.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/GasComparisonCard.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/EvElectricityCostCard.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/PeriodChargingLogsSheet.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/ChargerUsageReviewSheet.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleInsightsBentoGrid.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt`

**Steps:**
- [x] Add light/dark savings & workplace green constants to `Color.kt` (`SavingsContainerLight`/`Dark`, `SavingsAccentLight`/`Dark`, `SavingsTextLight`/`Dark`, `WorkIconContainerLight`/`Dark`, `WorkIconTintLight`/`Dark`).
- [x] Expose `@Composable @ReadOnlyComposable` semantic properties on `ExpressiveThemeTokens` in `Theme.kt`.
- [x] Replace the 6 hardcoded `isSystemInDarkTheme()` hex color blocks across the UI files with `ExpressiveThemeTokens.*`.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

## Phase 1B: Decomposing All 5 Production Files Over 1,000 Lines

### Task 4: Decompose `OdometerSyncService.kt` (4,020 Lines) & Split `OdometerSyncServiceTest.kt` (2,314 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/services/TelemetryModels.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TelemetryJsonUtils.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TeslaAuthClient.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/SmartcarAuthClient.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TeslaOdometerSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/SmartcarOdometerSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/WallConnectorParser.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/WallConnectorSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TeslaWorkChargingSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TeslaSuperchargerSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/SmartcarDcChargingSyncer.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/telemetry/TelemetryStatusFormatters.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/OdometerSyncService.kt` (4,020 → ~240 lines facade)
- Replace `app/src/test/kotlin/com/example/car_logger/OdometerSyncServiceTest.kt` (2,314 lines) with:
  - `app/src/test/kotlin/com/example/car_logger/telemetry/TelemetryTestFixtures.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/OdometerSyncCoreTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/TeslaOAuthAndOdometerTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/SmartcarOAuthAndOdometerTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/WallConnectorSyncTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/TeslaWorkChargingSyncTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/TeslaSuperchargerSyncTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/telemetry/SmartcarDcChargingSyncTest.kt`

**Steps:**
- [x] Extract all public data classes and sealed result hierarchies (lines 60–223) into `services/TelemetryModels.kt` (keeping `package com.example.car_logger.services` so no callers break).
- [x] Extract JSON helpers, JWT parsers (`parseJwtClaims`, `extractJwtExpiryMs`, `extractJwtScopes`, `hasTeslaLocationScope`, `hasTeslaChargingHistoryScope`), flexible timestamp/duration parsers, `haversineMeters`, cooldown math, `formatRelativeTime`, `shouldRecordOdometerLog`, `resolveProviderForCar`, and `recordOdometerReading` into `services/telemetry/TelemetryJsonUtils.kt`.
- [x] Extract `TeslaAuthClient` and `SmartcarAuthClient` (OAuth URLs, credentials, code exchange, token refresh, authenticated HTTP helpers, error parsers).
- [x] Extract `TeslaOdometerSyncer` and `SmartcarOdometerSyncer`.
- [x] Extract `WallConnectorParser` and `WallConnectorSyncer`.
- [x] Extract `TeslaWorkChargingSyncer`, `TeslaSuperchargerSyncer`, `SmartcarDcChargingSyncer`, and `TelemetryStatusFormatters`.
- [x] Slim `OdometerSyncService.kt` down to a ~240-line facade composing the collaborators with the injected `OkHttpClient`, owning `isSyncing`/`lastSyncMessage`, and keeping delegating `companion object` helpers for backwards compatibility.
- [x] Split `OdometerSyncServiceTest.kt` into `TelemetryTestFixtures.kt` + 7 domain test files with zero dropped test cases.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug` and check every new telemetry file is < 400 lines.

---

### Task 5: Decompose `SettingsTab.kt` (2,731 Lines) & Introduce `SettingsViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsUiState.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsSyncFormatters.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsCommonComponents.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsCredentialDialogs.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/SettingsSyncDialogs.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/VehicleSyncSettingsSection.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/HomeChargingSettingsSection.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/WorkChargingSettingsSection.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/PublicChargingSettingsSection.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/settings/GeneralBackupApiSettingsSections.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt` (2,731 → ~190 lines)
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/di/AppViewModelFactories.kt`
- Modify: `app/src/test/kotlin/com/example/car_logger/SettingsSyncFormattingTest.kt`
- Create: `app/src/test/kotlin/com/example/car_logger/SettingsViewModelTest.kt`

**Steps:**
- [x] Extract formatting helpers into `SettingsSyncFormatters.kt` and update imports in `SettingsSyncFormattingTest.kt`.
- [x] Create immutable `SettingsUiState` (sub-states per section + sealed `SettingsDialogState` replacing 11 dialog booleans) and `SettingsViewModel` (injecting `preferencesManager`, `googleDriveService`, `odometerSyncService`, `syncSchedulerController` — zero `Context` held).
- [x] Extract shared UI primitives (`SettingsSectionHeader`, `SettingsGroupCard`, `SettingsRowItem`, `SettingsItemDivider`, `DashboardLinkButton`, `WallConnectorAssignmentTile`) and a reusable `SettingsTimePickerDialog` into `SettingsCommonComponents.kt` (removing duplicated `VibrantTimePickerDialog` code).
- [x] Extract credential dialogs into `SettingsCredentialDialogs.kt` and wake/scope/time-picker dialogs into `SettingsSyncDialogs.kt`.
- [x] Extract the 5 stateless section composable files (`VehicleSyncSettingsSection`, `HomeChargingSettingsSection`, `WorkChargingSettingsSection`, `PublicChargingSettingsSection`, `GeneralBackupApiSettingsSections`).
- [x] Rewrite `SettingsTab.kt` (~190 lines) to collect `SettingsViewModel.uiState` with `collectAsStateWithLifecycle()`, replace `DisposableEffect` + `LifecycleEventObserver` with `LifecycleResumeEffect`, host the Google Sign-In & OAuth browser launchers, render sections in `LazyColumn`, and render the active `SettingsDialogState`.
- [x] Add `SettingsViewModelTest.kt` covering preference updates, WorkManager scheduler delegation, and state production.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 6: Decompose `VehicleInsightsBentoGrid.kt` (1,462 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/bento/BentoSharedControls.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/bento/DrivingPaceBento.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/bento/EvEfficiencyBento.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/bento/SavedVsGasBento.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/bento/TrueCostPerMileBento.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleInsightsBentoGrid.kt` (1,462 → ~170 lines)

**Steps:**
- [x] Extract `InDrawerPeriodConnectedToggle` and shared styling helpers into `BentoSharedControls.kt`.
- [x] Co-locate each bento tile with its drawer composable (`DrivingPaceBento`, `EvEfficiencyBento`, `SavedVsGasBento`, `TrueCostPerMileBento`), consuming `ExpressiveThemeTokens` for savings colors.
- [x] Slim `VehicleInsightsBentoGrid.kt` to the 2×2 grid + docked `AnimatedVisibility` / `AnimatedContent` drawer shell.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 7: Decompose `InsightsCalculationEngine.kt` (1,267 Lines) & Split `EvElectricityCostAnalysisTest.kt` (1,231 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/InsightsDateIntervals.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/UtilityRateResolver.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/ElectricityCostCalculator.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/FuelSavingsCalculator.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/data/insights/InsightsCalculationEngine.kt` (1,267 → ~260 lines)
- Replace `app/src/test/kotlin/com/example/car_logger/EvElectricityCostAnalysisTest.kt` (1,231 lines) with:
  - `app/src/test/kotlin/com/example/car_logger/insights/InsightsTestFixtures.kt`
  - `app/src/test/kotlin/com/example/car_logger/insights/UtilityRateMatchingTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/insights/SharedEvAttributionAndProrationTest.kt`
  - `app/src/test/kotlin/com/example/car_logger/insights/MultiVenueChargingAndSavingsTest.kt`

**Steps:**
- [x] Extract `DateInterval`, `generateIntervals`, `formatObservationWindowText`, `normalizeSegmentBounds`, and `calculateMilesInWindow` into `InsightsDateIntervals.kt`.
- [x] Extract ADR 0001 bill matching (`ResolvedBillRate`, `resolveRateForTimestamp`, `buildHomeRateSourceLabel`) into `UtilityRateResolver.kt`.
- [x] Extract ADR 0002 & 3-venue cost calculation into `ElectricityCostCalculator.kt`, breaking the 520-line `computeElectricityCost` body into clean private sub-functions (`computeSharedAttributionWeights`, `sliceSummarySession`, `sliceSingleSession`, `aggregateVenueMetrics`, `combineVenueTotals`).
- [x] Extract `computeNetFuelSavings` into `FuelSavingsCalculator.kt`.
- [x] Slim `InsightsCalculationEngine.kt` to ~260 lines while preserving exact `computeUiState(...)` and `formatObservationWindowText(...)` public APIs.
- [x] Split `EvElectricityCostAnalysisTest.kt` into the 3 domain suites + shared fixtures with zero dropped assertions.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 8: Decompose `EvElectricityCostCard.kt` (1,187 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/electricity/VenueSegmentedSelector.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/electricity/ElectricityVenueContent.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/electricity/InlineRateEditorRow.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/electricity/ExpressiveChargingSplitButton.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/EvElectricityCostCard.kt` (1,187 → ~190 lines)

**Steps:**
- [x] Extract `VenueConnectedButtonGroup` into `VenueSegmentedSelector.kt`.
- [x] Extract `EmptyVenueState`, `PopulatedVenueContent`, `VehicleElectricitySubCard`, and `formatKwh` into `ElectricityVenueContent.kt` (split `EmptyVenueState` out if file exceeds ~380 lines).
- [x] Extract `RateChipsRow` + plain state holder `InlineRateEditorState` into `InlineRateEditorRow.kt`.
- [x] Extract `ExpressiveChargingSplitButton` into `ExpressiveChargingSplitButton.kt`.
- [x] Slim `EvElectricityCostCard.kt` to the card header, parsing progress banner, animated venue switcher, and split button wiring.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

## Phase 2: Second-Tier Large Files (700–950 Lines) & Confirmed Fixes

### Task 9: Decompose `HomeViewModel.kt` (822 Lines) & Fix `MaintenanceRecordDialog` Deletion

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/ScheduledServiceCalculator.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/ElectricityImportController.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeTelemetryController.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeViewModel.kt` (822 → ~260 lines)
- Modify: `app/src/main/kotlin/com/example/car_logger/MainActivity.kt`
- Create: `app/src/test/kotlin/com/example/car_logger/ScheduledServiceCalculatorTest.kt`

**Steps:**
- [x] Extract EV tire rotation vs. ICE oil-change service due math into pure `ScheduledServiceCalculator.kt` and add `ScheduledServiceCalculatorTest.kt`.
- [x] Extract utility bill & charger file parsing, persistence, undo-batch, deletion, and rate updates into `ElectricityImportController.kt`.
- [x] Extract OAuth post-connect sync & manual car/Wall Connector/work/public charging sync orchestration into `HomeTelemetryController.kt`.
- [x] Add `deleteMaintenanceRecord(recordId: String)` (calling `recordDao.deleteRecordById(recordId)`) to `HomeViewModel` and wire `MaintenanceRecordDialog`'s `onDelete` callback in `MainActivity.kt` (lines 190–193) to call `homeViewModel.deleteMaintenanceRecord(recordId)` before closing the dialog.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 10: Decompose `ChargerUsageReviewSheet.kt` (946 Lines) & `UtilityBillReviewSheet.kt` (847 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/review/ChargerUsageReviewState.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/review/ChargerImportReviewContent.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/review/ChargerManualEntryContent.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/ChargerUsageReviewSheet.kt` (946 → ~210 lines)
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/review/UtilityBillReviewState.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/review/UtilityBillBreakdownCard.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/UtilityBillReviewSheet.kt` (847 → ~280 lines)

**Steps:**
- [x] Extract `ChargerUsageReviewState` (`rememberChargerUsageReviewState` + `buildEntitiesFromDrafts`), `ChargerImportReviewContent`, and `ChargerManualEntryContent` (including `VehicleAssignmentGroup` and `ManualModeConnectedGroup`).
- [x] Slim `ChargerUsageReviewSheet.kt` to a ~210-line `ModalBottomSheet` shell.
- [x] Extract `UtilityBillReviewState` (`rememberUtilityBillReviewState` + live rate & overlap state) and `UtilityBillBreakdownCard` (`BillBreakdownCard` + `breakdownCategoryLabel`).
- [x] Slim `UtilityBillReviewSheet.kt` to a ~280-line `ModalBottomSheet` shell.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 11: Decompose `PreferencesManager.kt` (790 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/data/local/prefs/GeneralPreferencesStore.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/local/prefs/TelemetryPreferencesStore.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/local/prefs/ChargingSyncPreferencesStore.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/local/prefs/PricingPreferencesStore.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/data/local/PreferencesManager.kt` (790 → ~200 lines facade)

**Steps:**
- [x] Split preferences into the 4 cohesive stores sharing the single `DataStore<Preferences>` instance with **identical** key strings and defaults.
- [x] Keep `PreferencesManager` as a delegating facade preserving every existing public flow, suspend getter/setter, and `Companion` constant so all callers and `PreferencesManagerDailyPollTest` continue passing unchanged.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 12: Decompose `VehicleDetailsScreen.kt` (721 Lines) & `VehicleDetailsViewModel.kt` (464 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/dialogs/VehicleDetailsDialogs.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/data/insights/VehicleCostHistoryCalculator.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/DriveReceiptSyncUseCase.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsScreen.kt` (721 → ~280 lines)
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsViewModel.kt` (464 → ~260 lines)
- Modify: `app/src/main/kotlin/com/example/car_logger/MainActivity.kt`
- Create: `app/src/test/kotlin/com/example/car_logger/VehicleCostHistoryCalculatorTest.kt`

**Steps:**
- [x] Extract all 6 inline `AlertDialog`s from `VehicleDetailsScreen.kt` into `VehicleDetailsDialogs.kt`.
- [x] Extract lifetime & yearly cost-per-mile calculation into `VehicleCostHistoryCalculator.kt` and add `VehicleCostHistoryCalculatorTest.kt`.
- [x] Extract Drive folder receipt syncing into `DriveReceiptSyncUseCase.kt` (using the injected `GoogleDriveService`, `GeminiAiService`, `PreferencesManager`, and DAOs — removing the duplicate `GeminiAiService` instantiation and removing `googleDriveService`/`preferencesManager` parameters drilled through `MainActivity` → `VehicleDetailsScreen` → `VehicleDetailsViewModel`).
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

### Task 13: Decompose `GeminiAiService.kt` (720 Lines) & Remove Deprecated `generativeai` SDK

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/services/ai/GeminiHttpClient.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/ai/ReceiptParserService.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/ai/VehicleImageGeneratorService.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/ai/ElectricityAiParserService.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/services/GeminiAiService.kt` (720 → ~130 lines facade)
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/kotlin/com/example/car_logger/ReceiptParserServiceTest.kt`

**Steps:**
- [x] Extract shared OkHttp REST transport (`executeMultimodalJsonRequest`, `executeImageRequest`, error parsing) into `GeminiHttpClient.kt`.
- [x] Extract `ReceiptParserService.kt`, migrating `parseWithGemini` from `com.google.ai.client.generativeai.GenerativeModel` + `BitmapFactory` onto `GeminiHttpClient.executeMultimodalJsonRequest` with `inlineData` (supporting both image and PDF receipts cleanly), plus pure `parseReceiptJsonResponse`.
- [x] Extract `VehicleImageGeneratorService.kt` and `ElectricityAiParserService.kt`.
- [x] Slim `GeminiAiService.kt` down to a ~130-line delegating facade preserving all existing public/`internal` methods used by tests.
- [x] Remove `implementation(libs.google.generativeai)` from `app/build.gradle.kts`, `googleGenerativeAi` & `google-generativeai` from `gradle/libs.versions.toml`, and `-keep class com.google.ai.client.generativeai.** { *; }` from `app/proguard-rules.pro`.
- [x] Add `ReceiptParserServiceTest.kt` testing REST JSON parsing, markdown fence stripping, and filename fallback.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug` and `rg -n "generativeai" app/ gradle/` returns zero matches.

---

### Task 14: Decompose `InsightsTab.kt` (704 Lines)

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/InsightsActions.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/insights/InsightsHeaderControls.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/InsightsTab.kt` (704 → ~280 lines)
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeScreen.kt`

**Steps:**
- [x] Create `@Immutable data class InsightsActions(...)` grouping the ~25 callback lambdas passed from `HomeScreen` to `InsightsTab`.
- [x] Extract `VehicleFilterConnectedButtonGroup` and `CompactElectricityPeriodBar` into `InsightsHeaderControls.kt`.
- [x] Update `InsightsTab.kt` and `HomeScreen.kt` to pass `InsightsActions`.
- [x] Verify: `./gradlew testDebugUnitTest assembleDebug`.

---

## Phase 3: Automated Lint Rules & Modularity Gates (Preventing Future Bloat)

To ensure new features cannot grow back into 1,000+ line files or reintroduce architecture anti-patterns, install a **3-layer gate stack** (architecture gate script, hardened Android Lint + Slack Compose rules, CI / `AGENTS.md` guardrails) wired directly into Gradle and `.github/workflows/ci.yml`.

### Task 15: Architecture & File-Size Gate Script + Gradle Task (`checkArchitectureGates`)

**Files:**
- Create: `scripts/check_architecture_gates.sh`
- Create: `config/architecture-gates-allowlist.txt` (empty by default; documents temporary exceptions only with PR rationale)
- Modify: `build.gradle.kts` (register `checkArchitectureGates` Gradle `Exec` task and attach to `check`)

**Enforced Hard Limits & Architectural Invariants:**
1. **Production Kotlin file size ceiling**: Fail build if any file under `app/src/main/kotlin/**/*.kt` exceeds **450 lines** (warn at **350 lines**). *(Note: also check any remaining 500–650 line files like `VehicleContainerTransformHost.kt`, `GarageTab.kt`, `PeriodChargingLogsSheet.kt`, `ManageElectricityDataSheet.kt`, `DrivingActivityCard.kt`, `VehiclePhotoCustomizerSheet.kt` — either split them or record their exact current line counts as non-growing watermarks in `config/architecture-gates-allowlist.txt` so they can shrink but never grow!)*
2. **Test Kotlin file size ceiling**: Fail build if any file under `app/src/test/kotlin/**/*.kt` exceeds **650 lines**.
3. **Lifecycle-aware Flow collection**: Fail if `\.collectAsState\(` appears anywhere in `app/src/main/kotlin/`.
4. **No `AndroidViewModel`**: Fail if `AndroidViewModel` appears anywhere in `app/src/main/kotlin/`.
5. **UDF ViewModel encapsulation**: Fail if public `val <name> = MutableStateFlow(` appears under `app/src/main/kotlin/.../ui/`.
6. **Single shared `OkHttpClient`**: Fail if `OkHttpClient.Builder()` appears in `app/src/main/kotlin/` outside `di/AppContainer.kt` (except optional constructor defaults for tests, or enforce injection-only in production code).
7. **No legacy Gemini SDK**: Fail if `com.google.ai.client.generativeai` appears in `app/` or `gradle/`.
8. **Centralized theme tokens**: Fail if duplicated savings/work hex colors (`0xFF0F4020`, `0xFFD3F5DC`, `0xFF81E6A1`, `0xFF0B5F2A`, `0xFFC6F7D4`, `0xFF072711`) appear outside `ui/theme/`.
9. **Composable length cap**: Fail if any `@Composable` function under `app/src/main/kotlin/` exceeds **120 lines** (legacy offenders recorded as non-growing watermarks in the allowlist).

**Steps:**
- [ ] Write `scripts/check_architecture_gates.sh` (POSIX/Bash, `chmod +x`, prints clear file path + line count + remediation guidance on failure).
- [ ] Add `config/architecture-gates-allowlist.txt` supporting per-file max-line ratchet entries (`path/to/File.kt=MAX_LINES`) for any untouched 450–650 line legacy files so they can never grow by even 1 line while every new/refactored file adheres strictly to `<= 450` lines.
- [ ] Register `tasks.register<Exec>("checkArchitectureGates")` in root `build.gradle.kts`.
- [ ] Verify: `./gradlew checkArchitectureGates`.

---

### Task 16: Harden Android Lint (`lintDebug`) + Add Slack `compose-lint-checks`

Android Lint already runs on every PR in `.github/workflows/ci.yml` (`./gradlew testDebugUnitTest lintDebug`) and natively supports AGP 9.1.1 / Kotlin 2.2 with zero Gradle plugin friction.

**Files:**
- Create: `app/lint.xml`
- Modify: `gradle/libs.versions.toml` (add `composeLintChecks = "1.6.1"` → `com.slack.lint.compose:compose-lint-checks`)
- Modify: `app/build.gradle.kts` (add `lintChecks(libs.compose.lint.checks)` and configure `android { lint { ... } }`)

**Key Lint Rules Promoted to `error` in `app/lint.xml`:**
- **Built-in Jetpack Compose & Lifecycle checks**:
  - `FlowOperatorInvokedInComposition` — prevents creating Flow operators on every recomposition
  - `StateFlowValueCalledInComposition` — prevents reading `.value` directly instead of collecting state
  - `CoroutineCreationDuringComposition` — prevents launching untracked coroutines during composition
  - `UnrememberedMutableState` / `RememberReturnType` / `ProduceStateDoesNotAssignValue`
  - `MutableCollectionMutableState`
  - `UnusedMaterial3ScaffoldPaddingParameter`
- **Slack Compose Lint Checks (`com.slack.lint.compose:compose-lint-checks:1.6.1`)**:
  - `ComposeViewModelForwarding` — stops ViewModels from being passed down into child/reusable composables
  - `ComposeViewModelInjection` — keeps ViewModel injection strictly at screen/route entry points
  - `ComposeMultipleContentEmitters` — forces composables to have a single layout responsibility
  - `ComposeMutableParameters` — forbids passing `MutableState`/`MutableList` down into child composables
  - `ComposeModifierMissing` / `ComposeModifierReused` / `ComposeParameterOrder`
  - `ComposeNaming` / `ComposePreviewPublic`
  - `ComposeM2Api` (prevents accidentally importing Material 2 instead of Material 3)

**Steps:**
- [ ] Add `com.slack.lint.compose:compose-lint-checks:1.6.1` to `gradle/libs.versions.toml` and `lintChecks(...)` in `app/build.gradle.kts`.
- [ ] Add `android { lint { abortOnError = true; lintConfig = file("lint.xml"); baseline = file("lint-baseline.xml") /* only if needed for unrelated legacy warnings */ } }` in `app/build.gradle.kts`.
- [ ] Create `app/lint.xml` enforcing the Compose & Lifecycle rules above as `error`.
- [ ] Fix any newly surfaced Compose lint errors across the refactored files (avoid suppressing unless genuinely justified).
- [ ] Verify: `./gradlew lintDebug`.

---

### Task 17: Wire CI & `AGENTS.md` Guardrails

> **Decision (2026-11-26):** Detekt was dropped from this plan. Its Compose rule set duplicates Task 16's Slack `compose-lint-checks` (same rules, Lint packaging), `LargeClass` duplicates Task 15's file-size gate, and the only stable-for-AGP-9 option was an alpha (`dev.detekt:2.0.0-alpha.x`). Function-level limits (≤ 60-line logic functions, ≤ 7 params, low nesting) are documented as `AGENTS.md` guidance instead of being machine-enforced.

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `AGENTS.md`

**Steps:**
- [ ] Update `.github/workflows/ci.yml` step `Run Unit Tests and Lint` to run:
  ```bash
  ./gradlew testDebugUnitTest lintDebug checkArchitectureGates
  ```
- [ ] Update `AGENTS.md` with a concise **Modularity & Architecture Guardrails** section documenting:
  - Enforced limits (`checkArchitectureGates`): `<= 450` lines/file production, `<= 650` lines/file tests, `<= 120` lines per `@Composable`.
  - Guidance (reviewed, not enforced): `<= 60` lines per logic function, `<= 7` params before grouping into an `@Immutable` state/actions class, nesting depth `<= 4`.
  - Architecture rules: inject via `AppContainer` (no `AndroidViewModel`, no ad-hoc `OkHttpClient.Builder()`), expose immutable `StateFlow`s, always use `collectAsStateWithLifecycle()`, place pure math in `data/` or domain calculators, and keep theme colors in `ExpressiveThemeTokens`.
  - Required pre-PR verification command: `./gradlew testDebugUnitTest lintDebug checkArchitectureGates assembleDebug`.
- [ ] Verify: `./gradlew testDebugUnitTest lintDebug checkArchitectureGates assembleDebug`.

---

## Final Verification Checklist

- [ ] Full verification suite passes cleanly:
  ```bash
  ./gradlew testDebugUnitTest lintDebug checkArchitectureGates assembleDebug
  ```
- [ ] File-size audit confirms every refactored production `.kt` file is `<= 450` lines:
  ```bash
  find app/src/main/kotlin -name '*.kt' -exec wc -l {} + | sort -rn | head -n 20
  ```
- [ ] Zero lifecycle-unaware `collectAsState(` usages:
  ```bash
  rg -n "collectAsState\(" app/src/main/kotlin
  ```
- [ ] Zero `AndroidViewModel` usages:
  ```bash
  rg -n "AndroidViewModel" app/src/main/kotlin
  ```
- [ ] Zero `generativeai` references:
  ```bash
  rg -n "generativeai" app/ gradle/
  ```
- [ ] `git branch --show-current` confirms changes are on `refactor/android-architecture-modularization` and `main` is completely unmodified (`git diff main..HEAD --stat`).
- [ ] Manual smoke verification on device/emulator (Light & Dark themes): Garage & predictive back transform, Vehicle Details Bento tiles/drawers & all 6 dialogs, Maintenance record edit & delete persistence, Drive folder sync (images + PDFs), Insights 3-venue card + bill/charger review sheets, Settings all 8 sections + time pickers + OAuth deep-link callback.
