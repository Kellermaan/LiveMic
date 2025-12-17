# LiveMic

A professional Android application for real-time microphone audio streaming to connected Bluetooth audio devices with simultaneous local recording.

## Overview

LiveMic captures audio from the device microphone and routes it to Bluetooth audio devices (headsets, speakers) while maintaining a local recording in WAV format. The application enforces Bluetooth device connectivity as a prerequisite for operation.

## Features

- **Real-time Audio Streaming**: Low-latency capture and playback of microphone audio
- **Bluetooth Routing**: Automatic routing of audio to connected Bluetooth devices (A2DP, HEADSET profiles)
- **Local Recording**: Simultaneous PCM to WAV file recording in external storage
- **Foreground Service**: Background operation with persistent notification
- **Bluetooth Validation**: Application only starts streaming when Bluetooth audio device is confirmed connected

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/livemic/
│   │   ├── MainActivity.kt              # Main UI and permissions
│   │   ├── StreamService.kt             # Foreground service orchestration
│   │   └── AudioStreamer.kt             # Audio capture, playback, and recording
│   ├── AndroidManifest.xml              # App permissions and service declaration
│   └── res/
│       ├── layout/activity_main.xml     # Main UI layout
│       └── values/
│           ├── strings.xml              # String resources
│           └── themes.xml               # Theme definitions
```

## Requirements

- Android 6.0 (API level 23) or higher
- Bluetooth audio device paired and connected
- RECORD_AUDIO permission granted
- BLUETOOTH_CONNECT permission (Android 12+)

## Building and Running

1. Clone the repository and open in Android Studio
2. Ensure Android SDK 33 or higher is installed
3. Connect an Android device (physical device recommended for Bluetooth stability)
4. Pair and connect a Bluetooth audio device
5. Build and deploy:
   ```bash
   ./gradlew assembleDebug
   ```
6. Grant required permissions when prompted
7. Tap "Start" to begin streaming (application will validate Bluetooth connectivity)
8. Audio files are saved to `Android/data/com.example.livemic/files/Records/`

## CI/CD (GitHub Actions)

- Workflow: `.github/workflows/android-release.yml`
- Trigger: push tags matching `v*` (e.g., `v1.0.0`) or manual `workflow_dispatch`
- Output: release APK uploaded as `LiveMic-<tag>.apk` in the GitHub Release assets and as a workflow artifact
- Signing: Release build uses the Android debug keystore for automation. Replace with a production signing config for store distribution.

### How to publish a release APK
1. Commit and push your changes to `main`.
2. Create a version tag and push it, for example:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. The workflow will build `app-release.apk`, rename it to `LiveMic-v1.0.0.apk`, upload it as an artifact, and attach it to the GitHub Release for that tag.
4. Download the APK from the Release page and sideload it to your device.

## Technical Details

### Audio Configuration
- **Sample Rate**: 16 kHz (configurable in AudioStreamer)
- **Channel Configuration**: Mono
- **Encoding**: PCM 16-bit signed
- **Buffer Strategy**: Uses device-optimized minimum buffer size

### Bluetooth Routing
- **A2DP Profile**: Media audio output (stable, lower latency than traditional SCO)
- **HEADSET Profile**: Alternative routing for compatible devices
- **SCO Mode**: Optional legacy support (8 kHz sample rate, higher latency)

### Permissions
- `RECORD_AUDIO`: Microphone access
- `BLUETOOTH`: Device discovery and connection state
- `BLUETOOTH_CONNECT`: Connect to paired devices (Android 12+)
- `FOREGROUND_SERVICE`: Persistent service notification
- `WRITE_EXTERNAL_STORAGE`: File recording to external storage

## Architecture

### MainActivity
- Handles runtime permission requests
- Validates Bluetooth audio device connection status
- Provides Start/Stop controls for streaming

### StreamService (Foreground Service)
- Manages service lifecycle
- Creates and monitors AudioStreamer thread
- Displays persistent notification during streaming

### AudioStreamer (Worker Thread)
- Creates AudioRecord (microphone input)
- Creates AudioTrack (Bluetooth audio output)
- Manages PCM buffering and playback
- Writes WAV format recording with proper headers

## Known Limitations

- Bluetooth latency varies significantly by device manufacturer and chipset
- Some devices may require additional system audio policy configuration
- A2DP profile routes media through speaker layer (not direct headset duplex)
- File size may grow large for extended recordings (16 kHz mono ≈ 120 KB/minute)

## Troubleshooting

### Application shows error about Bluetooth device not connected
- Ensure Bluetooth device is paired in system settings
- Verify device is within range and powered on
- Disconnect and reconnect Bluetooth device
- Check Android 12+ BLUETOOTH_CONNECT permission is granted

### Poor audio quality or crackling
- Reduce background apps to free system resources
- Ensure Bluetooth device battery is adequate
- Try a different Bluetooth audio device if available
- Physical proximity to device improves signal stability

### Recordings are corrupt or missing
- Verify external storage write permissions
- Ensure sufficient free storage space
- Check that app was not force-stopped during recording
- Verify device has external storage mounted

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome. Please ensure code follows Kotlin style guidelines and includes appropriate error handling.
