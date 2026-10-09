# ContinueO Auto Login

[![Build & Release APK](https://github.com/adityasing9/BMSIT-CP/actions/workflows/build.yml/badge.svg)](https://github.com/adityasing9/BMSIT-CP/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/adityasing9/BMSIT-CP?color=blue&label=release)](https://github.com/adityasing9/BMSIT-CP/releases/tag/latest)

A fast, modern Android application that automates the repetitive two-step login process for the **BMSIT&M ContinueO Parent Portal** (`https://student.bmsit.ac.in/parents/index.php`) and renders the portal in full desktop view.

---

## 📥 Download APK

Get the latest pre-built APK directly:

- **Direct Download:** [**ContinueO-AutoLogin.apk**](https://github.com/adityasing9/BMSIT-CP/releases/download/latest/ContinueO-AutoLogin.apk)
- **Releases Page:** [GitHub Releases (Latest)](https://github.com/adityasing9/BMSIT-CP/releases/tag/latest)

---

## ✨ Features

- **⚡ One-Tap 2-Step Automation:**
  - **Step 1:** Automatically fills USN and Date of Birth (Day, Month, Year dropdowns), computes credentials, and submits.
  - **Step 2:** Automatically detects the verification page, enters your ID Card Number, and completes login.
- **🖥 Genuine Desktop Mode (Default):**
  - Displays the authentic ContinueO desktop portal layout (1280px layout viewport, 2-column layout, desktop navigation and styling) instead of the cramped mobile-responsive layout.
  - Overview mode with full pinch-to-zoom and smooth panning.
  - Persistent preference: Remembers your mode (Desktop vs Mobile) across app restarts.
  - One-tap toggle button in the top bar (`🖥 Desktop ON` / `📱 Mobile`) with seamless page refresh and session retention.
- **🚀 Smooth & Fast Performance:**
  - Hardware-accelerated WebView rendering (`LAYER_TYPE_HARDWARE`).
  - Optimized transition delays and responsive DOM polling.
- **🔒 Privacy & Local Security First:**
  - Credentials are encrypted and stored **only on your device** using AndroidX Security (`EncryptedSharedPreferences` with MasterKey AES-256-GCM).
  - No backend, no cloud database, no third-party tracking or analytics.
  - Credentials are never logged, transmitted, or leaked in URLs.

---

## 🛑 What It Does NOT Do

- ❌ Bypass CAPTCHA, OTP, MFA, or anti-bot security mechanisms (if prompted, automation pauses for manual completion).
- ❌ Send credentials to any external server.
- ❌ Scrape or store personal college data off-device.

---

## 🔄 How It Works

```
Open App → Tap LOGIN NOW → Portal opens (Desktop Mode) → USN & DOB filled →
Page 1 Submitted → ID Card page detected → ID Card Number filled →
Page 2 Submitted → Authenticated ContinueO Dashboard loaded!
```

---

## 📱 Requirements

- Android 8.0 (API level 26) or higher
- Active Internet connection

---

## 🛠️ Technology Stack

- **UI Framework:** Jetpack Compose with Material 3
- **Language:** Kotlin & Kotlin Coroutines / Flow
- **Browser Engine:** Android WebView with customized `WebViewClient` and `WebChromeClient`
- **Security:** AndroidX Security Crypto (`EncryptedSharedPreferences`)
- **CI/CD:** GitHub Actions (Automated Gradle build & GitHub Release publishing)

---

## 🏗️ Building From Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build Commands

```bash
# Clone the repository
git clone https://github.com/adityasing9/BMSIT-CP.git
cd BMSIT-CP

# Build Debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Project Structure

```
app/src/main/java/com/continueo/autologin/
├── ContineoApp.kt                    # Application class
├── MainActivity.kt                   # Single activity entry point
├── models/
│   ├── Credentials.kt                # Credential data model
│   └── AutomationState.kt            # Automation state machine
├── security/
│   └── CredentialManager.kt          # Encrypted local storage & preferences
├── automation/
│   ├── ContineoSelectors.kt          # Portal CSS selectors & URLs
│   ├── ContineoAdapter.kt            # DOM interaction & autofill JS scripts
│   ├── AutomationEngine.kt           # Coroutine automation controller
│   └── WebViewManager.kt             # WebView desktop config & viewport manager
├── ui/
│   ├── theme/                        # Material 3 colors, theme, and typography
│   ├── navigation/                   # Navigation graph & routes
│   ├── screens/
│   │   ├── SetupScreen.kt            # Initial credential setup
│   │   ├── HomeScreen.kt             # "LOGIN NOW" launch screen
│   │   ├── LoginScreen.kt            # WebView display & desktop toggle
│   │   └── SettingsScreen.kt         # Edit credentials & app settings
│   └── viewmodels/
│       └── LoginViewModel.kt         # UI state & WebView coordination
└── utils/
    └── NetworkUtils.kt               # Network connectivity monitor
```

---

## 📄 License & Disclaimer

- **License:** Educational and personal use only.
- **Disclaimer:** This app is designed for authorized students and parents accessing their own BMSIT&M ContinueO accounts. It provides local input automation convenience only and does not circumvent any access controls or security features.
