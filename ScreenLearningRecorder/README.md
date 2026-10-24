# Screen Learning Recorder — Android MVP source project

This is an Android Studio Kotlin starter project for Version 1.

## Implemented in this source
- User-initiated Android MediaProjection screen recording to MP4.
- Persistent Android foreground notification with an explicit Stop action.
- Optional draggable floating bubble that hides while recording and reappears after recording stops.
- Local per-session storage in the app-specific external files directory.
- After recording ends, sample frames from the video at 1-second intervals and save JPEGs only when the sampled image differs enough from the prior sample.
- Basic local session list and share-sheet action.

## Not implemented yet
- Google Drive OAuth/API backup. The app currently records locally; Drive connection is the next development task and needs OAuth configuration and testing with the user's Google project.
- OCR, AI analysis, chat/group auto-posting, external databases and Docker server integration.
- A polished gallery/player UI and automated instrumentation tests.

## Build
Open this folder in Android Studio (recent version with Android SDK 35), allow Gradle sync, then choose **Build > Build APK(s)**. The output APK is typically under `app/build/outputs/apk/debug/`.

Minimum SDK is Android 10 (API 29). Recording requires the system screen-capture consent prompt. The app does not attempt to bypass Android capture indicators or protected-screen restrictions.

## Important testing notes
- Test on a physical Android device. Emulator behavior can differ.
- Large or long recordings use storage and post-processing time. The snapshot sampler is capped at 1,200 images per session.
- This is an MVP source scaffold and has not been compiled or device-tested in this environment. Build and runtime fixes may be needed before daily use.
