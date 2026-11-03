# Daily End-of-Day Odometer Polling & Telemetry Scheduling Design

## 1. Intent & Context
In the **Car Logger** Android application, accurate and granular mileage tracking is critical for the vehicle journal and the **Insights** engine (`InsightsCalculationEngine`). The Insights screen computes weekly, monthly, and year-to-date driving trends, daily mileage rates, and electric vs. gas savings based on historical entries stored in `odometer_logs` (`OdometerLogEntity`).

Currently, odometer syncing only executes on demand:
1. When the user manually taps "Poll Now" or "Force Wake" in the UI.
2. When the app launches (`HomeViewModel` checks `initialTelemetryChecked`).

If the user does not open the app daily, the application cannot record daily mileage points, reducing data fidelity. To resolve this, the app will automatically poll eligible vehicles at the end of every day (~11:00 PM local time).

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Automated Nightly Snapshot:** Schedule a background job using Android Jetpack `WorkManager` that runs once every 24 hours, targeting ~11:00 PM local time.
- **Wake While Plugged In:** In the nightly background poll, query all eligible vehicles regardless of current sleep state (`forceWake = true`), taking advantage of overnight charging when battery drain is negligible.
- **Cooldown Exemption for Daily Poll:** Exempt the scheduled end-of-day poll from the multi-day cooldown (`telemetry_cooldown_hours`, e.g. 72h / 24h), ensuring a snapshot is recorded every single day.
- **Preserve Interactive Cooldowns:** Retain the cooldown policy for manual syncs and app-launch syncs to prevent excessive polling during daytime usage.
- **Settings Transparency:** Add an "End-of-Day Polling" preference control and status view inside the `VEHICLE TELEMETRY` section of `SettingsTab`:
  - Toggle switch (enabled by default).
  - Target run time display (11:00 PM).
  - Last run timestamp and summary (e.g., "Last run: Oct 30, 11:00 PM • Synced Blazer EV & Model 3").
- **Survivability & Rescheduling:** Automatically ensure the periodic work is scheduled on application start (`CarLoggerApplication.onCreate`) and dynamically updated when toggled in Settings.

### Non-Goals
- Real-time continuous polling (e.g., polling every 15 minutes).
- Changing manual on-demand sync flows in `VehicleDetailsScreen`.
- Replacing existing `WakeProtectionPolicy` controls for manual/startup daytime syncing.

### Anti-Goals
- Silently failing without updating the last-run status in `PreferencesManager`.
- Allowing background work to bypass network connectivity constraints (must require `NetworkType.CONNECTED`).
- Polling archived vehicles or vehicles without configured telemetry credentials.

---

## 3. Approaches Considered & Tradeoffs

### Approach 1: Headless WorkManager Periodic Worker
- **Summary:** Enqueue a WorkManager `PeriodicWorkRequest` at startup targeting 11:00 PM. No UI representation in Settings.
- **Pros:** Minimal code, zero UI footprint.
- **Cons:** The user has no visibility into whether the background job succeeded or when it last ran.
- **Verdict:** Rejected in favor of Approach 2.

### Approach 2: WorkManager with Settings Integration & Status (Chosen)
- **Summary:** Jetpack `WorkManager` `PeriodicWorkRequest` targeting 11:00 PM with `NetworkType.CONNECTED`. Plus an in-app toggle and status card under the `VEHICLE TELEMETRY` section of `SettingsTab`.
- **Pros:** Full visibility into daily sync status, transparent execution history, user control to disable/re-enable.
- **Cons:** Slightly more UI and preference wiring.
- **Verdict:** Selected.

---

## 4. Technical Architecture & Implementation Details

### 4.1 Dependency Updates
- **`gradle/libs.versions.toml`**:
  - `work = "2.10.0"`
  - `androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }`
  - `androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }`
- **`app/build.gradle.kts`**:
  - `implementation(libs.androidx.work.runtime.ktx)`
  - `testImplementation(libs.androidx.work.testing)`

### 4.2 Data & Persistence Layer (`PreferencesManager`)
Add preferences to track daily poll configuration and execution results:
- `KEY_DAILY_POLL_ENABLED`: `booleanPreferencesKey("telemetry_daily_poll_enabled")` (default: `true`)
- `KEY_LAST_DAILY_POLL_TIME`: `longPreferencesKey("telemetry_last_daily_poll_time")` (default: `0L`)
- `KEY_LAST_DAILY_POLL_STATUS`: `stringPreferencesKey("telemetry_last_daily_poll_status")` (default: `null`)
- Expose flows and helper methods:
  - `dailyPollEnabledFlow: Flow<Boolean>`
  - `lastDailyPollTimeFlow: Flow<Long>`
  - `lastDailyPollStatusFlow: Flow<String?>`
  - `setDailyPollEnabled(enabled: Boolean)`
  - `recordDailyPollResult(timestampMs: Long, status: String)`

### 4.3 Telemetry Service Updates (`OdometerSyncService`)
Extend `OdometerSyncService` to support scheduled daily polling:
- `syncDailyEndOfDay(cars: List<Car>): List<OdometerSyncResult>`:
  - Iterates over unarchived vehicles with configured providers (`SMARTCAR` or `TESLA_FLEET`).
  - Calls `syncVehicle(car, isManual = false, forceWake = true, bypassCooldown = true)`.
  - Records each successful reading into `odometer_logs` via `recordOdometerReading`.
  - Computes a human-readable summary of the run (e.g., `"Synced 2 vehicles"` or `"Synced Blazer EV, Model 3 asleep"`).
  - Updates `PreferencesManager.recordDailyPollResult(nowMs, summary)`.
- Update `syncVehicle`:
  - Add parameter `bypassCooldown: Boolean = false`.
  - If `bypassCooldown` is true, skip the `isCooldownActive` check while preserving cooldown checks for regular calls (`isManual = false, bypassCooldown = false`).

### 4.4 Background Worker & Scheduler
- **`DailyOdometerSyncWorker`** (`com.example.car_logger.services.DailyOdometerSyncWorker`):
  - Extends `CoroutineWorker(appContext, workerParams)`.
  - Checks `preferencesManager.getDailyPollEnabled()`. If false, returns `Result.success()`.
  - Retrieves active vehicles via `AppDatabase.getInstance(appContext).carDao()`.
  - Runs `odometerSyncService.syncDailyEndOfDay(cars)`.
  - Returns `Result.success()` (or `Result.retry()` on transient network failure).
- **`DailyPollScheduler`** (`com.example.car_logger.services.DailyPollScheduler`):
  - Constant: `WORK_NAME = "daily_odometer_end_of_day_sync"`.
  - `calculateInitialDelay(targetHour: Int = 23, targetMinute: Int = 0, nowMs: Long = System.currentTimeMillis()): Long`:
    - Computes milliseconds until the next 11:00 PM local time.
    - If current time is before 11:00 PM today, delay is `today 11:00 PM - now`.
    - If current time is past 11:00 PM today, delay is `tomorrow 11:00 PM - now`.
  - `schedule(context: Context)`:
    - Calculates initial delay to 11:00 PM.
    - Builds a `PeriodicWorkRequestBuilder<DailyOdometerSyncWorker>(24, TimeUnit.HOURS, flexTimeInterval = 15, TimeUnit.MINUTES)`.
    - Sets constraints: `NetworkType.CONNECTED`.
    - Enqueues via `WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)`.
  - `cancel(context: Context)`:
    - Calls `WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)`.

### 4.5 Application Initialization (`CarLoggerApplication`)
In `CarLoggerApplication.onCreate()`:
- Check `preferencesManager.isDailyPollEnabled()`. If enabled (default), invoke `DailyPollScheduler.schedule(this)`.

### 4.6 UI Integration (`SettingsTab.kt`)
Under the `VEHICLE TELEMETRY` section:
- Insert an `End-of-Day Polling` settings row/card:
  - Title: "Nightly Mileage Snapshot"
  - Subtitle: "Poll at 11:00 PM while plugged in overnight"
  - Switch component bound to `dailyPollEnabled`.
  - Sub-label displaying last run information:
    - If run recorded: `Last poll: Today at 11:00 PM • Synced 2 vehicles` (or formatted relative date/time).
    - If not yet run: `Next poll scheduled for 11:00 PM`.
  - Toggling updates `PreferencesManager.setDailyPollEnabled` and either schedules or cancels via `DailyPollScheduler`.

---

## 5. Verification Plan

### Automated Tests
1. **`DailyPollSchedulerTest`**:
   - Verify `calculateInitialDelay` when current time is 9:00 AM (delay should be 14 hours).
   - Verify `calculateInitialDelay` when current time is 11:30 PM (delay should be 23.5 hours to next day's 11:00 PM).
2. **`OdometerSyncServiceTest`**:
   - Verify `syncDailyEndOfDay` executes with `bypassCooldown = true` and updates vehicles even if their last sync was 2 hours ago.
   - Verify startup/manual sync still enforces `isCooldownActive(lastSync, 72h)`.
   - Verify `recordDailyPollResult` stores timestamp and status message.
3. **`DailyOdometerSyncWorkerTest`**:
   - Test worker behavior with `WorkManagerTestInitHelper`.
   - Verify worker returns `Result.success()` and calls `syncDailyEndOfDay`.

### Manual End-to-End Verification
1. Run `./gradlew test` to ensure all unit tests pass.
2. Verify Settings UI reflects "Nightly Mileage Snapshot", allows toggling, and correctly displays status.
