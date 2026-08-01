# Firebase App Distribution Guide

This document outlines how to distribute new test builds of **Car Logger** to Firebase App Distribution for internal testing.

## App Configuration
- **Firebase Project ID**: `car-logger-track`
- **Android App ID**: `1:28790911573:android:0e08bfb1f94d5b29b6dadc`

---

## 🚀 Quick Local Distribution (Recommended)

You can push a build in seconds directly from your terminal using the helper script in `scripts/distribute.sh`.

### Prerequisites
1. **Firebase CLI**: Make sure `firebase-tools` is installed on your system.
   ```bash
   npm install -g firebase-tools
   ```
2. **Log In**: Ensure you are authenticated with Firebase.
   ```bash
   firebase login
   ```

### Command Usage

#### 1. Standard Release (Builds APK + Deploys with latest Git commit message)
```bash
./scripts/distribute.sh
```

#### 2. Custom Release Notes
```bash
./scripts/distribute.sh "Added tire pressure tracking module and bug fixes"
# OR using the --notes flag:
./scripts/distribute.sh --notes "Fix braking calculation issue"
```

#### 3. Specify Tester Emails or Tester Groups
```bash
./scripts/distribute.sh --testers "tester1@example.com,tester2@example.com"
./scripts/distribute.sh --groups "qa-team,beta-testers"
```

#### 4. Fast Upload (Skip Rebuilding APK)
If you already built the release APK (`flutter build apk --release`), you can skip the compilation step:
```bash
./scripts/distribute.sh --skip-build "Quick re-upload"
```

---

## 🤖 GitHub Actions CI/CD Deployment

An automated workflow is configured at `.github/workflows/firebase_app_distribution.yml`.

### How It Works:
1. Every push to the `main` branch automatically triggers a Flutter release build and uploads it to Firebase App Distribution.
2. You can also manually trigger builds from the **Actions** tab in GitHub using the **Run workflow** button (which lets you input custom release notes).

### Setup Required on GitHub Repository:
1. Generate a Service Account JSON key in [Google Cloud Console / Firebase Console](https://console.firebase.google.com/project/car-logger-track/settings/serviceaccounts/adminsdk) with **Firebase App Distribution Admin** permissions.
2. Add the JSON key content to your GitHub repository secret named `FIREBASE_SERVICE_ACCOUNT` under **Settings > Secrets and variables > Actions**.
