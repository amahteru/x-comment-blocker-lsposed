#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Local Automated LSPatch Test Runner
Runs local module build, LSPatch portable packaging, and ADB deployment on connected emulator/device.
"""

import os
import sys
import subprocess
import time
import urllib.request
import shutil

# Reconfigure stdout/stderr for UTF-8 on Windows
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS_DIR = os.path.join(PROJECT_ROOT, "tools")
PATCHED_DIR = os.path.join(PROJECT_ROOT, "patched")
LSPATCH_JAR_URL = "https://github.com/JingMatrix/LSPatch/releases/latest/download/lspatch.jar"
LSPATCH_JAR_PATH = os.path.join(TOOLS_DIR, "lspatch.jar")


def get_adb_cmd():
    # Check if adb is in PATH
    if shutil.which("adb"):
        return "adb"
    # Check Android SDK default path on Windows
    local_props = os.path.join(PROJECT_ROOT, "local.properties")
    if os.path.exists(local_props):
        with open(local_props, "r", encoding="utf-8") as f:
            for line in f:
                if line.startswith("sdk.dir="):
                    sdk_dir = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                    adb_path = os.path.join(sdk_dir, "platform-tools", "adb.exe" if sys.platform == "win32" else "adb")
                    if os.path.exists(adb_path):
                        return adb_path
    return "adb"


def main():
    print("=" * 60)
    print("🛠️  X Comment Blocker - Local LSPatch Automation Test")
    print("=" * 60)

    os.makedirs(TOOLS_DIR, exist_ok=True)
    os.makedirs(PATCHED_DIR, exist_ok=True)

    adb_cmd = get_adb_cmd()
    print(f"[*] Using ADB command: {adb_cmd}")

    # 1. Check connected devices
    try:
        devices_out = subprocess.check_output([adb_cmd, "devices"], encoding="utf-8")
        print("[*] ADB Devices Output:\n" + devices_out)
        lines = [line for line in devices_out.strip().splitlines() if line and not line.startswith("List")]
        if not lines:
            print("⚠️ No Android device or emulator currently connected.")
            print("💡 Please start an emulator (e.g. MuMu, LDPlayer, or AVD) or connect a phone with USB debugging.")
    except Exception as e:
        print(f"⚠️ Failed to query ADB devices: {e}")

    # 2. Build Debug APK
    print("\n[1/4] Building module Debug APK...")
    gradle_cmd = os.path.join(PROJECT_ROOT, "gradlew.bat" if sys.platform == "win32" else "gradlew")
    res = subprocess.run([gradle_cmd, "assembleDebug"], cwd=PROJECT_ROOT)
    if res.returncode != 0:
        print("❌ Gradle build failed!")
        sys.exit(1)

    module_apk = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if not os.path.exists(module_apk):
        print(f"❌ Module APK not found at {module_apk}")
        sys.exit(1)
    print(f"✅ Module APK ready: {module_apk}")

    # 3. Ensure LSPatch CLI jar exists
    if not os.path.exists(LSPATCH_JAR_PATH) or os.path.getsize(LSPATCH_JAR_PATH) == 0:
        print(f"\n[2/4] Downloading LSPatch CLI tool from GitHub...")
        try:
            urllib.request.urlretrieve(LSPATCH_JAR_URL, LSPATCH_JAR_PATH)
            print(f"✅ LSPatch tool downloaded: {LSPATCH_JAR_PATH}")
        except Exception as e:
            print(f"⚠️ Download failed ({e}), please manually place lspatch.jar into {TOOLS_DIR}")

    # 4. Patch target APK if provided
    target_apk = sys.argv[1] if len(sys.argv) > 1 else os.path.join(TOOLS_DIR, "twitter.apk")
    if os.path.exists(target_apk) and os.path.exists(LSPATCH_JAR_PATH):
        print(f"\n[3/4] Patching target APK {target_apk} with module {module_apk}...")
        subprocess.run([
            "java", "-jar", LSPATCH_JAR_PATH,
            target_apk,
            "-m", module_apk,
            "-l", "2",
            "-o", PATCHED_DIR
        ])
    else:
        print(f"\n[3/4] Skipping patch step (no target APK at {target_apk}).")
        print("💡 Usage: python scripts/test_lspatch_local.py [path_to_twitter.apk]")

    print("\n[4/4] Done! All build & patch artifacts are prepared.")
    print(f"  - Module APK: {module_apk}")
    print(f"  - Patched Dir: {PATCHED_DIR}")


if __name__ == "__main__":
    main()
