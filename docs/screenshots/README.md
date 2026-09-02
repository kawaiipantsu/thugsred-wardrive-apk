# Screenshots

Generated from the real Jetpack Compose UI on the JVM — no emulator, no device —
with [Robolectric](https://robolectric.org) + [Roborazzi](https://github.com/takahirom/roborazzi),
fed sample data from `app/src/test/java/red/thugs/wardrive/screenshots/Fakes.kt`.

| File | Screen |
|---|---|
| `list.png` | Live session list, streaming (LIVE strip, populated footer) |
| `map.png` | Offline map — scanned points + driving path |
| `list_empty.png` | Scanning, waiting for the first GPS fix |
| `about.png` | About page (project overview + optimise-your-phone checklist) |

Regenerate after a UI change:

```bash
./gradlew :app:recordRoborazziDebug     # rewrite these PNGs
./gradlew :app:verifyRoborazziDebug     # CI-style: fail if the UI changed
```

Test: `app/src/test/java/red/thugs/wardrive/screenshots/ScreenshotTest.kt`. It
renders `WardriveScaffold` / `AboutScreen` directly, so it needs no `MainViewModel`
or Android services.
