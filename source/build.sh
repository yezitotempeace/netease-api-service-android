#!/bin/bash
set -e
# 按你的环境修改下面三行
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/android"
./gradlew assembleDebug --no-daemon
