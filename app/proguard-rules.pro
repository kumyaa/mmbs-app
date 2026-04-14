# R8 full-mode shrinking + optimisation. Keep lists below are intentionally tight.
# Each keep rule has a reason comment — do not add blanket keep rules without one.

# --- Kotlin & Kotlinx.serialization ---------------------------------------------------------

# Required for kotlinx.serialization: keep @Serializable companion objects and serializer() methods.
-keepattributes InnerClasses
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# --- Room (added when we wire it in Phase A) ------------------------------------------------
# Room annotation processor generates DAO_Impl classes. No manual keep rules needed for Room 2.6+.

# --- App-specific (placeholder until Phase A code lands) ------------------------------------
# No extra keeps yet for the scaffold.

# --- Diagnostics --------------------------------------------------------------------------
# Keep line numbers in stack traces from release builds (tiny size cost; huge debug value).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
