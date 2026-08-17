# Notice

FamilyTree is licensed under the **GNU General Public License v3.0 or later**.
The full text is in [LICENSE](LICENSE).

## Why this project is GPL

FamilyTree is a derivative work of **[Family Gem](https://github.com/michelesalvador/FamilyGem)**
by Michele Salvador, which is licensed under GPL v3. The derivation is deliberate and
material, not incidental:

- **`libs/gedcomgraph-3.12.jar`** — the family-diagram layout engine, written for
  Family Gem and distributed with it. FamilyTree links against this binary directly.
  No source is published for it.
- **Data model and algorithms** — the GEDCOM date grammar handling, the tree "grade"
  share lifecycle, the person-merge matching rules and the record-integrity checks were
  all studied in Family Gem's implementation and reimplemented here.
- **Test fixtures** — `core/gedcom/src/test/resources/media.ged` is Family Gem's own
  test file, kept because it exercises real-world awkward cases.

Because of this, the GPL applies to FamilyTree as a whole. In practical terms:

- The complete source must be made available to anyone who receives the app.
- A closed-source release — including on Google Play — is not permitted.
- Any fork or redistribution must stay under GPL v3 or later.

## Third-party components

| Component | License | Role |
|---|---|---|
| [Family Gem](https://github.com/michelesalvador/FamilyGem) | GPL v3 | The application this one derives from |
| `gedcomgraph` 3.12 | GPL v3 | Diagram layout engine (bundled JAR) |
| [gedcom5-java](https://github.com/FamilySearch/gedcom5-java) (`org.familysearch.gedcom`) | Apache 2.0 | GEDCOM parsing and writing |
| AndroidX, Jetpack Compose, Room, DataStore, Navigation | Apache 2.0 | Application framework |
| Hilt / Dagger | Apache 2.0 | Dependency injection |
| kotlinx-coroutines, kotlinx-serialization, kotlinx-datetime | Apache 2.0 | Kotlin libraries |
| Coil | Apache 2.0 | Image loading |
| [Cairo](https://github.com/Gue3bara/Cairo) | SIL OFL 1.1 | Optional interface typeface, bundled — `core/designsystem/licenses/Cairo-OFL.txt` |
| [Alexandria](https://github.com/Gue3bara/Alexandria) | SIL OFL 1.1 | Optional interface typeface, bundled — `core/designsystem/licenses/Alexandria-OFL.txt` |
| Robolectric, JUnit, Truth, Turbine, MockK | Apache 2.0 / EPL | Testing |
| Avatar silhouettes — `core/designsystem/src/main/res/drawable/ic_avatar_{man,woman}.xml` | **Not yet established** | Placeholder for a person with no photograph |
| The family group inside the app's mark — `app/src/main/res/drawable/ic_launcher_foreground.xml` | **Not yet established** | Launcher icon, themed icon and splash screen |

> Three pieces of artwork came in as downloaded SVG files —
> `silhouette-male-user-icon.svg` and `woman-female-icon.svg` for the avatars, and
> `family-insurance-icon.svg` for the three figures in the launcher mark — and their
> origin and licence have not been established. **This has to be settled before the app
> is distributed**: either record the source and its licence in the table above, or
> replace the drawings with our own. The launcher icon is the more pressing of the two,
> being the one piece of the app a store listing puts in front of everyone. The rest of
> each icon — the layout, the connector, the palette, the composition — is ours.
> Everything else in this project is either ours or listed here.

## What is *not* carried over

The user interface is written from scratch in Jetpack Compose. None of Family Gem's
drawables, layouts, icons or translated strings are reused; the visual design and colour
palette are original to this project, as is the iconography apart from Material's own icon
set and the three downloaded drawings noted above.
