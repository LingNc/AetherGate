#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
BUILD_DIR="$ROOT_DIR/build"

# Configuration for AetherGate structure
PLUGIN_MODULE="$ROOT_DIR/plugin"
PLUGIN_POM="$PLUGIN_MODULE/pom.xml"
RESOURCE_PACK_DIR="$ROOT_DIR/resource_pack" # AetherGate uses 'resource_pack'

# Clean and Init
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# ---------------------------------------------------------
# 1. Extract Version
# ---------------------------------------------------------
if [[ -f "$PLUGIN_POM" ]]; then
  # Attempt to extract version from plugin/pom.xml
  VERSION="$(grep -m 1 -oP '(?<=<version>).*?(?=</version>)' "$PLUGIN_POM" 2>/dev/null || true)"
else
  echo "Warning: plugin/pom.xml not found."
  VERSION=""
fi

if [[ -z "$VERSION" ]]; then
  VERSION="unversioned"
  echo "Version could not be detected, using 'unversioned'."
fi

echo "Detected Project Version: $VERSION"

# ---------------------------------------------------------
# 2. Build Plugin
# ---------------------------------------------------------
echo "Building Plugin..."
( cd "$PLUGIN_MODULE" && mvn -q -DskipTests package )

# Construct expected Jar path based on Maven conventions
# ArtifactId is 'aether-gate' based on the provided pom.xml
EXPECTED_JAR_NAME="aether-gate-${VERSION}.jar"
UNSHADED_JAR_PATH="$PLUGIN_MODULE/target/original-$EXPECTED_JAR_NAME"

# Prefer the unshaded jar (original-*) to keep dependencies out of the final artifact
if [[ ! -f "$UNSHADED_JAR_PATH" ]]; then
  UNSHADED_JAR_PATH=$(find "$PLUGIN_MODULE/target" -maxdepth 1 -name "original-*.jar" | head -n 1 || true)
fi

# If all else fails, fall back to any jar (last resort)
if [[ -z "$UNSHADED_JAR_PATH" || ! -f "$UNSHADED_JAR_PATH" ]]; then
  UNSHADED_JAR_PATH=$(find "$PLUGIN_MODULE/target" -maxdepth 1 -name "*.jar" ! -name "original-*" | head -n 1 || true)
fi

if [[ -z "$UNSHADED_JAR_PATH" || ! -f "$UNSHADED_JAR_PATH" ]]; then
  echo "ERROR: Plugin jar not found in $PLUGIN_MODULE/target/" >&2
  exit 1
fi

PLUGIN_FINAL_NAME="AetherGate_Plugin_${VERSION}.jar"
cp "$UNSHADED_JAR_PATH" "$BUILD_DIR/$PLUGIN_FINAL_NAME"

# ---------------------------------------------------------
# 3. Build Resource Pack
# ---------------------------------------------------------
RESOURCE_PACK_FINAL_NAME="AetherGate_ResourcePack_${VERSION}.zip"

if [[ -d "$RESOURCE_PACK_DIR" && -f "$RESOURCE_PACK_DIR/pack.mcmeta" ]]; then
    echo "Building Resource Pack..."
    rm -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME"
    ( cd "$RESOURCE_PACK_DIR" && zip -rq "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" . )
else
    echo "WARNING: Resource pack directory or pack.mcmeta not found at $RESOURCE_PACK_DIR. Skipping."
fi

# ---------------------------------------------------------
# 4. Validation
# ---------------------------------------------------------
ensure_packmeta_in_zip() {
  local zipfile="$1"
  if [[ ! -f "$zipfile" ]]; then return 1; fi

  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$zipfile" | grep -qx "pack.mcmeta" || return 1
  else
    unzip -l "$zipfile" | awk 'NR>3 {print $4}' | grep -qx "pack.mcmeta" || return 1
  fi
  return 0
}

# Only validate if the file was actually created
if [[ -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" ]]; then
    if ! ensure_packmeta_in_zip "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME"; then
      echo "ERROR: $BUILD_DIR/$RESOURCE_PACK_FINAL_NAME does not contain pack.mcmeta at the root" >&2
      exit 1
    fi
fi

# ---------------------------------------------------------
# 5. Summary
# ---------------------------------------------------------
echo "------------------------------------------------"
echo "Build complete. Outputs in build/:"
echo " - Plugin:       $PLUGIN_FINAL_NAME"
if [[ -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" ]]; then
    echo " - ResourcePack: $RESOURCE_PACK_FINAL_NAME"
fi
echo "------------------------------------------------"