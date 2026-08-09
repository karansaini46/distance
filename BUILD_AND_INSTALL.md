# How to Build & Install on Both Phones

## Requirements
- Android Studio Hedgehog 2023.1.1 or newer
- Firebase account (free tier is fine)
- JDK 17

## Step 1 — Firebase (5 minutes)
1. console.firebase.google.com → New Project
2. Add Android app → Package: `com.karan.distancewidget`
3. Download `google-services.json` → put it inside the `app/` folder
4. Realtime Database → Create Database → Start in Test Mode
5. Rules: `{ "rules": { ".read": true, ".write": true } }`

## Step 2 — Build APK
In Android Studio:
```
Build → Build Bundle(s)/APK(s) → Build APK(s)
APK: app/build/outputs/apk/debug/app-debug.apk
```

Or via terminal:
```
./gradlew assembleDebug
```

## Step 3 — Install on Both Phones

**Via USB + ADB:**
```
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Via file transfer (no cable):**
- Send `app-debug.apk` via WhatsApp or Google Drive
- Settings → Install unknown apps → enable for Files/WhatsApp
- Tap the APK → Install

## Step 4 — First Launch (CRITICAL)

**YOUR phone:**
1. Open Distance app
2. Tap your name
3. Grant "Location" → tap "While using the app" first
4. Grant "Always allow location" when asked separately
5. Tap "Fix" on the battery optimisation warning
   - On **Xiaomi**: also disable in Security → Autostart
   - On **Samsung**: also go to Device Care → Battery → set to Unrestricted
   - On **OnePlus**: also Battery → Battery Optimisation → Distance → Don't Optimise

**PARTNER's phone:**
- Same steps but tap the partner name button

## Step 5 — Add Widget
1. Long-press home screen → Widgets
2. Scroll to find "Distance"
3. Drag and drop it onto your home screen

## Step 6 — Verify It Works
1. Both people open the app → tap "Sync Now"
2. Wait 15-30 seconds
3. Widget updates with real distance

## Troubleshooting

| Symptom                   | Fix                                                        |
|---------------------------|------------------------------------------------------------|
| "tap to retry" on widget  | Check internet. Tap widget to refresh.                     |
| Widget stuck on loading   | Open app → Sync Now                                        |
| Location not updating     | Battery optimisation → set Distance to Unrestricted        |
| Xiaomi stops after reboot | Security app → Autostart → enable Distance                 |
| Partner shows stale data  | Partner opens app → Sync Now → wait 30s                   |
| "google-services not found"| Make sure google-services.json is in the app/ folder      |
| App crashes on launch     | Firebase not initialised — verify google-services.json     |
