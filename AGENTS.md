# Agent Guidelines & Project Memory

## Git Worktrees: Required Setup for Google Services

When working in or initializing a new `git worktree` for this project:

> [!IMPORTANT]
> `android/app/google-services.json` is ignored by `.gitignore` and **will not be copied over automatically** when a new `git worktree` is created.
>
> Without this file, the Android build (`flutter run`, `./gradlew assembleDebug`, etc.) will fail because `com.google.gms.google-services` is required in `android/app/build.gradle.kts`.

### Action Required for Future Agents:
Whenever creating or checking out a new `git worktree`:
1. Check if `android/app/google-services.json` exists in the worktree.
2. If missing, copy it from the primary repository into the worktree:
   ```bash
   cp /Users/bbowling/Developer/car-logging/android/app/google-services.json android/app/google-services.json
   ```
   Or if `git worktree` path is relative, locate the main working directory and copy `android/app/google-services.json` to `<worktree>/android/app/google-services.json`.
