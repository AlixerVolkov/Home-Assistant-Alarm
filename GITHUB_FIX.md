# GitHub Actions fix

The repository must preserve this Android project structure:

- `build.gradle.kts` (root project)
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/dev/homepanel/app/...`
- `app/src/main/res/...`
- `.github/workflows/build-apk.yml`

Do not flatten the `app/` directory into the repository root.

This workflow deliberately does not use `./gradlew`, because the project package does not contain `gradlew`/`gradle-wrapper.jar`. GitHub Actions installs Gradle 9.6.0 with `gradle/actions/setup-gradle` and runs:

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

The APK is uploaded as the GitHub Actions artifact `HomePanel-v0.1-debug-apk`.
