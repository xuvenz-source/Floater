# Rapid Tap Toggle

A small Android overlay utility designed for Android 10.

## Behaviour
- Pick a target screen coordinate with the draggable target overlay.
- Show a small circular floating button.
- Tap **▶** once to begin repeated taps at the target.
- Tap **■** to stop immediately.
- Drag the floating circle to reposition it.
- Tap interval is configurable from 30–1000 ms (75 ms default).

## Permissions
- **Display over other apps**: required for the floating button and target picker.
- **Accessibility**: required only to dispatch the selected tap gestures.
- No INTERNET permission is requested.

## Android compatibility
- Minimum Android 7.0 (API 24)
- Target Android 10 (API 29)

The GitHub Actions workflow builds an installable debug APK and uploads it as an artifact.
