#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 - <<'PYEOF'
import pathlib
import re
import sys

ROOT = pathlib.Path(".")
ALLOWLIST_PATH = ROOT / "config" / "architecture-gates-allowlist.txt"

MAIN_MAX_LINES = 450
MAIN_WARN_LINES = 350
TEST_MAX_LINES = 650
COMPOSABLE_MAX_LINES = 120

file_watermarks: dict[str, int] = {}
composable_watermarks: dict[str, int] = {}

if ALLOWLIST_PATH.exists():
    for raw_line in ALLOWLIST_PATH.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            print(f"[FAIL] Malformed allowlist entry: {line}", file=sys.stderr)
            sys.exit(1)
        key, val_str = line.rsplit("=", 1)
        key = key.strip()
        max_lines = int(val_str.strip())
        if "::" in key:
            composable_watermarks[key] = max_lines
        else:
            file_watermarks[key] = max_lines

failures: list[str] = []
warnings: list[str] = []

main_files = sorted(pathlib.Path("app/src/main/kotlin").rglob("*.kt"))
test_files = sorted(pathlib.Path("app/src/test/kotlin").rglob("*.kt"))

# Gate 1: Production Kotlin file ceiling (<= 450L, warn > 350L)
for path in main_files:
    rel = path.as_posix()
    count = len(path.read_text(encoding="utf-8").splitlines())
    watermark = file_watermarks.get(rel)
    if watermark is not None:
        if count > watermark:
            failures.append(
                f"[Gate 1] {rel} grew to {count} lines (legacy allowlist ceiling: {watermark}). "
                "Split it instead of growing allowlisted files."
            )
    elif count > MAIN_MAX_LINES:
        failures.append(
            f"[Gate 1] {rel} is {count} lines (limit: {MAIN_MAX_LINES}). "
            "Extract cohesive helpers/subcomponents so every production file stays <= 450 lines."
        )
    elif count > MAIN_WARN_LINES:
        warnings.append(f"[Gate 1 WARN] {rel} is {count} lines (soft target <= {MAIN_WARN_LINES})")

# Gate 2: Test Kotlin file ceiling (<= 650L)
for path in test_files:
    rel = path.as_posix()
    count = len(path.read_text(encoding="utf-8").splitlines())
    if count > TEST_MAX_LINES:
        failures.append(
            f"[Gate 2] {rel} is {count} lines (limit: {TEST_MAX_LINES}). "
            "Split tests into focused domain test suites."
        )

# Gates 3-8: Static architecture invariants
collect_as_state_re = re.compile(r"\.collectAsState\(")
android_vm_re = re.compile(r"\bAndroidViewModel\b")
public_msf_re = re.compile(r"(?m)^\s*(?:val|var)\s+[A-Za-z0-9_]+\s*(?::[^=]+)?=\s*MutableStateFlow\(")
okhttp_builder_re = re.compile(r"OkHttpClient\.Builder\(")
generativeai_re = re.compile(r"com\.google\.ai\.client\.generativeai")
hex_token_re = re.compile(r"0xFF(?:0F4020|D3F5DC|81E6A1|0B5F2A|C6F7D4|072711)\b", re.IGNORECASE)

for path in main_files:
    rel = path.as_posix()
    text = path.read_text(encoding="utf-8")

    for m in collect_as_state_re.finditer(text):
        line_no = text[:m.start()].count("\n") + 1
        failures.append(
            f"[Gate 3] {rel}:{line_no} uses .collectAsState(. Use .collectAsStateWithLifecycle() instead."
        )

    for m in android_vm_re.finditer(text):
        line_no = text[:m.start()].count("\n") + 1
        failures.append(
            f"[Gate 4] {rel}:{line_no} references AndroidViewModel. Inject dependencies via AppContainer instead."
        )

    if "/ui/" in rel:
        for m in public_msf_re.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            failures.append(
                f"[Gate 5] {rel}:{line_no} exposes public MutableStateFlow. Keep _state private and expose StateFlow."
            )

    if not rel.endswith("di/AppContainer.kt"):
        for m in okhttp_builder_re.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            failures.append(
                f"[Gate 6] {rel}:{line_no} instantiates OkHttpClient.Builder(). Reuse AppContainer's shared client."
            )

    if "/ui/theme/" not in rel:
        for m in hex_token_re.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            failures.append(
                f"[Gate 8] {rel}:{line_no} duplicates savings hex literal {m.group(0)}. Use ExpressiveThemeTokens."
            )

# Gate 7: Zero legacy generativeai references in app/ or gradle/
for root_name in ("app", "gradle"):
    root_dir = pathlib.Path(root_name)
    if not root_dir.exists():
        continue
    for path in sorted(root_dir.rglob("*")):
        if not path.is_file():
            continue
        if path.parts[:2] == ("app", "build"):
            continue
        if path.suffix not in {".kt", ".kts", ".toml", ".pro", ".xml"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for m in generativeai_re.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            failures.append(
                f"[Gate 7] {path.as_posix()}:{line_no} references deprecated com.google.ai.client.generativeai."
            )

# Gate 9: @Composable function length cap (<= 120L)
def strip_line_comment_and_strings(line: str) -> str:
    # Remove double-quoted strings and trailing // comments for accurate brace/paren counting.
    out = []
    i = 0
    in_str = False
    while i < len(line):
        ch = line[i]
        if in_str:
            if ch == "\\" and i + 1 < len(line):
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue
        if ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
            break
        if ch == '"':
            in_str = True
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)

for path in main_files:
    rel = path.as_posix()
    lines = path.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        if "@Composable" in strip_line_comment_and_strings(lines[i]):
            j = i + 1
            while j < len(lines) and "fun " not in strip_line_comment_and_strings(lines[j]):
                if "@Composable" in strip_line_comment_and_strings(lines[j]):
                    break
                j += 1
            if j < len(lines) and "fun " in strip_line_comment_and_strings(lines[j]):
                sig = re.search(r"\bfun\s+([A-Za-z0-9_]+)", lines[j])
                name = sig.group(1) if sig else "unknown"
                paren = 0
                seen_paren = False
                k = j
                while k < len(lines):
                    cleaned = strip_line_comment_and_strings(lines[k])
                    for ch in cleaned:
                        if ch == "(":
                            paren += 1
                            seen_paren = True
                        elif ch == ")":
                            paren -= 1
                    if seen_paren and paren <= 0:
                        if "=" in cleaned and "{" not in cleaned:
                            k = -1
                            break
                        while k < len(lines) and "{" not in strip_line_comment_and_strings(lines[k]):
                            if "=" in strip_line_comment_and_strings(lines[k]):
                                k = -1
                                break
                            k += 1
                        break
                    k += 1
                if k == -1 or k >= len(lines):
                    i = j + 1
                    continue
                depth = 0
                started = False
                end = k
                m = k
                while m < len(lines):
                    cleaned = strip_line_comment_and_strings(lines[m])
                    for ch in cleaned:
                        if ch == "{":
                            depth += 1
                            started = True
                        elif ch == "}":
                            depth -= 1
                    if started and depth <= 0:
                        end = m
                        break
                    m += 1
                length = end - j + 1
                comp_key = f"{rel}::{name}"
                watermark = composable_watermarks.get(comp_key)
                if watermark is not None:
                    if length > watermark:
                        failures.append(
                            f"[Gate 9] {comp_key} grew to {length} lines (legacy allowlist ceiling: {watermark}). "
                            "Extract subcomposables instead of growing allowlisted composables."
                        )
                elif length > COMPOSABLE_MAX_LINES:
                    failures.append(
                        f"[Gate 9] {comp_key} is {length} lines (limit: {COMPOSABLE_MAX_LINES}). "
                        "Decompose into focused helper composables <= 120 lines."
                    )
                i = max(end + 1, j + 1)
                continue
        i += 1

for w in warnings:
    print(w)

if failures:
    print(f"\nArchitecture gate check FAILED ({len(failures)} violation(s)):", file=sys.stderr)
    for err in failures:
        print(f"  - {err}", file=sys.stderr)
    sys.exit(1)

print(
    f"Architecture gates PASSED "
    f"({len(main_files)} main files, {len(test_files)} test files, "
    f"{len(file_watermarks)} file watermarks, {len(composable_watermarks)} composable watermarks)."
)
PYEOF
