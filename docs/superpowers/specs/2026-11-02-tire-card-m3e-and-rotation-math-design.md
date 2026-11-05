# M3 Expressive Tire Health Card & Rotation Math Design

**Date**: 2026-11-02  
**Status**: Pending Approval  
**Size**: Small Change

## 1. Intent & Why
The tire card on `VehicleOverviewTab` felt horizontally cramped because its inner banner squeezed an icon, multi-line text, a status pill, and a log button into a single row, and its copy felt overly verbose. In addition, the rotation interval calculation ignored `"Tire Rotation"` entries from `MaintenanceRecord` logs and fell back to a synthetic `currentOdo - 5000` placeholder when no `TireEntity` existed.

## 2. Goals, Non-Goals, and Anti-Goals
- **Goals**:
  - Redesign `VehicleTireHealthCard` using the selected **Option A2** M3 Expressive stacked layout with a clean icon-free header (matching `VehicleCostHistoryCard`), natural concise copy, an M3 Expressive wavy progress indicator, and a connected 3-button action bar (`+ Rotate`, `Interval`, `New Tires`).
  - Fix tire rotation interval calculations across `VehicleTireHealthCard` and `HomeViewModel.scheduledServices` so they read the latest tire rotation from `MaintenanceRecord` logs as well as `Tire.lastRotationOdometer`.
  - Keep tire rotation actions and maintenance logs unified so logging a rotation from the tire card also records a `"Tire Rotation"` maintenance log.
- **Non-Goals**:
  - Database schema migrations (existing `tires` and `maintenance_records` tables already store all needed fields).
- **Anti-Goals**:
  - Robotic or repetitive labels (e.g., `"Rotation interval: every X mi"`, `"Target: X mi • Last: Y mi ago"`), or cramped multi-element horizontal banners.

## 3. Constraints
- Maintain compatibility with existing Room entities (`TireEntity`, `MaintenanceRecordEntity`, `CarEntity`) and Compose Material 3 theme tokens (`ExpressiveMotionTokens`).

## 4. Approaches Considered
- **Approach A1 (Same-View Bento Card + SplitButton)**: Two side-by-side metric tiles for Rotation and Tread with a split button in the header. Trades off horizontal width per metric on narrower screens.
- **Approach A2 (Selected — Same-View Focused Card with Wavy Progress & Connected ButtonGroup)**: Full-width stacked sections with an icon-free header, M3 Expressive wavy progress bar, concise natural copy, and a 3-button connected action group at the bottom. Eliminates horizontal cramping while keeping everything on `VehicleOverviewTab`.
- **Approach B1/B2 (Stand-Alone Tires Subview in Floating Toolbar)**: Restores a 3rd tab in `VehicleFloatingToolbar` and leaves a summary banner on Overview. Rejected in favor of keeping all vehicle overview info on a single view with A2's cleaner layout.

## 5. Design Decisions
1. **Header Row (Icon-Free)**:
   - Left: Tire title (`"${tire.manufacturer} ${tire.model}"`) and concise subtitle (`"Last rotated at X mi"`, or `"Installed at X mi"` if no rotation has been logged).
   - Right: Status pill (`"In X mi"`, `"Due in X mi"`, or `"X mi overdue"`).
2. **Middle Container (M3 Expressive Wavy Progress & Tread Summary)**:
   - Top row: `"Rotate at X mi"` on the left, `"Every Y mi"` on the right.
   - Center: Animated M3 Expressive wavy progress bar showing progress through the current rotation interval (`(milesSinceLastRotation / rotationInterval).coerceIn(0f, 1f)`).
   - Bottom row: `"Tread: X% (~Y mi left)"` on the left, `"Installed at Z mi"` on the right.
3. **Bottom Connected Action Group**:
   - 3-column connected M3 Expressive button row with asymmetric outer/inner corner radii (16.dp outer corners, 6.dp inner corners):
     - Leading filled button: `"+ Rotate"` (`onLogRotationClick`)
     - Middle tonal/surface button: `"Interval"` (`onEditIntervalClick`)
     - Trailing tonal/surface button: `"New Tires"` (`onInstallNewTireClick`)
4. **Unified Tire Rotation Math (`TireRotationCalculator`)**:
   - A shared domain helper identifies tire rotation records in `List<MaintenanceRecord>` (matching case-insensitive `"tire rotation"`, `"rotate tires"`, `"rotated tires"`, or `"tire"` + `"rotat"` in `title` or `description`).
   - `lastRotationOdometer` resolves to `maxOfNotNull(activeTire?.lastRotationOdometer, latestRotationRecord?.odometer)` (filtering out rotation records older than `activeTire.odometerInstalled` when an active tire set was installed more recently), falling back to `activeTire?.odometerInstalled` or `0` (removing the synthetic `currentOdo - 5000` default).
   - Logging a rotation via `VehicleDetailsViewModel.logTireRotation(date, odometer)` updates `TireEntity` **and** inserts a `"Tire Rotation"` `MaintenanceRecordEntity` (plus updates `CarEntity.odometer` if `odometer > car.odometer`).

## 6. Left to the Builder
- Exact canvas wave amplitude/wavelength parameters for the custom M3 Expressive wavy progress bar composable so it animates smoothly with `ExpressiveMotionTokens`.
