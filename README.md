# SimInfoViewer

SimInfoViewer is an Android app built with Kotlin and Jetpack Compose. It displays phone numbers and SIM information from the device, handling all modern Android privacy restrictions and permissions. The app also provides WiFi info and Google Play Services fallback for phone number retrieval.

## Features
- Reads and displays phone numbers and SIM info for all Android versions (10–15)
- Handles permissions and privacy restrictions gracefully
- Shows fallback SIM info and user guidance if phone number is unavailable
- Detects connection to specific WiFi networks (e.g., "ADU") and provides user actions
- Uses Google Play Services phone number picker as a fallback
- Modern Compose UI with robust error handling

## Automated APK Upload
Every time you push to GitHub, a pre-push git hook will:
1. Build the release APK
2. Upload the APK to a dedicated Google Drive folder using OAuth authentication

**Sensitive credentials are not tracked in git and must be present locally.**

---

## Setup
- Clone the repo
- Place your `credentials.json` and `token.json` in the project root (see `upload_to_drive.py` for details)
- The pre-push hook is already set up for automatic APK upload

---

## License
MIT 