# Vehicle Page UI Refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the vehicle details screen with a sparkle-only photo button, editable VIN and Michigan "Water-Winter Wonderland" license plate overlays, uncramped tire health card with streamlined 3-dot menu, and editable vehicle name.

**Architecture:** Update Compose UI components (`VehicleHeroCard`, `VehicleTireHealthCard`, `VehicleOverviewTab`, `VehicleDetailsScreen`) to support new visual styling and interactive edit dialogs that update Room DB entities via `VehicleDetailsViewModel.updateCar`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Room

**Spec:** `docs/superpowers/specs/2026-10-30-vehicle-page-refinements-design.md`

## Global Constraints
- `app/google-services.json` must be present for any Android compile/test tasks per `AGENTS.md`.
- No Room database migration needed: `CarEntity` already contains `vin`, `licensePlate`, `year`, `make`, `model`, and `trim`.
- Colors for Michigan plate: Deep Midnight Blue `#0B2245` canvas and Maize `#FFCB05` lettering/border.

## Review Focus
1. Blank/empty VIN or license plate handling (should display "Add VIN" / "ADD PLATE" placeholders instead of crashing or showing empty space).
2. Year validation when editing vehicle name (must parse to valid Int; reject invalid string).
3. Whitespace trimming and uppercase formatting for VIN and license plate strings.
4. Long tire name overflow handling (should wrap or ellipsize gracefully without pushing the 3-dot menu off-screen).
5. Fast click / dialog dismissal handling (dialogs cleanly close without orphaned state).

---

### Task 1: Update VehicleHeroCard with Sparkle Button, VIN Chip, and Michigan Plate

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleHeroCard.kt`

**Interfaces:**
- Consumes: `car: Car`, `onOpenPhotoCustomizer: () -> Unit`, `onEditVinClick: () -> Unit`, `onEditLicensePlateClick: () -> Unit`
- Produces: `VehicleHeroCard(car, onOpenPhotoCustomizer, onEditVinClick, onEditLicensePlateClick, modifier)`

- [ ] **Step 1: Update `VehicleHeroCard` signatures and parameters**
Add `onEditVinClick: () -> Unit` and `onEditLicensePlateClick: () -> Unit` callbacks to `VehicleHeroCard`.

- [ ] **Step 2: Replace "Photo AI" pill with circular sparkle-only button**
Replace the pill surface with a 36.dp circular frosted button containing `Icons.Filled.AutoAwesome` (contentDescription = "Customize Photo").

- [ ] **Step 3: Replace bottom overlay with VIN chip and Michigan Plate**
Remove the `${car.year} ${car.make} ${car.model}` and telemetry badge overlay. Add:
- Left: Frosted VIN chip showing `car.vin ?: "Add VIN"` with pencil icon, calling `onEditVinClick()`.
- Right: Michigan "Water-Winter Wonderland" styled plate with `#0B2245` background, `#FFCB05` border, "MICHIGAN" header, `car.licensePlate ?: "ADD PLATE"` center, and "WATER-WINTER WONDERLAND" footer, calling `onEditLicensePlateClick()`.

- [ ] **Step 4: Verify syntax & build**
Compile with `./gradlew compileDebugKotlin` to ensure no syntax errors in `VehicleHeroCard`.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleHeroCard.kt
git commit -m "feat(ui): update VehicleHeroCard with sparkle icon, VIN, and Michigan plate"
```

---

### Task 2: Uncramp VehicleTireHealthCard Header & Streamline Menu

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleTireHealthCard.kt`

**Interfaces:**
- Consumes: `car: Car`, `activeTire: Tire?`, `onLogRotationClick: () -> Unit`, `onEditIntervalClick: () -> Unit`, `onInstallNewTireClick: () -> Unit`
- Produces: `VehicleTireHealthCard(car, activeTire, onLogRotationClick, onEditIntervalClick, onInstallNewTireClick, modifier)`

- [ ] **Step 1: Simplify Header Row in `VehicleTireHealthCard`**
Remove `Button("Log Rotation")` from the header row. The header row should only contain:
- Left: Tire icon circle + Column with `"${tire.manufacturer} ${tire.model}"` and subtitle "Rotation interval: every X mi".
- Right: `IconButton` for `MoreVert` menu.

- [ ] **Step 2: Add "Log Tire Rotation" to DropdownMenu**
In the 3-dot dropdown menu, add a `DropdownMenuItem` for "Log Tire Rotation" with `Icons.Filled.Add` at the top of the menu items.

- [ ] **Step 3: Add contextual "+ Log" button in Rotation Due banner**
In the "Next Rotation Due" banner, add a compact tonal button `"+ Log"` when rotation is scheduled, urgent, or overdue so the user can still log a rotation in one tap.

- [ ] **Step 4: Verify syntax & build**
Compile with `./gradlew compileDebugKotlin` to ensure `VehicleTireHealthCard` builds cleanly.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/components/VehicleTireHealthCard.kt
git commit -m "feat(ui): uncramp tire health card header and streamline rotation actions"
```

---

### Task 3: Wire Callbacks in VehicleOverviewTab

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/tabs/VehicleOverviewTab.kt`

**Interfaces:**
- Consumes: `onEditVinClick: () -> Unit`, `onEditLicensePlateClick: () -> Unit`
- Passes to: `VehicleHeroCard`

- [ ] **Step 1: Add `onEditVinClick` and `onEditLicensePlateClick` to `VehicleOverviewTab`**
Add the two callback parameters to `VehicleOverviewTab` and forward them to `VehicleHeroCard`.

- [ ] **Step 2: Verify syntax & build**
Compile with `./gradlew compileDebugKotlin` to ensure clean compilation.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/tabs/VehicleOverviewTab.kt
git commit -m "feat(ui): wire VIN and license plate edit callbacks in VehicleOverviewTab"
```

---

### Task 4: Add Edit Dialogs & Editable Top Bar Title in VehicleDetailsScreen

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsScreen.kt`

**Interfaces:**
- Consumes: `viewModel: VehicleDetailsViewModel`, `carItem: Car`
- Produces: Edit dialogs for Vehicle Name, VIN, and License Plate, calling `viewModel.updateCar(...)`.

- [ ] **Step 1: Add state variables for edit dialogs**
Add `var showEditVehicleNameDialog by remember { mutableStateOf(false) }`, `var showEditVinDialog by remember { mutableStateOf(false) }`, and `var showEditLicensePlateDialog by remember { mutableStateOf(false) }`.

- [ ] **Step 2: Implement `EditVehicleNameDialog`**
Add an `AlertDialog` with fields for Year, Make, Model, and Trim. Validates Year as an integer, trims strings, and calls `viewModel.updateCar(carItem.copy(year = ..., make = ..., model = ..., trim = ...))`.

- [ ] **Step 3: Implement `EditVinDialog`**
Add an `AlertDialog` with an `OutlinedTextField` for VIN. Trims and uppercases input, and calls `viewModel.updateCar(carItem.copy(vin = it.ifBlank { null }))`.

- [ ] **Step 4: Implement `EditLicensePlateDialog`**
Add an `AlertDialog` with an `OutlinedTextField` for License Plate. Trims and uppercases input, and calls `viewModel.updateCar(carItem.copy(licensePlate = it.ifBlank { null }))`.

- [ ] **Step 5: Make TopAppBar title interactive**
In `TopAppBar`: wrap title text in a clickable row with an edit icon button (`Icons.Filled.Edit`) that triggers `showEditVehicleNameDialog = true`.

- [ ] **Step 6: Pass edit callbacks into `VehicleOverviewTab`**
Connect `onEditVinClick = { showEditVinDialog = true }` and `onEditLicensePlateClick = { showEditLicensePlateDialog = true }`.

- [ ] **Step 7: Verify compilation**
Compile with `./gradlew compileDebugKotlin`.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/vehicledetails/VehicleDetailsScreen.kt
git commit -m "feat(ui): add edit dialogs for vehicle name, VIN, and license plate"
```

---

### Task 5: Unit Testing & Verification

**Files:**
- Create: `app/src/test/kotlin/com/example/car_logger/VehicleDetailsEditTest.kt`

**Interfaces:**
- Verifies: `Car.copy()` validation, VIN / license plate normalization, year parsing logic.

- [ ] **Step 1: Write unit tests for vehicle editing logic**
Test:
- Valid year parsing and validation.
- VIN uppercase normalization and empty-to-null handling.
- License plate uppercase normalization and empty-to-null handling.
- Display name update when year, make, or model change.

- [ ] **Step 2: Run unit tests**
Run `./gradlew testDebugUnitTest --tests com.example.car_logger.VehicleDetailsEditTest` and verify all tests pass.

- [ ] **Step 3: Run full verification build**
Run `./gradlew assembleDebug` to verify end-to-end APK assembly.

- [ ] **Step 4: Commit**
```bash
git add app/src/test/kotlin/com/example/car_logger/VehicleDetailsEditTest.kt
git commit -m "test: add unit tests for vehicle details edit operations"
```
