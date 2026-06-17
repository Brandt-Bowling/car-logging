# Car Logger

A modern Flutter application for tracking vehicle maintenance, tire logs, and general documentation ("Glovebox"). It integrates with Google Drive for automated synchronization and uses Gemini AI to parse uploaded receipt images and PDFs.

---

## Features

- **Maintenance Logging:** Keep a record of all repairs, service actions, and odometer readings.
- **Tire Tracking:** Log tire rotations, swaps, and replacements.
- **Glovebox Document Storage:** Store receipt scans and important documents.
- **Google Drive Sync:** Backup and sync your documents directly with a dedicated Google Drive folder.
- **Automated Receipt Parsing:** Leveraging Gemini AI, the app automatically scans and extracts dates, odometer readings, and costs from receipt files synced with Google Drive.

---

## Getting Started

### 1. Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (v3.11.0 or higher recommended)
- A Google Account (for Drive backup & Gemini AI key)

### 2. Run the App
To start the app in development mode:
```bash
flutter pub get
flutter run
```

---

## Receipt Scanning Setup (Gemini AI Key)

By default, the app uses a smart regex parser based on receipt filenames to extract dates, odometer readings, and costs offline. To enable advanced, fully automated scanning of receipt images and PDFs, you can configure a free Gemini API key.

### How to Get a Free Gemini API Key:

1. **Go to Google AI Studio:**
   Navigate to [Google AI Studio (aistudio.google.com)](https://aistudio.google.com/).

2. **Sign In:**
   Log in using your preferred Google account.

3. **Create API Key:**
   - Click the **Get API Key** button (usually at the top left).
   - Click **Create API Key**.
   - You can create it in a new Google Cloud project or associate it with an existing one.
   - Choose the **Free Tier**, which is ideal for personal, non-commercial use (currently offering generous free limits).

4. **Copy the Key:**
   Copy the generated API Key (it will start with `AIzaSy...`).

### How to Configure the Key in the App:

1. Open the **Car Logger** app.
2. Navigate to the **Sync Settings** page (via the cloud icon or settings menu).
3. Scroll to the **Receipt Scanning (Gemini AI)** card and click **Setup Key** or **Configure**.
4. Paste your copied API Key in the field and click **Save**.
5. The status will update to **Active (Gemini 1.5 Flash)**. Future receipt syncs will automatically use the AI model to extract details.

---

## Google Drive Synchronization

To back up and sync your receipt images/PDFs:
1. Tap **Sign In with Google** on the Sync Settings screen.
2. Select or create a folder in your Google Drive where receipts will be stored (e.g., `Car Receipts`).
3. Place your receipt files in that folder. The app will sync them and automatically run receipt parsing using Gemini AI (if configured) or the smart filename parser fallback.
