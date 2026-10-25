#!/usr/bin/env bash
#
# JVM unit tests for the classes that do not touch the Android framework. They compile
# against the JDK alone, so a class that imports android.* cannot be listed here.
#
#   tools/unit-tests.sh
#
# Add a class to PURE when it gains tests, and its test class to UnitTests.main.

set -euo pipefail
cd "$(dirname "$0")/.."

PURE=(
  BannerColor.java
  Flavor.java
  Motion.java
  Palette.java
  Profiles.java
  SettingsTransfer.java
  Wave.java
)

rm -rf build-tests
mkdir -p build-tests
SRC=()
for f in "${PURE[@]+"${PURE[@]}"}"; do SRC+=("src/com/homestead/launcher/$f"); done
javac --release 11 -nowarn -encoding UTF-8 -d build-tests "${SRC[@]+"${SRC[@]}"}" tests/unit/*.java
java -cp build-tests com.homestead.launcher.UnitTests
