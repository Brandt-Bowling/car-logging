# Agent Guidelines & Project Memory

## Git Worktrees: Required Setup (`google-services.json` & `local.properties`)

Both `app/google-services.json` and `local.properties` are gitignored and are not copied automatically when a new `git worktree` is created. Without them, `./gradlew` builds fail (`com.google.gms.google-services` requires `app/google-services.json` and Android Gradle Plugin requires `sdk.dir` in `local.properties`).

Whenever working in a new `git worktree`, run:
```bash
./scripts/setup_worktree.sh
```
Or copy them manually from the primary repository:
```bash
cp /Users/bbowling/Developer/car-logging/app/google-services.json app/google-services.json
cp /Users/bbowling/Developer/car-logging/local.properties local.properties
```

## CLI & Tooling Notes

- **GitHub CLI (`gh`)**: Installed at `/opt/homebrew/bin/gh`.
- **Verification**: Always run `./gradlew testDebugUnitTest assembleDebug` before opening or merging a PR.
