#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_DIR=$(dirname -- "$APP_DIR")
export JAVA_HOME="$WORKSPACE_DIR/toolchain/jdk8/Contents/Home"
export GRADLE_USER_HOME="$APP_DIR/.gradle-user"
printf 'sdk.dir=%s\n' "$WORKSPACE_DIR/toolchain/android-sdk" > "$APP_DIR/local.properties"
mkdir -p "$APP_DIR/test-output"
JAVA_SOURCE="$APP_DIR/app/src/main/java/com/github/ma1co/pmcademo/app"
"$JAVA_HOME/bin/javac" -d "$APP_DIR/test-output" "$JAVA_SOURCE/LiveViewValues.java" "$JAVA_SOURCE/CapturePlan.java" "$JAVA_SOURCE/LuminanceHistogram.java" "$JAVA_SOURCE/ShutterDisplay.java" "$APP_DIR/tests/LiveViewValuesTest.java" "$APP_DIR/tests/CapturePlanTest.java" "$APP_DIR/tests/LuminanceHistogramTest.java" "$APP_DIR/tests/ShutterDisplayTest.java"
"$JAVA_HOME/bin/java" -cp "$APP_DIR/test-output" com.github.ma1co.pmcademo.app.LiveViewValuesTest
"$JAVA_HOME/bin/java" -cp "$APP_DIR/test-output" com.github.ma1co.pmcademo.app.CapturePlanTest
"$JAVA_HOME/bin/java" -cp "$APP_DIR/test-output" com.github.ma1co.pmcademo.app.LuminanceHistogramTest
"$JAVA_HOME/bin/java" -cp "$APP_DIR/test-output" com.github.ma1co.pmcademo.app.ShutterDisplayTest
if [ -f "$APP_DIR/qa/api-versions.xml" ]; then
    "$WORKSPACE_DIR/toolchain/gradle/gradle-3.5/bin/gradle" -p "$APP_DIR" --offline --no-daemon "-DLINT_API_DATABASE=$APP_DIR/qa/api-versions.xml" assembleDebug lintDebug
else
    "$WORKSPACE_DIR/toolchain/gradle/gradle-3.5/bin/gradle" -p "$APP_DIR" --offline --no-daemon assembleDebug
fi
