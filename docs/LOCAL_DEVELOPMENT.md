# Working on Keeply

## What you need

**A JDK, 21 or newer.** That is all. Keeply builds on any JDK from 21 up and
always emits Java 21 bytecode, so your local JDK never changes the artefact.

There is nothing else to install. Tesseract, OpenCV and SQLite are pulled from
Maven Central as native binaries for your platform, and the OCR language model is
fetched once at build time and verified against a pinned SHA-256.

```bash
git clone https://github.com/mateoosoriodelhonte/keeply.git
cd keeply
./gradlew build
```

The first build downloads a few hundred megabytes of native binaries. Later ones
do not.

## Running it

```bash
./gradlew :desktop:run
```

Against a throwaway library instead of your real one:

```bash
./gradlew :desktop:run -Pkeeply.dataDir=/tmp/keeply-dev
```

## Everything CI runs

```bash
./gradlew spotlessCheck privacyGuard build -Pkeeply.strict=true
```

- `spotlessCheck` — formatting, ktlint. `spotlessApply` fixes it.
- `privacyGuard` — fails if anything outside `:core:ai` gains network access or
  an analytics dependency.
- `-Pkeeply.strict=true` — Kotlin warnings become errors, as they do in CI,
  without making day-to-day work noisy.

## Receipts to test with

```bash
./gradlew :fixtures:writeSampleReceipts
```

Writes generated receipts to `fixtures/build/sample-receipts` in four layouts and
several capture conditions: clean, photographed at an angle, faded, blurry, plus
PDFs both with and without a text layer. Drag them onto the window.

## Screenshots

```bash
./gradlew :desktop:writeScreenshots
```

Renders the documentation screenshots from the real composables against demo
data, with no display required. They cannot drift from the application the way
hand-taken ones do.

## The icon

```bash
./gradlew :desktop:writeIconset
iconutil -c icns desktop/build/Keeply.iconset -o desktop/src/main/resources/icons/keeply.icns
```

The artwork is drawn in code, so it is reviewable as a diff and regenerable at
any size.

## Packaging

```bash
./gradlew :desktop:packageDistributionForCurrentOS
```

Produces `desktop/build/compose/binaries/main/app/Keeply.app` and a disk image
beside it.

**Use a Temurin JDK for this.** The Compose packager refuses a Homebrew JDK
outright, and other distributions can produce a broken bundle. If you hit
`Homebrew's JDK distribution may cause issues with packaging`, that is why.

```bash
JAVA_HOME=/path/to/temurin-21 ./gradlew :desktop:packageDistributionForCurrentOS
```

Native binaries default to your own platform. A release build asks for more:

```bash
./gradlew :desktop:packageDistributionForCurrentOS \
  -Pkeeply.nativePlatforms=macosx-arm64,macosx-x86_64
```

## macOS and unsigned applications

Keeply is not signed or notarised. This project has no Apple Developer
certificate, and buying one to release an open-source utility is a cost this
project does not carry.

What that means for somebody downloading it:

- **Double-clicking will not work.** macOS refuses with "Keeply cannot be opened
  because the developer cannot be verified."
- **Right-click, then Open, then confirm.** This works, and is only needed once.
- Depending on the macOS version, you may instead need to allow it from
  **System Settings → Privacy & Security** after the first attempt.

This is not a formality to click past. macOS is telling you it cannot verify who
built the application, and that is true. If you would rather not take that on
trust, build it yourself from source with the commands above: the result is the
same application, and you will have watched it being made.

To check a downloaded disk image matches what CI built, compare its digest
against the one on the release page:

```bash
shasum -a 256 Keeply-1.0.0-apple-silicon.dmg
```

## How the build is put together

Conventions live in `buildSrc/src/main/kotlin/keeply.kotlin-library.gradle.kts`:
explicit API mode, Java 21 bytecode, Spotless, and a test setup that refuses to
touch the real user data directory. Versions live in `gradle/libs.versions.toml`.

Two tools are deliberately absent, both for the same reason:

- **detekt** — its newest release still uses the Kotlin 1.9 compiler frontend and
  cannot parse a Kotlin 2.4 source set.
- **CodeQL** — its Kotlin extractor rejects this project with "Kotlin version too
  new".

A permanently red check that analyses nothing is worse than no check. Static
analysis is ktlint, the Kotlin compiler with warnings as errors, and the privacy
guard. Both will come back when they support the language version.

## Tests

```bash
./gradlew test                                    # everything
./gradlew :core:extraction:test                   # one module
./gradlew :core:ocr:test --tests '*Accuracy*'     # one class
```

Some are worth knowing about:

| | |
| --- | --- |
| `OcrAccuracyTest` | measures recognition against receipts whose contents are known |
| `PreprocessingAblationTest` | asserts the measurements that set the preprocessing defaults |
| `ExtractionAccuracyTest` | field-level accuracy across a 40-receipt corpus |
| `HostileArchiveTest` | twelve backup archives an attacker would send |
| `EndToEndTest` | whole jobs: import, correct, save, find, back up, restore |
| `SchemaTest` | the delete rules, which are load-bearing and invisible |

Tests are not tied to the day they run: anything involving a date takes one.
