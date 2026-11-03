# Vehicle Page Redesign Refinements Design

**Date**: 2026-10-30  
**Status**: Approved (Brainstorming Complete)

## 1. Intent & Goals
Address user feedback on the vehicle details screen redesign:
1. **Hero Sparkle Button**: Clean circular action button displaying only the sparkle icon (`Icons.Filled.AutoAwesome`), removing the "Photo AI" text pill.
2. **VIN Overlay**: Remove the duplicate vehicle name (`"${car.year} ${car.make} ${car.model}"`) from the hero image overlay and replace it with an editable VIN field / chip.
3. **License Plate Overlay**: Replace the "Tesla Fleet" badge with a realistic retro Michigan "Water-Winter Wonderland" plate badge (midnight blue background, maize/yellow text & border, "MICHIGAN" header, "WATER-WINTER WONDERLAND" footer). Tapping opens a quick edit dialog.
4. **Uncramp Tire Health Card**: Fix the header row where the tire title wrapped onto two lines next to a cramped "Log Rotation" button and 3-dot menu. The header row will house only the tire icon, full title, and interval on the left, and the 3-dot overflow menu on the right. The rotation log action will be contextualized in the rotation due banner and inside the 3-dot menu.
5. **Editable Vehicle Name**: Make the top app bar title editable, opening an edit dialog for Year, Make, Model, and Trim.

## 2. Non-Goals
- Changing database schema or adding new tables (all required fields `vin`, `licensePlate`, `year`, `make`, `model`, `trim` already exist in `CarEntity`).
- Modifying maintenance record logs or cost calculations.

## 3. Approaches Considered
- **Approach A (Selected)**: Frosted glass overlays on the hero image for VIN and authentic Michigan plate badge; streamlined single-line tire header with 3-dot overflow menu; tap-to-edit Material 3 dialogs.
- **Approach B**: Moving VIN and license plate to a metadata card beneath the hero image, and stacking tire actions into multiple rows. (Rejected: wasted vertical screen real estate).

## 4. Detailed Component Changes

### 4.1 VehicleHeroCard (`VehicleHeroCard.kt`)
- Change the top-right button into a 36.dp circular frosted button containing `Icons.Filled.AutoAwesome` (no text), triggering `onOpenPhotoCustomizer()`.
- Bottom overlay row:
  - Left: VIN chip displaying `car.vin ?: "Add VIN"` with edit icon. Click triggers `onEditVinClick()`.
  - Right: Michigan "Water-Winter Wonderland" styled plate:
    - Container: Deep navy/midnight blue (`Color(0xFF0B2245)` to `Color(0xFF06162F)`), rounded 6.dp corners, 1.5.dp border stroke in Maize (`Color(0xFFFFCB05)`).
    - Top text: "MICHIGAN" in maize bold, tracking/letter-spacing.
    - Center text: `car.licensePlate ?: "ADD PLATE"` in monospaced maize bold.
    - Bottom text: "WATER-WINTER WONDERLAND" in maize small bold.
    - Click triggers `onEditLicensePlateClick()`.

### 4.2 VehicleTireHealthCard (`VehicleTireHealthCard.kt`)
- Header row:
  - Left: Tire icon circle (38.dp) + Column with `tire.manufacturer tire.model` (single line or full width) and subtitle "Rotation interval: every X mi".
  - Right: Only `IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert) }`.
  - DropdownMenu items: "Log Tire Rotation", "Configure Rotation Interval", "Install New Tire Set".
- Rotation Due banner:
  - Inside the banner, when due or overdue, include a compact `Button` or tonal button `"+ Log"` to easily log rotation directly.

### 4.3 VehicleOverviewTab & VehicleDetailsScreen
- Add edit dialogs in `VehicleDetailsScreen`:
  1. `EditVehicleNameDialog`: Year, Make, Model, Trim text inputs with validation.
  2. `EditVinDialog`: VIN text input (uppercase, trimmed).
  3. `EditLicensePlateDialog`: License plate text input (uppercase, trimmed).
- TopAppBar title:
  - Displays `${carItem.year} ${carItem.make} ${carItem.model}` with an edit pencil button (`Icons.Filled.Edit`) or clickable row opening `EditVehicleNameDialog`.
- All updates persist reactively via `viewModel.updateCar(...)`.
