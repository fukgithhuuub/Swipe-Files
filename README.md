# Swipe-Files

An Android application built with Kotlin, Jetpack Compose, and Material 3, targeting Android 14 (API 34).

## Prerequisites

- JDK 17
- Android SDK (API 34)

## How to Build the APK

The project files are located inside the `Swipe-Files` directory, not in the root. Therefore, you must navigate into this directory before running any build commands.

```bash
cd Swipe-Files
```

### 1. Build an Unsigned APK (Priority)

If you just want to test the app or you plan to sign the APK at a later stage, you can build either a debug APK or an unsigned release APK.

#### Build a Debug APK (Recommended for Testing)

A Debug APK is automatically signed with a generic debug key provided by the Android SDK. This allows you to install and test the app immediately on your device or emulator.

```bash
./gradlew assembleDebug
```

The APK will be located at: `Swipe-Files/app/build/outputs/apk/debug/app-debug.apk`

#### Build an Unsigned Release APK

A Release APK is optimized and stripped of debugging code. By default, running the release build without keystore variables will produce an unsigned APK. **Note: Unsigned APKs cannot be installed directly on an Android device.**

```bash
./gradlew assembleRelease
```

The APK will be located at: `Swipe-Files/app/build/outputs/apk/release/app-release-unsigned.apk`

### Troubleshooting: "Package appears to be invalid" Error

If you are encountering an error saying "the package appears to be invalid" when trying to install the APK on your device, it is likely due to one of the following reasons:

1. **Trying to install an unsigned release APK**: Android enforces that all installed applications must be signed. If you built the app using `./gradlew assembleRelease` without providing signing credentials, the resulting `app-release-unsigned.apk` cannot be installed.
   - **Solution:** For testing, use `./gradlew assembleDebug` instead. This builds an APK signed with a temporary debug key, allowing installation. Alternatively, you can sign the release APK manually using `apksigner`.
2. **Conflicting Signatures**: If you have an older version of the app installed (e.g., signed with a different key, like a production release key) and you try to install a new version signed with a debug key, Android will reject it.
   - **Solution:** Uninstall the existing app from your device before installing the new one.
3. **Corrupted File**: The APK file may have been corrupted during transfer to your device.
   - **Solution:** Try rebuilding the APK and transferring it again.
4. **Path Issues**: The actual Gradle project is nested inside the `Swipe-Files` directory. Ensure you are running the `./gradlew` commands from within `Swipe-Files`, otherwise, the build might not run correctly.

### 2. Signing the Release APK (For Production)

When you are ready to sign the release APK for production, the project is configured to automatically read keystore details from environment variables.

You will need to set the following environment variables before running the release build:

- `KEYSTORE_PATH`: Path to your keystore file (`.jks` or `.keystore`)
- `RELEASE_KEYSTORE_PASSWORD`: Keystore password
- `RELEASE_KEY_ALIAS`: Key alias
- `RELEASE_KEY_PASSWORD`: Key password
- `VERSION_CODE` (Optional): The version code for the build
- `VERSION_NAME` (Optional): The version name for the build

Once these are set in your environment, running `./gradlew assembleRelease` will output a signed release APK instead of an unsigned one.
