# Build notes - HomePanel 0.3.0

The project is configured for the GitHub Actions build chain already proven by v0.2.x:

- JDK 17
- Gradle 9.6.0
- AGP 9.4.0
- compileSdk 37 / compileSdkMinor 0
- Android SDK package `platforms;android-37.0`

Run:

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk`

The current ChatGPT container does not include a complete Android SDK/Gradle toolchain, so the authoritative full build remains the included GitHub Actions workflow.
