# VoxAlarm — Neon Voice Alarm for Android

A native Android alarm clock (Kotlin · Jetpack Compose · Material 3) that **speaks your own personalised messages** using the device's Text-to-Speech engine. It has a dark interface with neon accents.

---

## 1. Architecture

```
app/src/main/java/com/voxalarm/
├── VoxAlarmApp.kt            Application + AppContainer (manual DI)
├── BuildConfigInfo.kt
├── data/
│   ├── Alarm.kt              Room entity (time, repeat mask, message, voice, colour, sound options…)
│   ├── HistoryEntry.kt       Room entity (dismissed / snoozed / missed / auto-stopped / skipped)
│   ├── Daos.kt               AlarmDao, HistoryDao
│   ├── AppDatabase.kt        Room DB in device-protected storage (Direct Boot)
│   ├── SettingsRepository.kt DataStore preferences (app defaults)
│   └── AlarmRepository.kt    Single source of truth – every write keeps AlarmManager in sync
├── alarm/
│   ├── AlarmScheduler.kt     AlarmManager.setAlarmClock(), next-occurrence maths (DST-safe)
│   ├── AlarmService.kt       Foreground service: tone → voice → repeat, vibration, volume ramp, queue
│   ├── Receivers.kt          AlarmReceiver, ReminderReceiver, SystemEventReceiver (boot/time/zone)
│   └── Notifications.kt      Channels, full-screen ringing notification, upcoming & missed alerts
├── tts/
│   ├── TtsManager.kt         Coroutine wrapper around android.speech.tts.TextToSpeech
│   └── SpeechText.kt         Builds the spoken sentence; {time} {day} {date} {name} tokens
├── util/TimeText.kt          12/24 h formatting, countdowns, repeat summaries
└── ui/
    ├── MainActivity.kt       NavHost + glowing bottom bar (Alarms · History · Settings)
    ├── theme/Theme.kt        Colours (#08090D / #171920), neon palette, typography
    ├── components/           NeonCard, glow modifier, pills, day toggles, swatches,
    │                         SoundWave, PulsingRings, VoicePicker, PermissionChecklist
    ├── home/                 Live clock, greeting, next-alarm countdown, alarm cards, FAB
    ├── edit/                 Create/edit alarm (time picker, repeat, name, message, voice, sound…)
    ├── history/              Stats + grouped history
    ├── settings/             All defaults, accent colour, notifications, reliability, test alarm
    └── ringing/AlarmActivity Full-screen lock-screen alarm UI (pulsing rings, sound-wave, snooze/dismiss)
```

**What happens when an alarm fires**

1. `AlarmManager.setAlarmClock()` fires `AlarmReceiver`. This works in Doze, and this kind of alarm is allowed to start a foreground service from the background.
2. `AlarmService` starts in the foreground (type `mediaPlayback`) and posts a `CATEGORY_ALARM` notification with a full-screen intent. On a locked or sleeping phone, that intent opens `AlarmActivity` over the lock screen.
3. The service plays the gentle chime, then speaks the message through TTS on the alarm audio stream. If you chose a repeat interval (15, 30 or 60 s), it repeats until you dismiss or snooze, or until the auto-stop time runs out. Volume can rise gradually.
4. **Dismiss**: a repeating alarm is rescheduled for its next day; a one-time alarm is turned off. **Snooze**: the alarm rings again after 5, 10, 15 or 20 minutes. Every outcome is saved to History.
5. If a second alarm fires while one is ringing, it waits in a queue and rings next, so overlapping alarms are never lost.
6. If TTS is missing or fails, the service plays the system alarm sound (or the built-in chime) on a loop, so the alarm still makes a sound.

**Reliability details**
- Reboots, app updates, time changes, time-zone changes and exact-alarm permission changes all re-register every alarm (`SystemEventReceiver`).
- Alarms are stored in device-protected storage, so they are rescheduled at `LOCKED_BOOT_COMPLETED`, before you unlock the phone after a restart.
- Next-ring times are calculated in local wall-clock time with `java.time`. In a daylight-saving gap, the alarm rings straight after the clock jumps.
- An alarm that couldn't ring because the phone was off is recorded as **Missed**, and you get a notification about it.

---

## 2. Build the APK

**Requirements:** Android Studio Ladybug (2024.2) or newer, with JDK 17+ (Android Studio includes one) and Android SDK 35.

### Option A — Android Studio (easiest)
1. Unzip the project and choose **File → Open** in Android Studio, then select the `VoxAlarm` folder.
2. Let the Gradle sync finish. It downloads Gradle 8.11.1 and the dependencies.
3. Connect your phone with USB debugging on (**Settings → About phone → tap Build number 7×**, then **Developer options → USB debugging**).
4. Press **Run ▶**.
5. To get an APK file: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file is created at `app/build/outputs/apk/debug/app-debug.apk`.

### Option B — Command line
```bash
cd VoxAlarm
./gradlew assembleDebug            # Windows: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`./gradlew assembleRelease` makes a minified release APK. It is signed with the debug key so it installs straight away; set up your own signing key before publishing.

> If the command line says the SDK location wasn't found, create `local.properties` with `sdk.dir=/path/to/Android/sdk`. Android Studio creates this file for you.

---

## 3. First launch checklist

The home screen shows a **"Finish setup"** card until everything below is granted. Each item has a working **Allow** button.

| Item | Why |
|---|---|
| Notifications (Android 13+) | Needed to show the ringing alarm and reminders |
| Exact alarms (Android 12/12L) | Alarms ring on the exact minute. Granted automatically on Android 13+ through `USE_EXACT_ALARM` |
| Full-screen alarms (Android 14+) | Shows the alarm over the lock screen |
| Unrestricted battery (recommended) | Stops manufacturer battery savers from delaying alarms |

On Xiaomi, Huawei, Oppo, Vivo and Samsung phones, also allow **Autostart** if the phone offers it, and set battery use to **Unrestricted** or **No restrictions**. See dontkillmyapp.com for steps for your model.

---

## 4. Testing alarms with the phone locked and the app closed

1. Open **Settings → Test your setup → Ring in 15 s**. This creates a real, temporary alarm that uses your default voice.
2. Straight away, **press the power button** to lock the phone. You can also swipe VoxAlarm away from Recents first.
3. After about 15 s the screen should turn on with the full-screen alarm and play the chime and spoken message. Try **Snooze** and **Dismiss**.
4. **Reboot test:** make an alarm for 3–4 minutes ahead, then restart the phone and *don't unlock it*. The alarm should still ring. The voice may fall back to a tone until you unlock, because some TTS engines don't run before the first unlock.
5. **Doze test (advanced, needs adb):**
   ```bash
   adb shell dumpsys deviceidle force-idle      # put device in Doze
   # wait for the alarm to fire
   adb shell dumpsys deviceidle unforce
   ```
6. **Check what is scheduled:** `adb shell dumpsys alarm | grep -A3 com.voxalarm`
7. **Check the missed-alarm notification:** set an alarm 2 minutes ahead and turn the phone off until that time has passed. When you turn it back on, you get a **Missed alarm** notification and a History entry.

---

## 5. Voices

- Voices, languages and accents come only from your installed TTS engine (for example *Speech Services by Google* or *Samsung TTS*). The app never lists a voice the engine doesn't report.
- **Male/Female** labels appear only when the engine reports them; most engines don't. Use **Test Voice** to hear each voice.
- A voice marked **Offline** works without internet. Tap **Get voices** to download more languages, or **TTS settings** to change the engine.
- Speech plays on the **alarm** audio stream, so you hear it when the phone is on silent or vibrate. While ringing, the app sets the alarm volume to your chosen level and restores it afterwards.

## 6. Honest limits
- An alarm cannot ring while the phone is **powered off**.
- Some manufacturers' battery managers can still interfere even with correct settings. The reliability checklist reduces this risk but can't remove it everywhere.
- Speech needs a working TTS engine. Without one, the alarm falls back to a tone so it still wakes you.

## 7. Ideas for later
- An optional cloud AI voice (natural neural speech) layered on top of the built-in TTS, which stays as the reliable offline default.
- Wear OS companion, weather and calendar summaries spoken in the message, and math or shake challenges to dismiss.
