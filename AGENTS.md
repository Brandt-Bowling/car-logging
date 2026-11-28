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
- **Verification**: Always run `./gradlew testDebugUnitTest lintDebug checkArchitectureGates assembleDebug` before opening or merging a PR.

## Modularity & Architecture Guardrails

- **Enforced limits (`./gradlew checkArchitectureGates`)**:
  - Production Kotlin files (`app/src/main/kotlin`): `<= 450` lines (warning above `350` lines).
  - Test Kotlin files (`app/src/test/kotlin`): `<= 650` lines.
  - `@Composable` functions: `<= 120` lines.
  - Legacy exceptions recorded in `config/architecture-gates-allowlist.txt` are strict ratchets (may shrink, never grow).
- **Guidance (code-reviewed)**:
  - Keep pure business logic functions `<= 60` lines, nesting depth `<= 4`, and parameter lists `<= 7` (group callbacks/state into `@Immutable` classes when larger).
- **Architecture & Compose Lint (`app/lint.xml` + `compose-lint-checks`)**:
  - Inject collaborators via `AppContainer` and `AppViewModelFactories` (no `AndroidViewModel`, no ad-hoc `OkHttpClient.Builder()` outside `AppContainer`).
  - Keep `ViewModel`s free of `android.content.Context` imports and expose immutable `StateFlow`/`SharedFlow` (no public `MutableStateFlow`).
  - Collect flows in Compose with `collectAsStateWithLifecycle()` (never `collectAsState()`).
  - Place pure math and domain calculations in `data/` or `services/` calculators with JVM unit tests.
  - Keep savings/work theme colors centralized in `ExpressiveThemeTokens`.
  - Follow Slack Compose lint rules (`ComposeViewModelForwarding`, `ComposeViewModelInjection`, `ComposeParameterOrder`, `ComposeMutableParameters`, `ComposePreviewPublic`, `ComposeMultipleContentEmitters`).
