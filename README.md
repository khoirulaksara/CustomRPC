# CustomRPC for Android (Codename: Archangel)

**CustomRPC** is a lightweight, reliable, and persistent Discord Rich Presence (RPC) client for Android. It allows you to display a custom status, "Playing" activity, or detailed presence on your Discord profile directly from your Android device.

![App Icon](app/src/main/res/mipmap-xxhdpi/ic_launcher.webp)

## 🌟 Features

*   **Persistent Service**: Runs in the background with a persistent notification to keep your presence alive even when the app is closed.
*   **Auto-Reconnect**: Intelligent reconnection logic handles network drops and service interruptions automatically.
*   **Rich Customization**:
    *   **Details & State**: two lines of custom text.
    *   **Assets**: Support for Large and Small images with tooltips.
    *   **Buttons**: Add up to 2 clickable buttons (Label & URL).
    *   **Timestamps**: Show "Elapsed", "Local Time", or a custom range.
    *   **Activity Types**: Playing, Streaming, Listening, Watching, Competing.
    *   **User Status**: Set your status to Online, Idle, DnD, or Invisible.
*   **Magic Asset Picker**: Automatically fetches assets from your Discord Application ID for easy selection.
*   **Multi-Language Support**:
    *   🇺🇸 **English** (Default)
    *   🇮🇩 **Indonesian** (Bahasa Indonesia) - *Automatically detected based on system language.*
*   **Token Sniffer**: Built-in web login helper to easily retrieve your Discord Auth Token.

## ⚠️ Disclaimer

**Use at your own risk.**
This application requires your Discord **User Token** to function. Using a user token for automated actions or "self-botting" can technically violate Discord's Terms of Service. However, this app only sends Rich Presence updates (Identity & Presence), which is generally considered safe for personal use.
*   **Never share your token with anyone.**
*   The developer is not responsible for any account bans or restrictions.

## 🚀 Installation

### Option 1: APK Download
Check the [Releases](https://github.com/khoirulaksara/CustomRPC/releases) page for the latest `.apk` file. Verify the "Archangel" codename in the About section.

### Option 2: Build from Source
1.  Clone this repository:
    ```bash
    git clone https://github.com/khoirulaksara/CustomRPC.git
    ```
2.  Open the project in **Android Studio**.
3.  Sync Gradle and build the project.
4.  Run on your Android device (Minimum SDK: Android 8.0 Oreo).

## 📖 Usage Guide

### 1. Getting your User Token
1.  Open the app and click **"How to get Token?"** or **"Login via Discord"**.
2.  Log in to your Discord account via the built-in browser.
3.  The app will automatically detect and grab your token from the network headers.
4.  Alternatively, you can manually paste your token if you already have it.

### 2. Setting up the RPC
1.  Go to the **Discord Developer Portal** and create a new Application.
2.  Copy the **Application ID**.
3.  In the app, click **"Configure Presence"**.
4.  Paste your **Application ID**.
5.  Fill in the **Details**, **State**, and **Images**.
    *   *Tip:* Click the search icon 🔍 next to the image fields to fetch assets from your application.
6.  Click **"Save & Apply Details"**.
7.  Return to the dashboard and click **"START"**.

## 🛠️ Technologies
*   **Language**: Kotlin
*   **Android SDK**: Native Android Development
*   **Communication**: WebSocket (for connecting to Discord Gateway)
*   **UI**: Material Design 3

## 📄 License

This project is licensed under the **MIT License**.

---
*Developed with ❤️ by Khoirul Aksara*
