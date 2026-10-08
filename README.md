# ContinueO Auto Login

A small, focused Android application that eliminates the repetitive manual login process for the BMSIT&M ContinueO Parent Portal.

## What It Does

Instead of manually entering your USN, Date of Birth, and ID Card Number every time you visit the ContinueO portal, this app lets you:

1. **Enter your credentials once**
2. **Tap "LOGIN NOW"**
3. **Arrive at the logged-in ContinueO portal**

That's it. One tap instead of repeatedly filling the same form.

## What It Does NOT Do

- ❌ Bypass CAPTCHA, OTP, MFA, or any security mechanism
- ❌ Send your credentials to any server
- ❌ Include a backend, cloud database, or analytics
- ❌ Recreate or scrape the ContinueO website
- ❌ Replace the actual ContinueO portal

If the portal presents a security challenge (CAPTCHA, OTP, etc.), the app stops automation and lets you complete it manually.

## How It Works

The app opens the real ContinueO website inside a WebView and automates only the repetitive form-filling steps:

```
Open App → LOGIN NOW → Portal loads → USN filled → DOB selected → Login submitted
→ ID Card page loads → ID Card Number filled → Submitted → You're logged in
```

## Security & Privacy

- Credentials are stored **locally on your device only** using Android's EncryptedSharedPreferences
- The app does **not** operate a backend server
- The app does **not** transmit your credentials to any third-party service
- Credentials are **never** logged, included in URLs, or exposed in screenshots

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle
4. Build & run on your device

```bash
# Command-line build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Release Build

```bash
# Generate release APK (requires signing configuration)
./gradlew assembleRelease
```

The release APK will be in `app/build/outputs/apk/release/`.

> **Note:** For release builds, update the signing configuration in `app/build.gradle.kts` with your keystore.

## Testing

### Dummy Credentials for Testing

During development, use dummy credentials:
- USN: `1BM22CS999`
- DOB: `01 / Jan / 2004`
- ID Card: `TEST12345`

Never use real credentials in source code, commits, or screenshots.

### Manual Testing Checklist

- [ ] First-time setup flow
- [ ] Credential validation (empty fields rejected)
- [ ] Credential persistence across app restarts
- [ ] LOGIN NOW automation (both pages)
- [ ] Error handling (wrong credentials, network failure)
- [ ] Portal structure change detection
- [ ] CAPTCHA/OTP manual intervention
- [ ] Clear credentials functionality
- [ ] Edit credentials functionality
- [ ] Dark mode / Light mode
- [ ] Back button behavior in WebView
- [ ] No internet connection message
- [ ] Different screen sizes

## Project Structure

```
app/src/main/java/com/continueo/autologin/
├── ContineoApp.kt                    # Application class
├── MainActivity.kt                   # Single activity entry point
├── models/
│   ├── Credentials.kt                # Credential data model
│   └── AutomationState.kt           # State machine for automation
├── security/
│   └── CredentialManager.kt          # Encrypted local storage
├── automation/
│   ├── ContineoSelectors.kt          # Portal DOM selectors
│   ├── ContineoAdapter.kt           # Portal-specific JS logic
│   ├── AutomationEngine.kt          # Core automation controller
│   └── WebViewManager.kt            # WebView setup & JS execution
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── screens/
│   │   ├── SetupScreen.kt           # First-time credential entry
│   │   ├── HomeScreen.kt            # Main LOGIN NOW screen
│   │   ├── LoginScreen.kt           # WebView + automation status
│   │   └── SettingsScreen.kt        # Edit/clear credentials, about
│   └── viewmodels/
│       └── LoginViewModel.kt
└── utils/
    └── NetworkUtils.kt
```

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Browser:** Android WebView
- **Storage:** EncryptedSharedPreferences (AndroidX Security Crypto)
- **Architecture:** Single Activity + Compose Navigation

## Requirements

- Android 8.0 (API 26) or higher
- Internet connection (to access the ContinueO portal)

## License

This project is for personal educational use.

## Disclaimer

This application is intended for use with your own authorized BMSIT&M account. It automates form-filling convenience only and does not bypass any security mechanisms. The developers are not responsible for any misuse.
