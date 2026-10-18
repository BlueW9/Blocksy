# Blocksy

A small, fast, offline call blocker for Android 10+.

- Move contacts into a blacklist; blocked numbers can't ring your phone
- Keeps blocking even when the app is closed (runs as Android's call screening service)
- No internet permission, no tracking, no call history stored
- English only
- License: GPL-3.0-or-later

## How it works
Blocksy registers as the phone's *call screening app*. Android hands every incoming call to it and Blocksy does one local database lookup (last 9 digits of the number). Match: the call is rejected. No match: the call rings normally. Hidden/private numbers are never blocked.

## Build
Push a tag (`git tag v1.0.0 && git push --tags`). GitHub Actions builds the APK, attaches it to the Release along with a SHA-256 checksum.
