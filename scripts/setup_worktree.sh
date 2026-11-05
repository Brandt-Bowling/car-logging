#!/usr/bin/env bash
set -euo pipefail

# Resolve the worktree root and the primary repository root
WORKTREE_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
GIT_COMMON_DIR="$(git rev-parse --git-common-dir 2>/dev/null || echo "")"

if [[ -n "$GIT_COMMON_DIR" && "$GIT_COMMON_DIR" != ".git" ]]; then
  MAIN_REPO_ROOT="$(cd "$GIT_COMMON_DIR/.." && pwd)"
else
  MAIN_REPO_ROOT="/Users/bbowling/Developer/car-logging"
fi

if [[ ! -f "$WORKTREE_ROOT/app/google-services.json" && -f "$MAIN_REPO_ROOT/app/google-services.json" ]]; then
  mkdir -p "$WORKTREE_ROOT/app"
  cp "$MAIN_REPO_ROOT/app/google-services.json" "$WORKTREE_ROOT/app/google-services.json"
  echo "Copied app/google-services.json from $MAIN_REPO_ROOT"
fi

if [[ ! -f "$WORKTREE_ROOT/local.properties" && -f "$MAIN_REPO_ROOT/local.properties" ]]; then
  cp "$MAIN_REPO_ROOT/local.properties" "$WORKTREE_ROOT/local.properties"
  echo "Copied local.properties from $MAIN_REPO_ROOT"
fi
