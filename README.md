# Jules Android

A single-module, native Android client for the Jules API, built with Kotlin, Jetpack Compose Material 3, a ViewModel/StateFlow UI state, and a separate HTTP repository/client layer.

## What was inspected

Dependency versions follow the repository’s version-catalog convention in `gradle/libs.versions.toml`. The source of truth reviewed for this recreation was the public repository at `https://github.com/TheRealAshik/Jules` (the corresponding repository was not present among this workspace's imported projects). Reviewed `README.md`, `AGENTS.md`, `JULES_API.md`, `JulesApiClient.kt`, `Models.kt`, the common `JulesViewModel`, Compose session/create/detail/settings screens, prompt storage, and Android key storage. The upstream project is Kotlin Multiplatform with a `shared` Jules SDK and Compose UI; API requests use `https://jules.googleapis.com/v1alpha`, `x-goog-api-key`, the documented request models, and the documented session/activity/source endpoints.

## Features in this Android recreation

- Real Jules API key validation by listing connected sources; no bundled key.
- API key encryption at rest with Android Keystore AES-GCM; key material is not printed or put in UI state.
- Live session history, search, status, pagination, refresh, and error/retry states.
- Task creation using real sources/branches, plan approval and AUTO_CREATE_PR options.
- Session/activity refresh while the detail screen is resumed (25-second interval; refresh requests are serialized), explicit refresh, activity pagination, text messaging, approval, deletion, and output/PR rendering.
- Local saved prompt gallery and System/Light/Dark themes.
- Compose screens reflow with keyboard insets; message composer is text-only because the Jules message endpoint accepts text.

## Build in Android Studio

1. Open this directory as a Gradle project in Android Studio.
2. Install JDK 17 and Android SDK Platform 35 (or update `compileSdk` / `targetSdk` in `app/build.gradle.kts` to the SDK installed on your machine).
3. Allow Gradle sync to resolve dependencies, then run the `app` configuration on an Android 8.0+ device/emulator.
4. Add your own key in Settings. It is validated before being saved.

The build container available for this handoff did not include Java, Gradle, or an Android SDK, so an APK build or emulator verification could not be run here. The project source is prepared for Android Studio but should be compiled there before release.

## Credential / product boundaries

A user-provided Jules key is encrypted locally, but no credential in a distributed client is equivalent to a trusted server-side secret. Public multi-user releases should use a server-side credential/proxy design appropriate to their threat model.

This handoff intentionally does not claim Google Sign-In, Firestore synchronization, or Gemini Copilot. No Firebase project configuration, OAuth client ID, Gemini key, or verified Gemini model/API settings were supplied. Add those as explicitly configured optional integrations before enabling them; do not use fake identity, mock AI results, or a shared key in the APK. Session and source lists are never populated with sample records.

## Jules API data flow

`MainActivity` wires `SecurePrefs`, `AppPreferences`, `JulesRepository`, and `JulesViewModel`. Composables observe a single `StateFlow<AppUiState>`. The repository owns `JulesApiClient`; all HTTP requests run asynchronously in coroutines. The client follows `JULES_API.md` contracts for list/create/get/delete sessions, sendMessage, approvePlan, list activities, list sources, and page tokens. API error responses are mapped to safe user-facing messages without echoing response bodies or credentials.
