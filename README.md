# Car Logger ("Garage Journal")

A modern, native Android application built with **Kotlin** and **Jetpack Compose** following Google's official **Material 3 Expressive** design guidelines. It manages vehicle maintenance history, dynamic service intervals, tire lifecycle tracking, and glovebox document storage, with automated Google Drive receipt syncing and Gemini 2.5 Flash receipt parsing.

---

## Features

- **Material 3 Expressive UI:** Built strictly according to Google's canonical M3 Expressive specifications, featuring dynamic theming (`dynamicLightColorScheme` / `dynamicDarkColorScheme`), the signature enveloped body canvas sheet (28dp top radius resting on outer shell with zero bleed), expressive typography, and spring motion.
- **Refactored Vehicle View:** Replaced standard bottom navigation with an M3 **Floating Toolbar** for subviews (Maintenance, Tires, Glovebox) with an attached/docked Floating Action Button for adding records.
- **Maintenance & Dynamic Scheduler:** Tracks all service events, logs mileage, and calculates upcoming intervals (Oil Change, Tire Rotation, Cabin/Engine Filters, Brake Fluid Flush, Spark Plugs) with urgent alerts (<= 500 miles remaining).
- **Tire Lifecycle Management:** Active tire set tracking with circular progress tread wear gauge, estimated remaining miles, and rotation logging.
- **Glovebox Document Storage:** Store local registration, insurance card, and owner's manual copies with native Android viewing and quick odometer editing.
- **Automated Receipt Parsing (Gemini AI):** Uses the official Google Gen AI Android SDK (`gemini-2.5-flash`) for multi-modal receipt extraction, with smart regex filename fallback.
- **Google Drive Sync:** Direct Google Play Services Auth and Drive v3 integration to browse folders and sync receipt documents.
- **Zero-Loss Data Migration:** Built-in `FlutterDataMigrator` automatically detects existing `FlutterSharedPreferences` and imports all previously saved vehicles, service records, and settings into Jetpack Room on first launch.

---

## Project Structure

```
├── app/
│   ├── build.gradle.kts           # App-level build script (Compose, Room, Firebase, DataStore)
│   ├── debug.keystore             # Keystore used for debug and release builds (for update continuity)
│   ├── google-services.json       # Firebase configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── kotlin/com/example/car_logger/
│   │   │       ├── CarLoggerApplication.kt
│   │   │       ├── MainActivity.kt
│   │   │       ├── data/          # Room DB, Entities, DAOs, DataStore, Migration, Vehicle Database
│   │   │       ├── services/      # AutoDevService, GoogleDriveService, GeminiAiService
│   │   │       └── ui/            # Jetpack Compose Screens, M3 Floating Toolbar, Theme, Canvas
│   │   └── test/                  # Unit test suites
├── gradle/
│   ├── libs.versions.toml         # Gradle Version Catalog
│   └── wrapper/
├── build.gradle.kts               # Root build script
├── settings.gradle.kts            # Root settings script
├── scripts/
│   └── distribute.sh              # Local Firebase App Distribution helper script
└── .github/workflows/
    └── firebase_app_distribution.yml # CI/CD automated release pipeline
```

---

## Building & Running

### Requirements
- Android SDK 35 (compileSdk 35, targetSdk 35, minSdk 26)
- JDK 17 or JDK 21 (e.g. bundled with Android Studio)
- Gradle 8.14 (managed via `./gradlew`)

### Compile & Test
```bash
# Run unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

The output release APK is generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## Firebase App Distribution

Deploy release builds directly to registered testers:

```bash
# Build & deploy release APK with latest git commit message
./scripts/distribute.sh

# Deploy with custom release notes
./scripts/distribute.sh "Updated M3 Expressive floating toolbar and tire wear gauge"
```
