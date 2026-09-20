# GitHub Actions fix - v0.1.2

The Android 17 platform package is published as `platforms;android-37.0`, not `platforms;android-37`.

Changes in this package:

- GitHub Actions installs `platforms;android-37.0`.
- The app declares `compileSdk = 37` and `compileSdkMinor = 0` so AGP resolves the installed `android-37.0` platform.
- AGP remains 9.4.0, Gradle 9.6.0, JDK 17.
- APK artifact name is `HomePanel-v0.1.2-debug-apk`.

Run **Actions > Build Android APK > Run workflow** after pushing the files.
