# Daily End-of-Day Odometer Polling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement automated nightly background polling via Android Jetpack WorkManager at 11:00 PM local time to record high-fidelity daily vehicle mileage snapshots in `odometer_logs`, with settings transparency in `SettingsTab`.

**Architecture:** A WorkManager periodic worker (`DailyOdometerSyncWorker`) scheduled daily at 11:00 PM via `DailyPollScheduler` queries all unarchived vehicles with `forceWake = true` and `bypassCooldown = true`. Results are saved to Room (`odometer_logs`) and summarized in DataStore (`PreferencesManager`). The feature can be toggled and monitored in `SettingsTab` under VEHICLE TELEMETRY.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Material 3 Expressive, AndroidX WorkManager, Room, DataStore Preferences, OkHttp, Coroutines.

**Spec:** [docs/superpowers/specs/2026-10-30-daily-mileage-poll-design.md](file:///Users/bbowling/.gemini/antigravity/worktrees/car-logging/daily_mileage_poll_schedule/docs/superpowers/specs/2026-10-30-daily-mileage-poll-design.md)

## Global Constraints
- Target/Compile SDK: 35, Min SDK: 26.
- WorkManager dependency: `androidx.work:work-runtime-ktx:2.10.0`.
- Scheduled poll interval: 24 hours, flex interval: 15 minutes, network constraint: `NetworkType.CONNECTED`.
- Default target time: 11:00 PM (23:00) local time.
- Overnight poll must wake vehicles (`forceWake = true`) and bypass cooldown (`bypassCooldown = true`).
- Manual sync and app startup checks must continue to honor `telemetry_cooldown_hours`.
- Setting located under `VEHICLE TELEMETRY` section of `SettingsTab`.

## Review Focus
1. Current time after 11:00 PM: Scheduler calculates delay to 11:00 PM tomorrow rather than negative or immediate run.
2. Device offline at 11:00 PM: WorkManager defers execution until network is connected without throwing an unhandled exception.
3. Disabled in Settings: If user disables the toggle, existing periodic work is cancelled and worker aborts early if already triggered.
4. Cooldown interaction: Scheduled sync ignores cooldown and updates mileage, while manual sync performed right before/after respects cooldown.
5. All vehicles asleep: Tesla is awakened via `wake_up` endpoint and Smartcar telematics is pinged, logging readings into `odometer_logs`.

---

### Task 1: Add WorkManager dependencies to Gradle catalog and app build

**Files:**
- Modify: `gradle/libs.versions.toml:28,79`
- Modify: `app/build.gradle.kts:140-160`

**Interfaces:**
- Consumes: Gradle version catalog
- Produces: `androidx.work:work-runtime-ktx` and `androidx.work:work-testing` available in classpath

- [ ] **Step 1: Add WorkManager catalog entries in `gradle/libs.versions.toml`**

Add version:
```toml
work = "2.10.0"
```
Add library definitions under `[libraries]`:
```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

- [ ] **Step 2: Add WorkManager dependencies to `app/build.gradle.kts`**

Add under `dependencies`:
```kotlin
implementation(libs.androidx.work.runtime.ktx)
testImplementation(libs.androidx.work.testing)
```

- [ ] **Step 3: Compile to verify dependencies resolve**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add AndroidX WorkManager dependencies"
```

---

### Task 2: DataStore Preferences for Daily Polling

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/data/local/PreferencesManager.kt`
- Test: `app/src/test/kotlin/com/example/car_logger/PreferencesManagerDailyPollTest.kt`

**Interfaces:**
- Consumes: AndroidX DataStore `Preferences`
- Produces:
  - `PreferencesManager.KEY_DAILY_POLL_ENABLED`
  - `PreferencesManager.KEY_LAST_DAILY_POLL_TIME`
  - `PreferencesManager.KEY_LAST_DAILY_POLL_STATUS`
  - `PreferencesManager.dailyPollEnabledFlow: Flow<Boolean>`
  - `PreferencesManager.lastDailyPollTimeFlow: Flow<Long>`
  - `PreferencesManager.lastDailyPollStatusFlow: Flow<String?>`
  - `suspend fun getDailyPollEnabled(): Boolean`
  - `suspend fun setDailyPollEnabled(enabled: Boolean)`
  - `suspend fun recordDailyPollResult(timestampMs: Long, status: String)`

- [ ] **Step 1: Write the failing unit test for daily poll preferences**

Create `app/src/test/kotlin/com/example/car_logger/PreferencesManagerDailyPollTest.kt`:
```kotlin
package com.example.car_logger

import com.example.car_logger.data.local.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreferencesManagerDailyPollTest {
    @Test
    fun `verify preferences keys are defined with correct names`() {
        assertEquals("telemetry_daily_poll_enabled", PreferencesManager.KEY_DAILY_POLL_ENABLED.name)
        assertEquals("telemetry_last_daily_poll_time", PreferencesManager.KEY_LAST_DAILY_POLL_TIME.name)
        assertEquals("telemetry_last_daily_poll_status", PreferencesManager.KEY_LAST_DAILY_POLL_STATUS.name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.PreferencesManagerDailyPollTest"`
Expected: FAIL with unresolved reference `KEY_DAILY_POLL_ENABLED`.

- [ ] **Step 3: Implement preferences in `PreferencesManager.kt`**

In `PreferencesManager.kt`:
1. In `companion object`:
```kotlin
val KEY_DAILY_POLL_ENABLED = booleanPreferencesKey("telemetry_daily_poll_enabled")
val KEY_LAST_DAILY_POLL_TIME = longPreferencesKey("telemetry_last_daily_poll_time")
val KEY_LAST_DAILY_POLL_STATUS = stringPreferencesKey("telemetry_last_daily_poll_status")
```
2. Add flows:
```kotlin
val dailyPollEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
    prefs[KEY_DAILY_POLL_ENABLED] ?: true
}

val lastDailyPollTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
    prefs[KEY_LAST_DAILY_POLL_TIME] ?: 0L
}

val lastDailyPollStatusFlow: Flow<String?> = context.dataStore.data.map { prefs ->
    prefs[KEY_LAST_DAILY_POLL_STATUS]
}
```
3. Add accessor functions:
```kotlin
suspend fun getDailyPollEnabled(): Boolean = dailyPollEnabledFlow.first()

suspend fun setDailyPollEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[KEY_DAILY_POLL_ENABLED] = enabled
    }
}

suspend fun recordDailyPollResult(timestampMs: Long, status: String) {
    context.dataStore.edit { prefs ->
        prefs[KEY_LAST_DAILY_POLL_TIME] = timestampMs
        prefs[KEY_LAST_DAILY_POLL_STATUS] = status
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.PreferencesManagerDailyPollTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/data/local/PreferencesManager.kt app/src/test/kotlin/com/example/car_logger/PreferencesManagerDailyPollTest.kt
git commit -m "feat: add daily poll preference keys and accessors"
```

---

### Task 3: Telemetry Engine Updates in `OdometerSyncService`

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/services/OdometerSyncService.kt:570-620`
- Modify: `app/src/test/kotlin/com/example/car_logger/OdometerSyncServiceTest.kt`

**Interfaces:**
- Consumes: `PreferencesManager`, `Car`, `CarDao`, `OdometerLogDao`
- Produces:
  - `syncVehicle(car: Car, isManual: Boolean = false, forceWake: Boolean = false, bypassCooldown: Boolean = false): OdometerSyncResult`
  - `syncDailyEndOfDay(cars: List<Car>): List<OdometerSyncResult>`

- [ ] **Step 1: Write failing tests for bypassCooldown and daily end of day sync**

In `app/src/test/kotlin/com/example/car_logger/OdometerSyncServiceTest.kt`, add:
```kotlin
    @Test
    fun `isCooldownActive respects bypassCooldown parameter concept`() {
        val now = 1_700_000_000_000L
        val oneHourAgo = now - 3_600_000L
        val inCooldown = OdometerSyncService.isCooldownActive(oneHourAgo, 72, now)
        assertTrue(inCooldown)
        // With bypassCooldown, callers skip isCooldownActive check
    }

    @Test
    fun `formatDailyPollSummary creates descriptive message for updated and skipped results`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)
        val results = listOf(
            OdometerSyncResult.Updated(car1.id, 15000, OdometerSyncService.PROVIDER_SMARTCAR),
            OdometerSyncResult.Updated(car2.id, 28000, OdometerSyncService.PROVIDER_TESLA)
        )
        val summary = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), results)
        assertEquals("Synced 2 vehicles", summary)
    }
```

- [ ] **Step 2: Run test to verify failure**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.OdometerSyncServiceTest"`
Expected: FAIL with unresolved reference `formatDailyPollSummary`.

- [ ] **Step 3: Implement `formatDailyPollSummary`, `bypassCooldown`, and `syncDailyEndOfDay`**

In `OdometerSyncService.kt`:
1. Add companion helper:
```kotlin
fun formatDailyPollSummary(cars: List<Car>, results: List<OdometerSyncResult>): String {
    val updatedCount = results.count { it is OdometerSyncResult.Updated }
    if (updatedCount == results.size && updatedCount > 0) {
        return if (updatedCount == 1) "Synced 1 vehicle" else "Synced $updatedCount vehicles"
    }
    val carMap = cars.associateBy { it.id }
    val details = results.mapNotNull { res ->
        when (res) {
            is OdometerSyncResult.Updated -> carMap[res.carId]?.model?.let { "$it: synced" }
            is OdometerSyncResult.SkippedAsleep -> carMap[res.carId]?.model?.let { "$it: asleep" }
            is OdometerSyncResult.Error -> carMap[res.carId]?.model?.let { "$it: error" }
            else -> null
        }
    }
    return if (details.isNotEmpty()) details.joinToString(", ") else "Daily sync completed"
}
```
2. Update `syncVehicle`:
```kotlin
suspend fun syncVehicle(
    car: Car,
    isManual: Boolean = false,
    forceWake: Boolean = false,
    bypassCooldown: Boolean = false
): OdometerSyncResult = withContext(Dispatchers.IO) {
    val provider = resolveProviderForCar(car)
        ?: return@withContext OdometerSyncResult.NotConfigured(car.id)

    val cooldownHours = preferencesManager.getSyncCooldownHours()
    val nowMs = System.currentTimeMillis()

    // Enforce cooldown unless user explicitly forced wake/poll OR it is a scheduled daily poll
    if (!forceWake && !bypassCooldown && isCooldownActive(car.lastOdometerSyncTime, cooldownHours, nowMs)) {
        val remaining = remainingCooldownHours(car.lastOdometerSyncTime, cooldownHours, nowMs)
        return@withContext OdometerSyncResult.SkippedCooldown(car.id, remaining)
    }
    ...
```
3. Implement `syncDailyEndOfDay`:
```kotlin
suspend fun syncDailyEndOfDay(cars: List<Car>): List<OdometerSyncResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<OdometerSyncResult>()
    val eligibleCars = cars.filter { !it.isArchived && resolveProviderForCar(it) != null }
    for (car in eligibleCars) {
        val res = syncVehicle(car = car, isManual = false, forceWake = true, bypassCooldown = true)
        results.add(res)
    }
    val nowMs = System.currentTimeMillis()
    val summary = formatDailyPollSummary(eligibleCars, results)
    preferencesManager.recordDailyPollResult(nowMs, summary)
    results
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.OdometerSyncServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/services/OdometerSyncService.kt app/src/test/kotlin/com/example/car_logger/OdometerSyncServiceTest.kt
git commit -m "feat: add daily poll sync mode and summary formatting to OdometerSyncService"
```

---

### Task 4: Scheduler Logic & Daily Worker Implementation

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/services/DailyPollScheduler.kt`
- Create: `app/src/main/kotlin/com/example/car_logger/services/DailyOdometerSyncWorker.kt`
- Create: `app/src/test/kotlin/com/example/car_logger/DailyPollSchedulerTest.kt`

**Interfaces:**
- Consumes: `androidx.work.WorkManager`, `PreferencesManager`, `AppDatabase`, `OdometerSyncService`
- Produces:
  - `DailyPollScheduler.calculateInitialDelay(targetHour: Int, targetMinute: Int, nowMs: Long, zoneId: ZoneId): Long`
  - `DailyPollScheduler.schedule(context: Context)`
  - `DailyPollScheduler.cancel(context: Context)`
  - `DailyOdometerSyncWorker: CoroutineWorker(context, workerParams)`

- [ ] **Step 1: Write failing unit test for `DailyPollScheduler.calculateInitialDelay`**

Create `app/src/test/kotlin/com/example/car_logger/DailyPollSchedulerTest.kt`:
```kotlin
package com.example.car_logger

import com.example.car_logger.services.DailyPollScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyPollSchedulerTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `calculateInitialDelay before 11 PM targets today 11 PM`() {
        // 9:00 AM today -> 14 hours until 11:00 PM
        val morning = LocalDateTime.of(2026, 10, 30, 9, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = DailyPollScheduler.calculateInitialDelay(
            targetHour = 23,
            targetMinute = 0,
            nowMs = morning,
            zoneId = zone
        )
        val expectedDelayMs = 14 * 3600 * 1000L
        assertEquals(expectedDelayMs, delay)
    }

    @Test
    fun `calculateInitialDelay after 11 PM targets tomorrow 11 PM`() {
        // 11:30 PM today -> 23.5 hours until tomorrow 11:00 PM
        val lateNight = LocalDateTime.of(2026, 10, 30, 23, 30, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val delay = DailyPollScheduler.calculateInitialDelay(
            targetHour = 23,
            targetMinute = 0,
            nowMs = lateNight,
            zoneId = zone
        )
        val expectedDelayMs = (23 * 3600 + 30 * 60) * 1000L
        assertEquals(expectedDelayMs, delay)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.DailyPollSchedulerTest"`
Expected: FAIL with unresolved class `DailyPollScheduler`.

- [ ] **Step 3: Implement `DailyPollScheduler.kt` and `DailyOdometerSyncWorker.kt`**

Create `app/src/main/kotlin/com/example/car_logger/services/DailyPollScheduler.kt`:
```kotlin
package com.example.car_logger.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyPollScheduler {
    const val UNIQUE_WORK_NAME = "daily_odometer_end_of_day_sync"
    const val TARGET_HOUR = 23
    const val TARGET_MINUTE = 0

    fun calculateInitialDelay(
        targetHour: Int = TARGET_HOUR,
        targetMinute: Int = TARGET_MINUTE,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        var target = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli() - nowMs
    }

    fun schedule(context: Context) {
        val delayMs = calculateInitialDelay()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DailyOdometerSyncWorker>(
            24, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
```

Create `app/src/main/kotlin/com/example/car_logger/services/DailyOdometerSyncWorker.kt`:
```kotlin
package com.example.car_logger.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DailyOdometerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.getDailyPollEnabled()) {
            return@withContext Result.success()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val carDao = database.carDao()
        val odometerLogDao = database.odometerLogDao()
        val syncService = OdometerSyncService(prefs, carDao, odometerLogDao)

        return@withContext try {
            val entities = carDao.getAllCars().first()
            val models = entities.map { it.toModel() }
            syncService.syncDailyEndOfDay(models)
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyOdometerWorker", "Nightly sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --tests "com.example.car_logger.DailyPollSchedulerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/services/DailyPollScheduler.kt app/src/main/kotlin/com/example/car_logger/services/DailyOdometerSyncWorker.kt app/src/test/kotlin/com/example/car_logger/DailyPollSchedulerTest.kt
git commit -m "feat: implement DailyPollScheduler and DailyOdometerSyncWorker"
```

---

### Task 5: Application Startup Integration

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/CarLoggerApplication.kt:25-38`

**Interfaces:**
- Consumes: `PreferencesManager.getDailyPollEnabled()`, `DailyPollScheduler.schedule(context)`

- [ ] **Step 1: Integrate `DailyPollScheduler` into `CarLoggerApplication.kt`**

In `CarLoggerApplication.kt` inside `onCreate()`:
```kotlin
        // Schedule nightly end-of-day mileage poll if enabled
        applicationScope.launch {
            try {
                if (preferencesManager.getDailyPollEnabled()) {
                    DailyPollScheduler.schedule(this@CarLoggerApplication)
                }
            } catch (e: Exception) {
                Log.e("CarLoggerApplication", "Scheduler init error: ${e.message}", e)
            }
        }
```

- [ ] **Step 2: Compile to verify syntax**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/CarLoggerApplication.kt
git commit -m "feat: schedule daily odometer poll on app startup"
```

---

### Task 6: Settings UI Integration in `SettingsTab.kt`

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt:550-640`

**Interfaces:**
- Consumes:
  - `preferencesManager.dailyPollEnabledFlow`
  - `preferencesManager.lastDailyPollTimeFlow`
  - `preferencesManager.lastDailyPollStatusFlow`
  - `DailyPollScheduler.schedule(context)`
  - `DailyPollScheduler.cancel(context)`
- Produces: M3 Switch item under VEHICLE TELEMETRY section with status display

- [ ] **Step 1: Collect daily poll flows in `SettingsTab.kt`**

Add collectAsState for daily poll preferences:
```kotlin
val dailyPollEnabled by preferencesManager.dailyPollEnabledFlow.collectAsState(initial = true)
val lastDailyPollTime by preferencesManager.lastDailyPollTimeFlow.collectAsState(initial = 0L)
val lastDailyPollStatus by preferencesManager.lastDailyPollStatusFlow.collectAsState(initial = null)
val context = LocalContext.current
```

- [ ] **Step 2: Add Nightly Mileage Snapshot Row in `SettingsTab.kt`**

Inside the `SettingsGroupCard` of the `VEHICLE TELEMETRY` section (above the provider connections), insert:
```kotlin
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    // Nightly End-of-Day Polling
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Nightly Mileage Snapshot",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Poll at 11:00 PM while plugged in overnight",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dailyPollEnabled) {
                val lastRunText = if (lastDailyPollTime > 0L) {
                    val formattedTime = OdometerSyncService.formatRelativeTime(lastDailyPollTime)
                    val statusText = lastDailyPollStatus?.let { " • $it" } ?: ""
                    "Last run $formattedTime$statusText"
                } else {
                    "Scheduled for 11:00 PM tonight"
                }
                Text(
                    text = lastRunText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = dailyPollEnabled,
            onCheckedChange = { isChecked ->
                coroutineScope.launch {
                    preferencesManager.setDailyPollEnabled(isChecked)
                    if (isChecked) {
                        DailyPollScheduler.schedule(context)
                    } else {
                        DailyPollScheduler.cancel(context)
                    }
                }
            }
        )
    }
```

- [ ] **Step 3: Compile to verify layout and imports**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt
git commit -m "feat: add nightly mileage snapshot toggle and status in SettingsTab"
```

---

### Task 7: Comprehensive Verification & Test Suite

**Files:**
- Test all components and integration

- [ ] **Step 1: Run all unit tests**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test`
Expected: All tests pass with zero errors.

- [ ] **Step 2: Assemble Debug APK to confirm packaging**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL with debug APK generated.

- [ ] **Step 3: Final Git status check**

Run: `git status`
Expected: Working tree clean.
