# SnapTask 📸✅

**SnapTask** is a native Android application that bridges the gap between screenshot hoarding and actionable to-do lists. It automatically monitors new screenshots taken on the device, extracts text using bundled on-device OCR, parses actionable tasks and dates using intelligent heuristics, and prompts the user with actionable notifications and an intuitive review queue.

---

## 🔒 The Privacy Promise
- **100% On-Device & Offline**: Uses Google ML Kit Text Recognition with the **bundled model** (`com.google.mlkit:text-recognition`). OCR models run locally without downloading assets at runtime.
- **Zero Network Access**: SnapTask does **NOT** request the `android.permission.INTERNET` permission and includes no telemetry, analytics, or cloud connections.
- **Sensitive Content Shield**: Automatically detects and drops screenshots containing OTPs, authentication codes, credit card numbers (verified via the Luhn algorithm), CVVs, and passwords.
- **Zero Image Duplication**: Only local `content://` URIs are stored in the database. When a user deletes a screenshot from their gallery, SnapTask handles it gracefully without crashes.

---

## 🏗️ Architecture & Tech Stack

- **Language & Framework**: Kotlin with 100% Jetpack Compose and Material 3 (M3).
- **Architecture**: Single-Activity, MVVM pattern with Kotlin Coroutines and `StateFlow`.
- **Local Database**: Android Room for persistent storage of `Task` and `ScreenshotRecord` entities.
- **Preferences**: AndroidX DataStore for user settings and scheduling timestamps.
- **Background Orchestration**: AndroidX WorkManager:
  - **Primary Trigger**: A `OneTimeWorkRequest` observing `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` descendants via `ContentUriTrigger`. When a screenshot is saved, it executes immediately and re-enqueues itself.
  - **Fallback Sweep**: A `PeriodicWorkRequest` running roughly every 15 minutes to guarantee no screenshot is missed even if a trigger was throttled.
- **Reminders**: `AlarmManager` with `setExactAndAllowWhileIdle()` when exact alarm permissions are granted, falling back to inexact alarms gracefully.
- **Biometric Lock**: Jetpack `BiometricPrompt` for local app security.

---

## 📱 Permissions Breakdown

| Permission | Purpose |
| :--- | :--- |
| `READ_MEDIA_IMAGES` (Android 13+) | Access newly captured screenshot files in MediaStore. |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Support partial photo picker access cleanly. |
| `READ_EXTERNAL_STORAGE` (Android ≤ 12) | Access screenshot files on legacy Android versions. |
| `POST_NOTIFICATIONS` (Android 13+) | Display "Tasks found" alerts and scheduled reminder notifications. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Precise task due date alarms and snooze functionality. |
| `RECEIVE_BOOT_COMPLETED` | Automatically reschedule pending reminders and re-arm the screenshot trigger after device restart. |
| `VIBRATE` | Haptic feedback for reminder notifications. |

---

## 🧪 How to Test Screenshot Detection

### 1. In Streaming Emulator / Android Device
1. Launch SnapTask and complete the 3-step Onboarding (grant Media and Notification permissions).
2. Take a screenshot on your device:
   - Press **Power + Volume Down**, or
   - Use the emulator's Screenshot button in the side toolbar.
3. Within seconds, a notification appears: **"N tasks found in your screenshot"** with three actions:
   - **Review**: Opens the Review screen showing a zoomable preview, editable OCR text, and candidate task cards.
   - **Save all**: Instantly saves detected tasks to your list and schedules reminders.
   - **Dismiss**: Clears the notification.
4. If you leave it unreviewed, open SnapTask and switch to the **Inbox** tab to view your pending screenshots grid.

### 2. Manual Import & Share Target (Always Available)
- **In-App Import**: In the Inbox screen or empty state, tap **Import a Screenshot** to select any photo via the system Photo Picker.
- **System Share Sheet**: In Google Photos, Files, or any gallery app, tap **Share** on any image and choose **SnapTask**. It immediately opens the review queue.
- **Quick Settings Tile**: Pull down the notification shade, tap the **SnapTask Import** tile to launch directly into the image picker.

### 3. Unit Tests
All rule-based parsing scenarios, date/time logic, and sensitive content filters are verified via unit tests:
```bash
gradle :app:testDebugUnitTest
```
Runs test cases including:
- `"Call mom tomorrow 6pm"` -> 1 task due tomorrow at 18:00
- `"- buy milk\n- pay electricity bill by 15th\n- book dentist"` -> 3 bullet tasks
- `"Can you send the report by Friday?"` -> 1 task due Friday
- Luhn credit card validation and OTP/password detection.
