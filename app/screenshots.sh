#!/bin/bash

LOCALES=(
  "en-US"
  "fr-CA"
  "es-ES"
)
ADB=adb
PACKAGE=com.sigmanote.notes.debug
TEST_CLASS=com.sigmanote.notes.screenshot.Screenshots
TEST_RUNNER=
ADB_DEVICE=
SOURCE=/sdcard/Pictures/screenshot_
DESTINATION1=src/main/play/listings
DESTINATION2=graphics/phone-screenshots

export taking_screenshots=true

echo "Assembling app"
../gradlew assembleDebug
echo "Assembling androidTest"
../gradlew assembleDebugAndroidTest
echo "Installing androidTest"
../gradlew uninstallDebugAndroidTest installDebugAndroidTest

# get device name
echo "Waiting for device"
$ADB wait-for-device
if [ -z "$ADB_DEVICE" ]; then
  ADB_DEVICE=$($ADB devices | sed -n '2p' | awk '{ print $1 }')
fi
echo "Device name: $ADB_DEVICE"
ADBD="$ADB -s $ADB_DEVICE"

echo "Uninstalling test app"
$ADBD uninstall $PACKAGE

echo "Installing test app"
$ADBD install -r -g ./build/outputs/apk/debug/app-debug.apk

# get test runnner string
if [ -z "$TEST_RUNNER" ]; then
  echo "Obtaining test runner class"
  TEST_RUNNER=$($ADB shell pm list instrumentation | sed -n "s/^instrumentation:\($PACKAGE.*\) .*$/\1/p")
fi
echo "Test runner is: $TEST_RUNNER"

$ADBD shell settings put global sysui_demo_allowed 1

for locale in "${LOCALES[@]}"; do
  echo "Taking screenshots for locale $locale"
  if ! $ADBD shell am instrument --no-window-animation -w -e testLocale "$locale" -e endingLocale "en-US" \
    -e debug false -e class $TEST_CLASS -e package $PACKAGE "$TEST_RUNNER"; then
    echo "Instrumentation failed"
    exit 1
  fi

  echo "Copying screenshots to listing directory"
  mkdir -p $DESTINATION1/"$locale"/$DESTINATION2
  $ADBD pull $SOURCE/. $DESTINATION1/"$locale"/$DESTINATION2
  $ADBD shell rm -rf $SOURCE
done

export taking_screenshots=false

echo "DONE"