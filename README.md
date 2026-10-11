# 🚀 ContinueO Auto Login

<div align="center">

[![Build & Release APK](https://github.com/adityasing9/BMSIT-CP/actions/workflows/build.yml/badge.svg)](https://github.com/adityasing9/BMSIT-CP/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/adityasing9/BMSIT-CP?color=007AFF&label=Release&logo=github)](https://github.com/adityasing9/BMSIT-CP/releases/tag/latest)
[![Android Min SDK](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Security](https://img.shields.io/badge/Storage-AES--256--GCM%20Encrypted-green?logo=securityscorecard&logoColor=white)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)

**A high-performance Android utility that automates the repetitive two-step login process for the BMSIT&M ContinueO Portal and delivers an authentic, responsive desktop portal experience on mobile devices.**

[📥 Download Latest APK](#-direct-download) • [✨ Key Features](#-key-features) • [🔄 How It Works](#-how-it-works) • [🔒 Security & Privacy](#-security--privacy-first) • [🏗️ Build from Source](#-building-from-source)

</div>

---

## 📖 Overview

Every visit to the **BMSIT&M ContinueO Parent Portal** (`https://student.bmsit.ac.in/parents/index.php`) normally requires:
1. Entering your **USN**.
2. Selecting your **Date of Birth** through three separate dropdown menus (Day, Month, Year).
3. Submitting and waiting for server response.
4. Entering your **ID Card Number** on a separate verification screen.
5. Submitting again to finally reach your dashboard.

Furthermore, mobile browsers often render the portal in a cramped, single-column responsive layout where navigation headers and tables are cut off or misaligned.

**ContinueO Auto Login** eliminates this friction:
- **One tap** on **LOGIN NOW** completes the entire 2-step authentication flow in seconds.
- Renders the portal in its **full desktop layout** by default, allowing effortless pinch-to-zoom, smooth scrolling, and complete access to all features.
- All credentials stay **100% on your device**, encrypted with military-grade **AES-256-GCM**.

---

## 📥 Direct Download

Ready to use? Grab the latest pre-compiled debug/release APK directly from GitHub Releases:

| Release Asset | Description | Direct Link |
| :--- | :--- | :--- |
| **`ContinueO-AutoLogin.apk`** | Latest compiled Android APK | [⬇️ **Download APK**](https://github.com/adityasing9/BMSIT-CP/releases/download/latest/ContinueO-AutoLogin.apk) |
| **GitHub Releases** | Release notes, changelog & assets | [📦 **View Releases**](https://github.com/adityasing9/BMSIT-CP/releases/tag/latest) |

> **Note for Installation:** Since this APK is distributed directly outside the Google Play Store, Android will ask you to *"Install apps from unknown sources"* or *"Allow installation from this source"*. Enable this toggle to proceed with installation.

---

## ⚖️ Why ContinueO Auto Login?

| Feature / Experience | Standard Web Browser | With ContinueO Auto Login |
| :--- | :--- | :--- |
| **Login Time** | 45–60 seconds of repetitive typing | **~2–3 seconds (Single Tap)** |
| **Credential Storage** | Manual typing or clunky browser autofill | **Encrypted local hardware keystore** |
| **Date of Birth Entry** | 3 tedious HTML select dropdowns | **Automated background composition** |
| **Step 2 (ID Card)** | Waiting for page reload + manual typing | **Automated detection & instant entry** |
| **Layout Quality** | Squished mobile layout; clipped tables | **True 1280px Desktop view with zoom** |
| **Session Control** | Manual cookies & frequent session timeouts | **Seamless desktop/mobile toggle** |
| **Cloud Dependency** | None | **Zero Cloud — 100% On-Device** |

---

## ✨ Key Features

### ⚡ 1. Intelligent Two-Step Automation
- **Step 1 (USN & DOB):** Automatically populates `#username`, chooses the exact Day (accounting for portal value trailing whitespace), Month, and Year selectors, invokes the portal's internal `putdate()` computation, sets the hidden password field, and triggers form validation.
- **Step 2 (ID Card Verification):** Automatically polls the DOM for the subsequent verification view, locates the input field (even across nested forms, dynamic elements, and iframes), injects the ID card number using native prototype setters, and submits.
- **Graceful Error Handling:** If the portal issues a CAPTCHA challenge or one-time password (OTP), automation pauses and gives you a single-tap **Continue Manually** option without interrupting your session.

### 🖥️ 2. Genuine Desktop Mode (Default: ON)
- **Authentic 1280px Viewport:** Bypasses mobile screen-width constraints (`<meta name="viewport" content="width=device-width">`) by reconfiguring the layout viewport to 1280px on page render.
- **Desktop Grid Breakpoints:** Triggers the portal's native UIkit desktop styles (`@media (min-width: 992px)` and `@l: 1200px`), showing the two-column dashboard layout (notice board on the left, login/dashboard on the right).
- **Smooth Navigation:** Built-in overview mode automatically scales the 1280px canvas to fit your phone screen, supported by full pinch-to-zoom and multi-touch gestures.
- **One-Tap Mode Switcher:** Toggle between `🖥 Desktop ON` and `📱 Mobile` right from the top bar with a clean, controlled reload that keeps your login session active.
- **Persistent Choice:** Your chosen mode is saved locally and remembered whenever you reopen the app.

### 🚀 3. Speed & Performance Optimizations
- **Hardware-Accelerated WebView:** Enabled `LAYER_TYPE_HARDWARE` for fluid scrolling and 60fps animations.
- **Optimized Delay Pipelines:** Eliminated artificial wait times, utilizing reactive coroutines and low-latency element detection (150ms intervals).
- **Intelligent Caching:** Configured with `LOAD_DEFAULT` caching and automatic image decoding for immediate page loads.

### 🔒 4. Security & Privacy First
- **Zero Cloud Architecture:** There is **no backend server**, **no telemetry**, **no analytics**, and **no third-party tracking**.
- **AndroidX Security Crypto:** Your USN, Date of Birth, and ID Card Number are encrypted using Google's `EncryptedSharedPreferences` backed by an Android Keystore `MasterKey` (AES-256-GCM / AES-256-SIV).
- **No Log Leaks:** Credential strings are never written to Logcat, included in URLs, or exposed in UI status cards.

---

## 🔄 How It Works

```mermaid
flowchart TD
    A[Launch App] --> B{Credentials Saved?}
    B -- No --> C[Setup Screen: Enter USN, DOB, ID Card]
    C --> D[Save to EncryptedSharedPreferences]
    D --> E[Home Screen]
    B -- Yes --> E[Home Screen]
    
    E --> F[Tap LOGIN NOW]
    F --> G[Initialize WebView in Desktop Mode 1280px]
    G --> H[Open student.bmsit.ac.in/parents]
    
    H --> I[Detect Page 1: USN & DOB Form]
    I --> J[Inject USN & Select DD/MM/YYYY]
    J --> K[Submit Login Form]
    
    K --> L{Check Next Page}
    L -- ID Card Page --> M[Detect Page 2 Input Field]
    M --> N[Inject ID Card Number & Submit]
    N --> O[Verify Success Page]
    
    L -- Already Logged In --> O[Verify Success Page]
    L -- CAPTCHA / OTP Required --> P[Pause & Enable Manual Interaction]
    
    O --> Q[Display Authenticated Desktop Portal]
```

---

## 📱 System Requirements

| Specification | Requirement |
| :--- | :--- |
| **Operating System** | Android 8.0 Oreo (API level 26) or newer |
| **Target SDK** | Android 14 (API level 34) |
| **Architecture** | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| **Connectivity** | Active Internet connection (WiFi / Mobile Data) |
| **Permissions** | `android.permission.INTERNET` |

---

## 🛠️ Technology Stack

- **UI & Architecture:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/) design system
- **State & Concurrency:** Kotlin Coroutines, `StateFlow`, and Android Architecture Components (`ViewModel`)
- **Web Automation:** Android `WebView`, custom `WebViewClient`, `WebChromeClient`, and dynamic DOM JavaScript evaluation
- **Storage & Cryptography:** AndroidX Security (`androidx.security:security-crypto:1.1.0-alpha06`)
- **Build System:** Gradle Kotlin DSL (`build.gradle.kts`)
- **Continuous Integration:** GitHub Actions (`.github/workflows/build.yml`)

---

## 🏗️ Building from Source

### Prerequisites
1. **Android Studio** Hedgehog (2023.1.1) or newer
2. **Java Development Kit (JDK):** Version 17
3. **Android SDK Platform:** 34

### Compilation Steps

```bash
# 1. Clone the repository
git clone https://github.com/adityasing9/BMSIT-CP.git
cd BMSIT-CP

# 2. Compile Debug APK using the Gradle Wrapper
./gradlew assembleDebug

# 3. (Optional) Install directly to an attached Android phone or emulator
./gradlew installDebug
```

The output artifact will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Codebase Structure

```
app/src/main/java/com/continueo/autologin/
├── ContineoApp.kt                    # Application class
├── MainActivity.kt                   # Edge-to-edge single activity entry point
│
├── models/
│   ├── Credentials.kt                # Secure credentials data model & masked formatter
│   └── AutomationState.kt            # Automation state machine definitions
│
├── security/
│   └── CredentialManager.kt          # EncryptedSharedPreferences (AES-256-GCM) & preferences
│
├── automation/
│   ├── ContineoSelectors.kt          # Portal DOM selectors, URLs, and timing constants
│   ├── ContineoAdapter.kt            # Robust JavaScript generators for form autofill
│   ├── AutomationEngine.kt           # Coroutine-based automation state manager
│   └── WebViewManager.kt             # WebView desktop mode, viewport & JS execution bridge
│
├── ui/
│   ├── theme/                        # Material 3 color palettes, typography, and theme
│   ├── navigation/                   # Compose Navigation graph & destinations
│   ├── screens/
│   │   ├── SetupScreen.kt            # First-time credentials entry form
│   │   ├── HomeScreen.kt             # Main launch screen with "LOGIN NOW" button
│   │   ├── LoginScreen.kt            # WebView display, status timeline & desktop toggle
│   │   └── SettingsScreen.kt         # Manage / clear credentials and app details
│   └── viewmodels/
│       └── LoginViewModel.kt         # UI state orchestration & automation bridge
│
└── utils/
    └── NetworkUtils.kt               # Real-time network availability checker
```

---

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><b>1. Are my college credentials safe?</b></summary>
Yes, 100%. Your credentials never leave your physical device. They are encrypted using the Android Keystore system with AES-256-GCM encryption. The app has no backend servers, no analytics SDKs, and no tracking code.
</details>

<details>
<summary><b>2. What happens if the portal displays a CAPTCHA or OTP?</b></summary>
The app is built to adhere strictly to security best practices. If an anti-bot check or OTP appears, the automated sequence pauses immediately, flags the step, and offers a <b>Continue Manually</b> button so you can solve it yourself in the WebView.
</details>

<details>
<summary><b>3. How do I switch between Desktop and Mobile layout?</b></summary>
At the top right of the login screen, tap the <b>🖥 Desktop ON / 📱 Mobile</b> button. The app will immediately adjust the user-agent and viewport and reload the page while preserving your authenticated session.
</details>

<details>
<summary><b>4. How do I update or delete my saved credentials?</b></summary>
From the main screen, tap <b>Settings</b> at the bottom. You can either select <b>Edit Credentials</b> to modify your USN, DOB, or ID card, or choose <b>Clear Saved Credentials</b> to wipe all stored data from the encrypted vault.
</details>

---

## 📄 License & Disclaimer

- **License:** Distributed for educational and personal productivity purposes.
- **Disclaimer:** This software is an independent, client-side productivity tool designed exclusively for authorized students and parents accessing their own BMSIT&M ContinueO portal accounts. It does not tamper with or bypass college security protocols. The developers assume no responsibility for any unauthorized usage.
