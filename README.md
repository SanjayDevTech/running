# Running

An offline Android run tracker: Strava's core and nothing else. Record a run or walk with GPS,
see it on a map with splits, pace and elevation, and track weekly totals and personal bests.
Everything stays on the device. There is no account, no social feed and no analytics. The only
network use is map tiles.

> Status: foundation only. The shared code, data layer and build are in place; the screens are
> placeholders until the feature tracks land (see [docs/architecture.md](docs/architecture.md)).

## Install

Grab the APK from [Releases](../../releases). It is signed with the public key committed in
this repo, so each release installs over the last as an update.

That key is public on purpose, which means anyone can sign an APK with it. It is a
convenience for updating, never proof of who built a build.

## Build

```sh
./gradlew assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew installDebug       # build + install on a connected device
./gradlew assembleRelease    # signed, R8-shrunk release APK
./gradlew testDebugUnitTest  # JVM unit tests (Robolectric for Room)
```

Android SDK 37 and a Java 25 toolchain. The Gradle daemon pins Java 25 in
`gradle/gradle-daemon-jvm.properties` and downloads it if you do not have it. `minSdk` is 31,
English only, kilometres only.

Debug builds can seed ~40 synthetic runs for UI work:

```sh
adb shell am start -n com.example.running/.MainActivity --ez seed_demo true
```

Release builds bundle a Baseline Profile (committed in `app/src/release/generated/baselineProfiles/`)
so startup and scrolling are AOT-compiled even though the app is sideloaded. Building needs no
device; to regenerate it after major UI changes (boots a headless API 34 emulator, ~12 min):

```sh
./gradlew :app:generateBaselineProfile
```

Details, the startup benchmark and when to regenerate: [docs/architecture.md §7.1](docs/architecture.md).

CI: **Actions → Build Release APK** builds any branch and attaches the APK to the run, and
**Actions → Trigger a Release** bumps the version, builds, then tags and publishes
`Running-v<version>.apk` only if the build passed.

## Optional configuration

**Map tiles.** The map uses [OpenFreeMap](https://openfreemap.org) tiles with a bundled dark
style and needs no key. To use MapTiler instead, add to `local.properties` (git-ignored):

```properties
MAPTILER_KEY=your_key_here
```

## Stack

Kotlin · Jetpack Compose (Material 3 Expressive, always dark) · Room · DataStore · WorkManager ·
Play services location + the platform LocationManager · MapLibre · Vico charts

Single module with manual DI. The package layout, the contracts between parts and who owns which
file are in [docs/architecture.md](docs/architecture.md); UI rules are in
[docs/ui-house-rules.md](docs/ui-house-rules.md).

## How it was made

Built with AI assistance (Claude), directed, designed and tested by its maintainer. The commit
trailers say so too.

## License

[MIT](LICENSE) © 2026 Running contributors.
