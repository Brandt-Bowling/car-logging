# API Key Developer Dashboard Links Design

**Date**: 2026-11-02  
**Status**: Ready for Approval  
**Scope**: Small Change (Settings UI)

## 1. Intent & Why
In the Settings screen under "API KEYS & INTEGRATIONS", users configure developer credentials for four external services:
1. Google AI Studio (Gemini 2.5 Flash receipt OCR & parsing)
2. Auto.dev (Vehicle specs decoding and photo retrieval)
3. Smartcar (GM / Chevy Blazer EV odometer telemetry)
4. Tesla Fleet API (Tesla Model 3 odometer telemetry)

Currently, finding where to obtain or view these API credentials requires navigating to external websites on one's own. Adding direct dashboard links into each credential dialog allows users to jump straight to the respective developer portal to create, retrieve, or manage their keys right when they are configuring them.

## 2. Goals, Non-Goals, Anti-Goals
- **Goals**:
  - Add contextual external link buttons into each of the four credential setup dialogs in `SettingsTab.kt`.
  - Launch the target developer console/dashboard in the device's default web browser using Android `Intent.ACTION_VIEW`.
  - Maintain clean, clutter-free Settings list rows.
- **Non-Goals**:
  - In-app WebView or OAuth flow changes (Smartcar and Tesla OAuth flows already exist and remain unchanged).
  - Storage/preference schema alterations (all keys and tokens remain in `PreferencesManager`).
- **Anti-Goals**:
  - Cluttering the main Settings list with multiple icon buttons per row.

## 3. Approaches Considered
- **Option A (Direct Row Action)**: Dual icon buttons on each row (`OpenInNew` and `Key`) in the main Settings list.
  - *Tradeoff*: Immediate 1-tap access from Settings, but crowds the trailing area of each row and risks accidental external browser launches when trying to tap the row to edit credentials.
- **Option B (In-Dialog Helper Action — Selected by User)**: Keep the Settings rows clean. Add an action button with `Icons.Filled.OpenInNew` inside each credential dialog (`Gemini`, `Auto.dev`, `Smartcar`, and `Tesla`).
  - *Tradeoff*: Requires opening the dialog first (2 taps), but provides maximum contextual relevance right where users are entering and validating their credentials, preserving clean Material 3 list rows.
- **Option C (Hybrid)**: Both row quick-link buttons and in-dialog helper links.
  - *Tradeoff*: Higher UI density and unnecessary redundancy when Option B already serves the primary key setup journey cleanly.

## 4. Detailed Component Changes (`SettingsTab.kt`)

### 4.1 URL Constants
Define clean, maintainable constants for the developer portal URLs:
- `URL_GEMINI_DASHBOARD = "https://aistudio.google.com/apikey"`
- `URL_AUTO_DEV_DASHBOARD = "https://auto.dev/dashboard"`
- `URL_SMARTCAR_DASHBOARD = "https://dashboard.smartcar.com/apps"`
- `URL_TESLA_DASHBOARD = "https://developer.tesla.com/dashboard"`

### 4.2 Helper Composable / Dialog Link Button
Introduce a reusable helper button inside `SettingsTab.kt`:
```kotlin
@Composable
private fun DashboardLinkButton(
    label: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
```

### 4.3 Dialog Updates
1. **`showGeminiKeyDialog`**: Place `DashboardLinkButton("Get API Key at Google AI Studio", URL_GEMINI_DASHBOARD)` below the description text and above the text input.
2. **`showAutoDevKeyDialog`**: Place `DashboardLinkButton("Get API Key at Auto.dev", URL_AUTO_DEV_DASHBOARD)` below the description text and above the text input.
3. **`showSmartcarDialog`**: Place `DashboardLinkButton("Open Smartcar Dashboard", URL_SMARTCAR_DASHBOARD)` above the Client ID field.
4. **`showTeslaDialog`**: Place `DashboardLinkButton("Open Tesla Developer Portal", URL_TESLA_DASHBOARD)` above the Client ID field.

## 5. Verification & Testing
1. Unit/UI check: Verify all 4 dialogs render correctly with the link buttons.
2. Intent handling: Verify that tapping each button fires an `Intent.ACTION_VIEW` with the intended URL.
3. Android compilation: Verify project builds successfully with `./gradlew assembleDebug` (with worktree `google-services.json` in place).
