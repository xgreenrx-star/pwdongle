#!/usr/bin/env bash
# Helper script to build the debug APK with the local JDK that includes jlink
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="/home/Commodore/java/jdk-17.0.9+9"
./gradlew assembleDebug
