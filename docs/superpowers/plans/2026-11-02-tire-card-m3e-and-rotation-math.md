# Tire Card M3 Expressive Redesign & Rotation Math Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign `VehicleTireHealthCard` with the Option A2 Material 3 Expressive layout (icon-free header, concise copy, wavy progress indicator, and connected 3-button group) and fix tire rotation math to read the latest tire rotation from maintenance logs.

**Architecture:** Extract pure calculation and record-matching logic into `TireRotationCalculator` inside `Tire.kt` so both `VehicleTireHealthCard` and `HomeViewModel` compute rotation targets from the latest `"Tire Rotation"` `MaintenanceRecord` and `Tire.lastRotationOdometer`. Update `VehicleDetailsViewModel.logTireRotation` to also insert a `"Tire Rotation"` maintenance record, and rebuild `VehicleTireHealthCard` with a custom M3 Expressive wavy progress indicator and connected action buttons.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-02-tire-card-m3e-and-rotation-math-design.md`

## Global Constraints

- Do not alter Room database schema or entity table definitions (`TireEntity`, `MaintenanceRecordEntity`, `CarEntity`).
- Use natural, concise copy in `VehicleTireHealthCard`: header subtitle `"Last rotated at X mi"` or `"Installed at X mi"`, status pill `"In X mi"` / `"Due in X mi"` / `"X mi overdue"`, middle container `"Rotate at X mi"` and `"Every Y mi"`, tread footer `"Tread: X% (~Y mi left)"` and `"Installed at Z mi"`, and connected buttons `"+ Rotate"`, `"Interval"`, `"New Tires"`.
- Header row must not display a leading tire icon.
- Remove the synthetic `currentOdo - 5000` default rotation odometer fallback when `activeTire == null`.

## Review Focus

1. **Non-rotation maintenance logs with higher odometer** (e.g., `"Cabin Air Filter"` or `"Wiper Blades"` at 34,000 mi after a `"Tire Rotation"` at 30,000 mi): must not reset the tire rotation baseline; baseline stays at 30,000 mi.
2. **Rotation log in `description` rather than `title`** (e.g., `title = "30,000 Mile Service"`, `description = "Rotated tires and inspected brakes"`): must be recognized as a tire rotation log.
3. **Old rotation log from a previous tire set** (e.g., rotation log at 25,000 mi, but `activeTire.odometerInstalled` is 40,000 mi with `lastRotationOdometer = null`): must ignore rotation logs below `activeTire.odometerInstalled` and use 40,000 mi as the baseline.
4. **No `activeTire` entity and no rotation logs in `records`** (e.g., a newly added car at 18,000 mi with no tire or rotation history): must not invent a fake `currentOdo - 5000` rotation; baseline starts from `0` (or `odometerInstalled = 0`) so rotation due status reflects actual unlogged state.
5. **Current odometer lower than or equal to baseline** (e.g., user logs a rotation at 30,000 mi while `car.odometer` is still 29,500 mi): `milesSinceBaseline` clamps to `0`, `remainingMilesToRotation` uses the highest known odometer (`maxOf(car.odometer, baseline)`), and progress stays in `0f..1f`.

---

### Task 1: Unified Tire Rotation Math & Maintenance Log Sync

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/data/model/Tire.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsViewModel.kt:299-324`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeViewModel.kt:90-120`
- Modify: `app/src/main/kotlin/com/example/car_logger/data/local/dao/TireDao.kt`
- Test: `app/src/test/kotlin/com/example/car_logger/TireModelTest.kt`

**Interfaces:**
- Produces in `com.example.car_logger.data.model`:
  ```kotlin
  data class TireRotationStatus(
      val rotationInterval: Int,
      val currentOdometer: Int,
      val lastRotationOdometer: Int?,
      val baselineOdometer: Int,
      val nextRotationOdometer: Int,
      val remainingMilesToRotation: Int,
      val milesSinceBaseline: Int,
      val rotationProgress: Float,
      val isOverdue: Boolean,
      val isUrgent: Boolean
  )

  object TireRotationCalculator {
      fun isTireRotationRecord(record: MaintenanceRecord): Boolean
      fun calculateRotationStatus(
          car: Car,
          activeTire: Tire?,
          records: List<MaintenanceRecord>
      ): TireRotationStatus
  }
  ```

- [ ] **Step 1: Write failing unit tests in `app/src/test/kotlin/com/example/car_logger/TireModelTest.kt`**

Add tests covering `TireRotationCalculator.isTireRotationRecord` and `TireRotationCalculator.calculateRotationStatus` for all 5 Review Focus cases and standard rotation log resolution:

```kotlin
    @Test
    fun testUsesLatestTireRotationMaintenanceLogOverStaleTireEntity() {
        val car = Car(id = "c1", make = "Tesla", model = "Model Y", year = 2023, odometer = 35350, isEv = true, tireRotationInterval = 7500)
        val tire = Tire(
            id = "t1", carId = "c1", manufacturer = "Michelin", model = "Defender 2",
            dateInstalled = "2024-01-01", odometerInstalled = 15000, estimatedTreadLifeMiles = 60000,
            lastRotationDate = "2024-06-01", lastRotationOdometer = 22500
        )
        val records = listOf(
            MaintenanceRecord(id = "r1", carId = "c1", title = "Tire Rotation", date = "2025-02-01", odometer = 30000),
            MaintenanceRecord(id = "r2", carId = "c1", title = "Cabin Air Filter", date = "2025-05-01", odometer = 34000)
        )

        val status = TireRotationCalculator.calculateRotationStatus(car, tire, records)
        assertEquals(30000, status.lastRotationOdometer)
        assertEquals(37500, status.nextRotationOdometer)
        assertEquals(2150, status.remainingMilesToRotation)
        assertEquals(5350, status.milesSinceBaseline)
    }

    @Test
    fun testRecognizesRotationInDescriptionAndIgnoresPreInstallLogs() {
        val car = Car(id = "c1", make = "Rivian", model = "R1T", year = 2024, odometer = 43000, isEv = true, tireRotationInterval = 7500)
        val tire = Tire(
            id = "t1", carId = "c1", manufacturer = "Pirelli", model = "Scorpion",
            dateInstalled = "2025-01-01", odometerInstalled = 40000, estimatedTreadLifeMiles = 50000,
            lastRotationOdometer = null
        )
        val records = listOf(
            MaintenanceRecord(id = "r_old", carId = "c1", title = "Tire Rotation", date = "2024-08-01", odometer = 25000),
            MaintenanceRecord(id = "r_desc", carId = "c1", title = "42k Multi-Point Service", description = "Rotated tires and checked alignment", date = "2025-03-01", odometer = 42000)
        )

        val status = TireRotationCalculator.calculateRotationStatus(car, tire, records)
        assertEquals(42000, status.lastRotationOdometer)
        assertEquals(49500, status.nextRotationOdometer)
        assertEquals(6500, status.remainingMilesToRotation)
    }

    @Test
    fun testNoActiveTireAndNoRotationLogsDoesNotUseFakeOffset() {
        val car = Car(id = "c1", make = "Ford", model = "Mustang Mach-E", year = 2023, odometer = 18000, isEv = true, tireRotationInterval = 7500)
        val status = TireRotationCalculator.calculateRotationStatus(car, activeTire = null, records = emptyList())
        assertEquals(null, status.lastRotationOdometer)
        assertEquals(0, status.baselineOdometer)
        assertEquals(7500, status.nextRotationOdometer)
        assertEquals(-10500, status.remainingMilesToRotation)
        assertEquals(true, status.isOverdue)
    }

    @Test
    fun testRotationOdometerHigherThanCarOdometerClampsCleanly() {
        val car = Car(id = "c1", make = "Tesla", model = "Model 3", year = 2023, odometer = 29500, isEv = true, tireRotationInterval = 7500)
        val records = listOf(
            MaintenanceRecord(id = "r1", carId = "c1", title = "Rotate Tires", date = "2025-06-01", odometer = 30000)
        )
        val status = TireRotationCalculator.calculateRotationStatus(car, activeTire = null, records = records)
        assertEquals(30000, status.lastRotationOdometer)
        assertEquals(30000, status.currentOdometer)
        assertEquals(37500, status.nextRotationOdometer)
        assertEquals(7500, status.remainingMilesToRotation)
        assertEquals(0, status.milesSinceBaseline)
        assertEquals(0f, status.rotationProgress, 0.001f)
    }
```

- [ ] **Step 2: Run unit tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests com.example.car_logger.TireModelTest`
Expected: FAIL with `Unresolved reference: TireRotationCalculator`

- [ ] **Step 3: Implement `TireRotationStatus` and `TireRotationCalculator` in `app/src/main/kotlin/com/example/car_logger/data/model/Tire.kt` and wire ViewModels**

In `Tire.kt`, implement `TireRotationStatus` and `TireRotationCalculator`:
- `isTireRotationRecord(record: MaintenanceRecord): Boolean` checks ` "${record.title} ${record.description.orEmpty()}".lowercase() ` for `"tire rotation"`, `"rotate tire"`, `"rotated tire"`, `"tires rotated"`, or (`contains("tire")` && `contains("rotat")`).
- `calculateRotationStatus(car: Car, activeTire: Tire?, records: List<MaintenanceRecord>): TireRotationStatus` filters `records` to `it.carId == car.id && isTireRotationRecord(it) && (activeTire == null || it.odometer >= activeTire.odometerInstalled)`, resolves `lastRotationOdometer = listOfNotNull(activeTire?.lastRotationOdometer, maxRecordOdo).maxOrNull()`, `baselineOdometer = lastRotationOdometer ?: activeTire?.odometerInstalled ?: 0`, `currentOdometer = maxOf(car.odometer ?: 0, baselineOdometer)`, and computes the remaining fields.
- In `TireDao.kt`, add `@Query("SELECT * FROM tires") fun getAllTires(): Flow<List<TireEntity>>`.
- In `HomeViewModel.kt`, combine `tireDao.getAllTires()` into `scheduledServices` and use `TireRotationCalculator.calculateRotationStatus` when `car.isEv` (or when a tire/rotation exists) so non-rotation records do not reset the rotation schedule.
- In `VehicleDetailsViewModel.kt`, update `logTireRotation(date: String, odometer: Int)` so it also inserts a `MaintenanceRecordEntity(id = UUID.randomUUID().toString(), carId = carId, title = "Tire Rotation", date = date, odometer = odometer)` and updates `carDao.updateOdometer(carId, odometer)` when `odometer > (car.value?.odometer ?: 0)`.

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.example.car_logger.TireModelTest`
Expected: PASS

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/kotlin/com/example/car_logger/data/model/Tire.kt \
        app/src/main/kotlin/com/example/car_logger/data/local/dao/TireDao.kt \
        app/src/main/kotlin/com/example/car_logger/ui/screens/home/HomeViewModel.kt \
        app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsViewModel.kt \
        app/src/test/kotlin/com/example/car_logger/TireModelTest.kt
git commit -m "fix(tires): calculate rotation interval from latest tire rotation maintenance log"
```

---

### Task 2: Option A2 M3 Expressive `VehicleTireHealthCard` Redesign

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleTireHealthCard.kt`
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/tabs/VehicleOverviewTab.kt:94-103`

**Interfaces:**
- Consumes: `TireRotationCalculator.calculateRotationStatus(car, activeTire, records)` from Task 1.
- Produces in `com.example.car_logger.ui.screens.vehicledetails.components`:
  ```kotlin
  @Composable
  fun VehicleTireHealthCard(
      car: Car,
      activeTire: Tire?,
      records: List<MaintenanceRecord> = emptyList(),
      onLogRotationClick: () -> Unit,
      onEditIntervalClick: () -> Unit,
      onInstallNewTireClick: () -> Unit,
      modifier: Modifier = Modifier
  )

  @Composable
  fun ExpressiveWavyProgressIndicator(
      progress: Float,
      color: Color,
      trackColor: Color,
      modifier: Modifier = Modifier
  )
  ```

- [ ] **Step 1: Update `VehicleTireHealthCard.kt` and `VehicleOverviewTab.kt` with Option A2 layout**

In `VehicleTireHealthCard.kt`:
1. Use `TireRotationCalculator.calculateRotationStatus(car, activeTire, records)` for rotation values, and default `tire` when `activeTire == null` to `odometerInstalled = 0` and `lastRotationOdometer = null` (no fake `currentOdo - 5000`).
2. **Top Header Row (Icon-Free)**:
   - Left `Column`:
     - Title: `"${tire.manufacturer} ${tire.model}"` (`MaterialTheme.typography.titleMedium`, `FontWeight.Bold`).
     - Subtitle (`MaterialTheme.typography.bodySmall`, `onSurfaceVariant`):
       - If `status.lastRotationOdometer != null`: `"Last rotated at ${fmt(status.lastRotationOdometer)} mi"`
       - Else if `tire.odometerInstalled > 0`: `"Installed at ${fmt(tire.odometerInstalled)} mi"`
       - Else: `"No rotation logged yet"`
   - Right status pill (`Surface` with `CircleShape`):
     - Overdue: `"${fmt(-status.remainingMilesToRotation)} mi overdue"`
     - Urgent (`0..1000`): `"Due in ${fmt(status.remainingMilesToRotation)} mi"`
     - Scheduled: `"In ${fmt(status.remainingMilesToRotation)} mi"`
3. **Middle Container (`Surface` with `RoundedCornerShape(16.dp)`, `surfaceContainerHigh.copy(alpha = 0.65f)`)**:
   - Top row: `"Rotate at ${fmt(status.nextRotationOdometer)} mi"` (`FontWeight.SemiBold`) and `"Every ${fmt(status.rotationInterval)} mi"` (`onSurfaceVariant`).
   - Middle: `ExpressiveWavyProgressIndicator` drawing an M3 Expressive sinusoidal wave along `(0f..animatedProgress)` with `StrokeCap.Round` and a flat rounded track from `animatedProgress..1f`.
   - Bottom row: `"Tread: ${(wearPercentage * 100).toInt()}% (~${fmt(remainingTreadMiles)} mi left)"` and `"Installed at ${fmt(tire.odometerInstalled)} mi"`.
4. **Bottom Connected M3 Expressive ButtonGroup (`Row` with `horizontalArrangement = Arrangement.spacedBy(6.dp)`)**:
   - Leading button (`RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 6.dp, bottomEnd = 6.dp)`): Primary filled `"+ Rotate"` calling `onLogRotationClick`.
   - Middle button (`RoundedCornerShape(6.dp)`): Tonal/surface `"Interval"` calling `onEditIntervalClick`.
   - Trailing button (`RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp)`): Tonal/surface `"New Tires"` calling `onInstallNewTireClick`.
5. In `VehicleOverviewTab.kt`, pass `records = records` to `VehicleTireHealthCard`.

- [ ] **Step 2: Run full unit test suite and assemble debug APK**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL` with all unit tests passing.

- [ ] **Step 3: Commit Task 2**

```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleTireHealthCard.kt \
        app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/tabs/VehicleOverviewTab.kt \
        docs/superpowers/specs/2026-11-02-tire-card-m3e-and-rotation-math-design.md \
        docs/superpowers/plans/2026-11-02-tire-card-m3e-and-rotation-math.md
git commit -m "feat(ui): redesign tire card with M3 expressive wavy progress and connected buttons"
```
