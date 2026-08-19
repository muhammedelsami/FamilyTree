# R8 rules for the release build.
#
# Room, Hilt, kotlinx.serialization and Compose all ship their own consumer rules, so this
# file only has to cover what the bundled GEDCOM libraries drag in.

# gedcom5-java and gedcomgraph log through slf4j, whose LoggerFactory looks up a binder
# implementation that is deliberately not packaged: on Android the logging goes nowhere and
# nothing needs it. The class is absent by design, so the reference is a warning, not a
# missing dependency.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Domain enums travel inside the @Serializable navigation routes, and kotlinx.serialization
# writes an enum as its constant's *name*. R8 renaming those constants would break argument
# passing in release builds only — `Relation` reaching PersonEditor as an unknown name is
# the concrete case. Lint suggests @Keep, but `core:model` is deliberately Android-free, so
# the rule belongs here rather than as an AndroidX annotation in that module.
-keepclassmembers enum com.familytree.core.model.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# The rule above keeps the constants, but not the *name of the enum class itself* — and
# Navigation needs that name. `serialName` is baked into the descriptor at compile time as
# the original fully qualified name, and `androidx.navigation.serialization` resolves an
# enum argument's NavType by calling Class.forName() on it. R8 renamed Relation to `t13`,
# so the lookup threw
#
#   IllegalArgumentException: Cannot find class with name
#   "com.familytree.core.model.Relation?"
#
# while NavHost was still building its graph — every release build crashed on its first
# frame, before a single screen was drawn, and no debug build ever could. `Route.PersonEditor`
# is the destination that carries one today; keeping the names of all the model enums stops
# the next enum to travel in a route from repeating it.
-keepnames enum com.familytree.core.model.**
