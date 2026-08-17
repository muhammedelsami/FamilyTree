# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android family-tree app built on the **GEDCOM 5.5.1** standard — a ground-up Jetpack
Compose + Clean Architecture rewrite of [Family Gem](https://github.com/michelesalvador/FamilyGem)
(GPL v3). The defining constraint is **lossless round-tripping**: any `.ged` file must
survive import → edit → export without losing a single tag.

The app is **entirely local**: no account, no server, no synchronisation. Cloud sync was
built here once and then removed in full; `docs/YOL-HARITASI.md` records why, and that
decision is not to be quietly re-litigated by adding a network dependency back.

`docs/YOL-HARITASI.md` (Turkish) is the project's authoritative design document — phases,
decisions, and the reasoning behind each architectural choice. Read it before making
structural changes.

## Commands

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:installDebug         # install on device/emulator
./gradlew test                      # all unit tests
./gradlew projects                  # module tree
./gradlew :app:lintDebug            # Android Lint

# One module's tests (Android library modules use the variant-qualified task):
./gradlew :core:gedcom:testDebugUnitTest
./gradlew :core:model:test          # pure-JVM modules (core:model, core:domain)

# A single test class or method:
./gradlew :core:gedcom:testDebugUnitTest --tests "*GedcomRoundTripTest*"
./gradlew :core:backup:testDebugUnitTest --tests "*TreeMergeTest*"

# Regenerate the Room schema JSON (core/database/schemas/…/1.json is version-controlled):
./gradlew :core:database:assembleDebug
```

JDK 17, `minSdk` 28, `compileSdk` 37. Gradle configuration cache is **on**
(`gradle.properties`) — build logic must stay configuration-cache safe.

Package: `com.familytrees.app` (`.debug` suffix on debug builds). Module packages are
`com.familytree.*` — only the application id and `:app`'s namespace carry the plural.

## Module architecture

23 modules, all configured by convention plugins in the `build-logic` composite build —
individual `build.gradle.kts` files should stay near-empty (a plugin alias, a namespace,
module-specific deps only). Convention plugin ids: `familytree.android.application`,
`familytree.android.library`, `familytree.android.compose`, `familytree.android.feature`,
`familytree.hilt`, `familytree.room`, `familytree.jvm.library`.

```
app/          Application, MainActivity, NavHost, the Hilt graph
core/
  model/      Pure Kotlin domain models + GEDCOM date parser/builder   [JVM, no Android]
  common/     Dispatcher qualifiers, LoadState                          [JVM]
  domain/     Repository *interfaces* + use cases                       [JVM]
  database/   Room entities, DAOs, converters
  datastore/  Preferences DataStore
  data/       Repository *implementations*, mappers
  gedcom/     folg/gedcom5-java interop: importer, exporter, projector
  diagram/    gedcomgraph JAR wrapper + Compose Canvas renderer
  media/      File resolution, SAF folders, cropping
  backup/     ZIP archive, tree comparison, merge
  billing/ notifications/
  designsystem/  M3 theme, tokens, atomic composables
  ui/         Shared composables that know the domain model
feature/      trees, person, family, diagram, media, backup, share, settings
```

**The dependency rule is enforced by the build, not by convention.**
`AndroidFeatureConventionPlugin` gives every feature module exactly `core:model`,
`core:common`, `core:domain`, `core:designsystem`, `core:ui` — **never `core:data`**.
Implementations are named only in `core/data/.../di/DataModule.kt` and bound in `:app`.
A feature module physically cannot reach Room or DataStore. Do not add `core:data` to a
feature module's dependencies to "make something work" — the missing piece belongs in
`core:domain` as an interface or use case.

`core:model` and `core:domain` are pure JVM. Anything Android-specific added there breaks
that and must go elsewhere.

## Invariants that tests lock down

**GEDCOM losslessness.** `GedcomRoundTripTest` imports a `.ged`, exports it, and fails if
any tag/value pair went missing — then re-imports the export and asserts the tree is
unchanged (a *fixed-point* test; the one-way test alone once passed while `_ROOT` was
being written twice). Any change to entities, mappers, importer or exporter must keep both
directions green. Unmapped vendor tags live in the self-referential `extensions` table
with nesting and order preserved; typed tag-spelling columns (`uidTag`, `wwwTag`,
`marriedNameTag`, …) exist for the same reason and are not dead weight.

**Records get their GEDCOM id at creation time**, not at export — the diagram engine
addresses records by cross-reference id, and deferring it renders new people as blank
cards. `RecordCreationTest` guards this.

**Merging matches on cross-reference id, never on row id or name.** Row ids are
device-local; three "Mehmet Yılmaz"es in one tree is ordinary. `TreeMergeTest` deliberately
offsets ids on both sides.

## Room schema (20 tables, version 1)

- **`family_members` is the single source of truth for kinship.** GEDCOM stores it twice
  (`FAM.HUSB/WIFE/CHIL` *and* `INDI.FAMS/FAMC`); keeping one row and emitting both
  directions on export makes one-sided dangling references structurally impossible.
- **Dual identity**: every table has a Room PK `id: Long` plus `gedcomId: String?`
  (`null` = inline record), unique on `(treeId, gedcomId)`. FKs use the row id, so changing
  a GEDCOM id never requires rewriting references.
- `order` and `primary` are SQLite keywords — the columns are `position` and `isPrimary`.
- Schema JSONs are checked in; adding a migration means adding a schema JSON too.

## Diagram (core:diagram)

Wraps `libs/gedcomgraph-3.12.jar` (Family Gem's layout engine, GPL v3) and renders in
Compose Canvas. Two traps, both invisible on a small test tree:

1. `setMaxBitmapSize()` is a **required handshake**, not an optimisation — without it the
   engine emits cards but zero connecting lines.
2. **Never wrap the whole diagram in one `graphicsLayer`.** A render node past the GPU
   texture limit (~4096 px) silently draws nothing, and a five-generation tree is ~6300 px
   wide. Pan/zoom is applied per-card, with connections drawn via `withTransform` on one
   screen-sized canvas. `DiagramEngineTest` has a canary test recording this.

The diagram observes a combined "content fingerprint" (people, memberships, family events,
portraits, settings) so edits elsewhere redraw it; fingerprint comparison stops Room's
re-emissions from re-running the engine.

**`PannableZoomable` keeps the original's viewport model on purpose, and it is not the
model you would arrive at.** A diagram opens at 0.7 scale rather than zoomed to fit, and one
small enough to fit is *enlarged* until it touches the edges (the fit scale is allowed above
1×). Zooming to fit reads as the better idea and disables the finger: content the exact size
of the viewport has nowhere to travel, so the cards under the app bar and in the corners can
never be dragged into view. On top of that the drag may overrun the resting bounds by a
quarter of the viewport and springs back on release, so a diagram with genuinely nowhere to
go still answers the finger. `PanBoundsTest` records the bounds; the opening scale is only
recorded here.

## UI conventions

- Navigation is **type-safe**: destinations are `@Serializable` classes in
  `app/.../navigation/Routes.kt`. Arguments travel in the route, not in remembered state.
- Streams reach screens as `LoadState<T>` via `Flow.asLoadState()` (`core:common`), so
  loading and failure are rendered states rather than crashes.
- The generic record editor is driven by **`FieldSpec`** (`core/ui/FieldSpec.kt`) —
  typed getter/setter lambda pairs, no reflection, no R8 keep rules. Supporting a new
  record type means adding a field list to `RecordFields`, not writing a screen.
  `expertOnly = true` hides GEDCOM plumbing behind expert mode.
- Genealogy-semantic colours (sex, tree state) live in `LocalGenealogyColors`, deliberately
  outside the dynamic-colour palette so wallpaper theming cannot recolour meaning.
- The typeface is a setting (`AppFont`, in DataStore): the device font, **Cairo** or
  **Alexandria**. Both are bundled as single *variable* font files, so each weight must
  declare `FontVariation.weight(...)` — a `FontWeight` alone yields the file's default
  instance and every weight comes out identical. Both are Arabic-first families carrying
  Latin Extended, which is what makes them safe here; a Latin-first font would wreck
  Arabic. They are OFL 1.1 (licences in `core/designsystem/licenses/`, recorded in
  `NOTICE.md`) and add ~0.9 MB to the release APK. `core:designsystem` holds the resources
  but knows no domain types — the `AppFont → FontFamily` mapping lives in `core:ui`.

## Localisation

**EN / TR / AR, complete in every module** — each module carries its own
`res/values{,-tr,-ar}/strings.xml`. Arabic makes **RTL mandatory**; verify layouts mirror.
`resourceConfigurations` in `app/build.gradle.kts` restricts the shipped set.

The language is chosen in Settings and is the one preference **not** kept in DataStore: the
manifest declares `android:localeConfig`, so Android 13+ also offers this app in the system
language picker, and a second copy would be wrong the moment the user used that screen.
`AppLocales` (`core:common`) wraps `AppCompatDelegate.setApplicationLocales`, which is the
platform store on 13+ and AppCompat's own below. The shipped list is `AppLanguage`, and it
must stay in step with `res/xml/locales_config.xml` and `resourceConfigurations` —
`AppLanguageTest` asserts exactly that.

**`MainActivity` is an `AppCompatActivity` for this and nothing else.** Everything it draws
is Compose. `AppCompatDelegate.setApplicationLocales` obtains a `Context` from AppCompat's
*active delegates*, so with only `ComponentActivity` in the app it finds none and **does
nothing at all, silently, on every API level** — the radio button moves and the language
never changes. Turning it back into a `ComponentActivity`, or repointing `Theme.FamilyTree`
away from an AppCompat parent, breaks language switching without breaking a build or a
test.

## Things that will bite

- **AGP 9**: `CommonExtension` is no longer generic; its `defaultConfig { }` /
  `compileOptions { }` lambda forms don't exist (use property access in convention
  plugins); AGP applies Kotlin itself, so applying `org.jetbrains.kotlin.android`
  is an error (`kotlin.plugin.compose` and `kotlin.plugin.serialization` are still applied
  manually).
- **Robolectric tests need `@Config(sdk = [36])`** — modules compile against SDK 37 and
  Robolectric 4.16.1 has no 37 image yet.
- **Release signing reads `keystore.properties` through `providers.fileContents`, not
  `File.exists()`.** The configuration cache is on, and a plain filesystem check is not a
  declared input: the first build after dropping the keystore in reuses the cached
  configuration and silently emits an *unsigned* release APK. The provider form makes the
  file an input, so appearing or changing invalidates the entry. The keystore, its
  passwords and `keystore.properties` are gitignored — this repository is public, and they
  are the only secret it has. Without them `:app:assembleRelease` still succeeds, unsigned.
- The gedcomgraph JAR is Java 21 bytecode; JDK 17's `javac` cannot read it. Fine while
  `core:diagram` has no `.java` sources — adding one breaks the build.
- Convention plugins set `failOnNoDiscoveredTests = false`, since Gradle 9 fails a module
  that has test dependencies but no tests.
- **Check `./gradlew :app:assembleRelease` after touching serialized types.** The release
  build minifies, and R8 renaming an enum constant breaks kotlinx.serialization — which
  serializes enums by name — in release builds only. `app/proguard-rules.pro` keeps the
  `core:model` enum members for exactly this reason; the rule lives there rather than as an
  `@Keep` annotation so `core:model` stays Android-free.
- There are **no instrumented or Compose UI tests** (`androidTest` does not exist in any
  module). Everything is JVM/Robolectric, so anything that only breaks on a real device —
  gesture handling, SAF permissions, GPU limits — is caught by hand, not by CI.

## Licence

GPL v3 or later, and not by choice: linking `gedcomgraph-3.12.jar` makes this a derivative
work of Family Gem. A closed-source release is not permitted. See `NOTICE.md` before
adding dependencies or considering distribution.
