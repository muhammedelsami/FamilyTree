# FamilyTree

A family-tree app for Android built on the **GEDCOM 5.5.1** standard: import a tree from
any genealogy program, edit it, and export it back without losing a single tag.

Written in Jetpack Compose with a layered architecture, and **entirely offline** — there
is no account, no server, and no analytics. The tree lives in a Room database on the
device and leaves it only when you export or back it up yourself.

## What it does

- **GEDCOM import and export** that round-trips losslessly, vendor tags included.
- **People, families, events, sources, notes and media**, with a generic record editor
  that covers the parts of GEDCOM most apps drop.
- **An interactive family diagram**, pan and zoom, drawn from the tree's real structure.
- **ZIP backups** — manifest, GEDCOM and media in one archive — with restore.
- **Tree sharing by file**: send a `.ged`, receive one back, and review every difference
  before it is merged.
- **Birthday reminders**, scheduled locally.
- **English, Turkish and Arabic**, fully translated, with RTL layouts.

Everything needed to record and keep a family tree is free. One optional purchase unlocks
automatic merging of two trees — the part that otherwise costs hours of manual work.

## Privacy

The app collects nothing and sends nothing. There is no account system, no cloud
synchronisation and no analytics; your tree is a Room database in the app's private
storage. The merged manifest does carry `INTERNET`, because Google Play Billing needs it
to talk to the Play Store — no part of the app sends your data anywhere. See
[docs/PRIVACY.md](docs/PRIVACY.md).

## Building

```bash
./gradlew :app:assembleDebug     # build
./gradlew :app:installDebug      # install on a connected device
./gradlew test                   # run the unit tests
```

Requires JDK 17+ and the Android SDK. Minimum supported Android version is 9 (API 28).

Release builds look for a `keystore.properties` in the repository root (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`). Without it `:app:assembleRelease` still
succeeds and produces an unsigned APK — the signing material is deliberately not in
version control.

## Losslessness

The claim that a GEDCOM file survives a round trip is enforced by tests, not asserted:
`GedcomRoundTripTest` imports a file, exports it, and fails if any tag/value pair went
missing — then re-imports the export and checks the tree is unchanged. Vendor tags the
app has no editor for (`_APID`, `_MILT`, `_ROOT` …) are preserved in a dedicated
`extensions` table with their nesting and order intact.

## Architecture

23 Gradle modules configured by convention plugins in a `build-logic` composite build.
Feature modules can reach `core:domain` but physically cannot reach Room or DataStore —
the dependency rule is enforced by the build rather than by convention.

The plan, the architecture and the decisions behind them live in
**[docs/YOL-HARITASI.md](docs/YOL-HARITASI.md)** (Turkish), including why the cloud
synchronisation that was once built here was removed again.

## Licence

GPL v3 or later. FamilyTree derives from
[Family Gem](https://github.com/michelesalvador/FamilyGem) by Michele Salvador and links
against its diagram engine, so the whole application is covered — see
[NOTICE.md](NOTICE.md) for what that means in practice.
