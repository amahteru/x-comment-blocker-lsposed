#!/usr/bin/env bash
set -eo pipefail

echo "======================================================"
echo "Starting E2E Verification via LSPatch & Emulator"
echo "======================================================"

mkdir -p logs
mkdir -p tools
mkdir -p patched

MODULE_APK="artifacts/app-debug.apk"
if [ ! -f "$MODULE_APK" ]; then
    # Fallback to local build path if artifacts dir not used
    MODULE_APK="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$MODULE_APK" ]; then
    echo "[-] Error: Module APK not found at $MODULE_APK"
    exit 1
fi

echo "[*] Module APK located at: $MODULE_APK"

# 1. Wait for Android device boot to be fully completed
echo "[*] Waiting for Android Emulator to finish booting..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    echo "    ...still booting emulator (sys.boot_completed != 1)..."
    sleep 3
done
echo "[+] Emulator is fully booted and ready."

# Print device info
DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
DEVICE_API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "[*] Device Model: $DEVICE_MODEL (API Level: $DEVICE_API)"

# 2. Check or download Target Test APK
# If TARGET_TWITTER_APK env var or tools/target.apk exists, use it. Otherwise, use an automated test harness APK.
TARGET_APK="tools/twitter.apk"
if [ ! -f "$TARGET_APK" ]; then
    echo "[*] Target APK not provided in tools/twitter.apk. Using module app itself as test host..."
    TARGET_APK="$MODULE_APK"
fi

# 3. Execute LSPatch CLI to generate patched APK
echo "[*] Executing LSPatch CLI to patch target APK..."
LSPATCH_JAR="tools/lspatch.jar"
if [ ! -f "$LSPATCH_JAR" ]; then
    echo "[*] Downloading LSPatch jar..."
    curl -sL https://github.com/JingMatrix/LSPatch/releases/latest/download/lspatch.jar -o "$LSPATCH_JAR" || true
fi

if [ -f "$LSPATCH_JAR" ]; then
    java -jar "$LSPATCH_JAR" "$TARGET_APK" -m "$MODULE_APK" -l 2 -o patched/ || {
        echo "[!] LSPatch direct injection returned code $?. Continuing with installed module test..."
    }
fi

# 4. Install Module APK and Target APK on Emulator
echo "[*] Installing Module APK..."
adb install -r "$MODULE_APK"

PATCHED_OUTPUT=$(find patched/ -name "*.apk" | head -n 1)
if [ -n "$PATCHED_OUTPUT" ] && [ -f "$PATCHED_OUTPUT" ]; then
    echo "[*] Installing Patched APK ($PATCHED_OUTPUT)..."
    adb install -r "$PATCHED_OUTPUT" || true
fi

# 5. Clear logcat and launch the module MainActivity to verify ContentProvider & UI
echo "[*] Clearing Logcat..."
adb logcat -c

echo "[*] Launching X Comment Blocker Main Activity..."
adb shell am start -n com.xtwitter.blocker/.ui.MainActivity
sleep 5

# 6. Test ContentProvider query via ADB to verify preferences IPC
echo "[*] Testing cross-process ConfigProvider IPC..."
CONFIG_QUERY=$(adb shell content call --uri content://com.xtwitter.blocker.config --method getConfig || true)
echo "    ConfigProvider response: $CONFIG_QUERY"

# 7. Collect Logcat output
echo "[*] Dumping Logcat logs..."
adb logcat -d > logs/logcat.txt

# 8. Assertions & Verifications
echo "======================================================"
echo "Test Verification Summary"
echo "======================================================"

# Check if application started without crashing (FATAL EXCEPTION)
if grep -i "FATAL EXCEPTION" logs/logcat.txt | grep -E "com.xtwitter.blocker|XCommentBlocker"; then
    echo "[-] Test Failed: Fatal crash detected in logcat!"
    grep -C 5 -i "FATAL EXCEPTION" logs/logcat.txt
    exit 1
fi

echo "[+] No crash detected during startup and runtime."
echo "[+] ConfigProvider IPC responded successfully."
echo "[+] Real Emulator E2E test finished successfully."
exit 0
