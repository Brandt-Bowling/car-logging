# API Key Developer Dashboard Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add direct links to the respective developer dashboards (Google AI Studio, Auto.dev, Smartcar, and Tesla Fleet) inside each credential configuration dialog in Settings.

**Architecture:** Define provider dashboard URLs as clean constants (`DeveloperDashboardUrls`). Introduce a reusable `DashboardLinkButton` composable leveraging Jetpack Compose Material 3 `OutlinedButton` with `Icons.AutoMirrored.Filled.OpenInNew` and Android `Intent.ACTION_VIEW`. Embed this button into the Gemini, Auto.dev, Smartcar, and Tesla dialogs in `SettingsTab.kt`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Intents (`Intent.ACTION_VIEW`), JUnit4.

**Spec:** [`docs/superpowers/specs/2026-11-02-api-key-dashboard-links-design.md`](file:///Users/bbowling/.gemini/antigravity/worktrees/car-logging/add_api_key_dashboard_links/docs/superpowers/specs/2026-11-02-api-key-dashboard-links-design.md)

## Global Constraints
- Target platform: Android with Jetpack Compose Material 3.
- All dashboard links must use standard HTTPS URLs and open via Android's `Intent.ACTION_VIEW`.
- Settings list rows must remain clean with single trailing Key icon (Option B).
- Use `Icons.AutoMirrored.Filled.OpenInNew` to avoid deprecation warnings.
- Build and unit test runs require `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` and `sdk.dir` in `local.properties`.

## Review Focus
1. Gemini URL: must point to Google AI Studio API keys dashboard (`https://aistudio.google.com/apikey`).
2. Auto.dev URL: must point to Auto.dev dashboard (`https://auto.dev/dashboard`).
3. Smartcar URL: must point to Smartcar apps dashboard (`https://dashboard.smartcar.com/apps`).
4. Tesla Fleet URL: must point to Tesla Developer portal dashboard (`https://developer.tesla.com/dashboard`).
5. Intent resolution safety: verify URLs are valid URI schemes without runtime crashes on launch.

---

### Task 1: Dashboard URL Constants & URL Validity Unit Tests

**Files:**
- Create: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/DeveloperDashboardUrls.kt`
- Test: `app/src/test/kotlin/com/example/car_logger/DeveloperDashboardUrlsTest.kt`

**Interfaces:**
- Produces: `DeveloperDashboardUrls` object containing:
  - `const val GEMINI = "https://aistudio.google.com/apikey"`
  - `const val AUTO_DEV = "https://auto.dev/dashboard"`
  - `const val SMARTCAR = "https://dashboard.smartcar.com/apps"`
  - `const val TESLA = "https://developer.tesla.com/dashboard"`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/car_logger/DeveloperDashboardUrlsTest.kt`:
```kotlin
package com.example.car_logger

import com.example.car_logger.ui.screens.home.tabs.DeveloperDashboardUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class DeveloperDashboardUrlsTest {

    @Test
    fun testDeveloperDashboardUrlsAreValidHttpsUris() {
        val urls = listOf(
            DeveloperDashboardUrls.GEMINI,
            DeveloperDashboardUrls.AUTO_DEV,
            DeveloperDashboardUrls.SMARTCAR,
            DeveloperDashboardUrls.TESLA
        )

        for (urlString in urls) {
            val uri = URI.create(urlString)
            assertEquals("https", uri.scheme)
            assertTrue("Host should not be blank: $urlString", !uri.host.isNullOrBlank())
        }

        assertEquals("https://aistudio.google.com/apikey", DeveloperDashboardUrls.GEMINI)
        assertEquals("https://auto.dev/dashboard", DeveloperDashboardUrls.AUTO_DEV)
        assertEquals("https://dashboard.smartcar.com/apps", DeveloperDashboardUrls.SMARTCAR)
        assertEquals("https://developer.tesla.com/dashboard", DeveloperDashboardUrls.TESLA)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest --tests "com.example.car_logger.DeveloperDashboardUrlsTest"
```
Expected: FAIL (unresolved reference: `DeveloperDashboardUrls`).

- [ ] **Step 3: Implement `DeveloperDashboardUrls` in `DeveloperDashboardUrls.kt`**

Create `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/DeveloperDashboardUrls.kt`:
```kotlin
package com.example.car_logger.ui.screens.home.tabs

object DeveloperDashboardUrls {
    const val GEMINI = "https://aistudio.google.com/apikey"
    const val AUTO_DEV = "https://auto.dev/dashboard"
    const val SMARTCAR = "https://dashboard.smartcar.com/apps"
    const val TESLA = "https://developer.tesla.com/dashboard"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest --tests "com.example.car_logger.DeveloperDashboardUrlsTest"
```
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/DeveloperDashboardUrls.kt app/src/test/kotlin/com/example/car_logger/DeveloperDashboardUrlsTest.kt
git commit -m "feat(settings): add developer dashboard URL constants and tests"
```

---

### Task 2: Create `DashboardLinkButton` & Integrate into Gemini and Auto.dev Dialogs

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt`

**Interfaces:**
- Consumes: `DeveloperDashboardUrls.GEMINI`, `DeveloperDashboardUrls.AUTO_DEV`
- Produces: `DashboardLinkButton(label: String, url: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Add `DashboardLinkButton` composable to `SettingsTab.kt`**

Add the helper composable in `SettingsTab.kt`:
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
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
```

Ensure `import androidx.compose.material.icons.automirrored.filled.OpenInNew` is added.

- [ ] **Step 2: Add dashboard link button to Gemini API Key Dialog**

In `showGeminiKeyDialog`, insert `DashboardLinkButton` below the explanatory `Text` and above `OutlinedTextField`:
```kotlin
DashboardLinkButton(
    label = "Get API Key (Google AI Studio)",
    url = DeveloperDashboardUrls.GEMINI
)
Spacer(modifier = Modifier.height(12.dp))
```

- [ ] **Step 3: Add dashboard link button to Auto.dev API Key Dialog**

In `showAutoDevKeyDialog`, insert `DashboardLinkButton` below the explanatory `Text` and above `OutlinedTextField`:
```kotlin
DashboardLinkButton(
    label = "Get API Key (Auto.dev Dashboard)",
    url = DeveloperDashboardUrls.AUTO_DEV
)
Spacer(modifier = Modifier.height(12.dp))
```

- [ ] **Step 4: Run unit tests and compilation check**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL without errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt
git commit -m "feat(settings): add dashboard link buttons to Gemini and Auto.dev dialogs"
```

---

### Task 3: Integrate `DashboardLinkButton` into Smartcar and Tesla Fleet Dialogs

**Files:**
- Modify: `app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt`

**Interfaces:**
- Consumes: `DeveloperDashboardUrls.SMARTCAR`, `DeveloperDashboardUrls.TESLA`

- [ ] **Step 1: Add dashboard link button to Smartcar Setup Dialog**

In `showSmartcarDialog`, add `DashboardLinkButton` at the top of the dialog content `Column`:
```kotlin
DashboardLinkButton(
    label = "Open Smartcar Dashboard",
    url = DeveloperDashboardUrls.SMARTCAR
)
```

- [ ] **Step 2: Add dashboard link button to Tesla Fleet Setup Dialog**

In `showTeslaDialog`, add `DashboardLinkButton` at the top of the dialog content `Column`:
```kotlin
DashboardLinkButton(
    label = "Open Tesla Developer Portal",
    url = DeveloperDashboardUrls.TESLA
)
```

- [ ] **Step 3: Run compilation and test suite**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
```
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/example/car_logger/ui/screens/home/tabs/SettingsTab.kt
git commit -m "feat(settings): add dashboard link buttons to Smartcar and Tesla dialogs"
```

---

### Task 4: Full App Build & Verification

**Files:**
- Verify: Full project build (`./gradlew assembleDebug`)

- [ ] **Step 1: Run complete Android assembleDebug build**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL and `app-debug.apk` generated.

- [ ] **Step 2: Run all unit tests**

Run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
```
Expected: All tests pass.
