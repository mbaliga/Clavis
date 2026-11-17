# Clavis release shrinking rules (applied on top of proguard-android-optimize.txt).
# Keep every rule here explicit and narrowly scoped; each block says why it exists. R8 runs in full mode
# (AGP >= 8.0 default). Review the merged result in build/outputs/mapping/release/configuration.txt.

# ---- kotlinx.serialization (rules from the library README) --------------------------------------
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
# See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
-dontnote kotlinx.serialization.**

# Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
# If there is no `java.lang.ClassValue` (for example, in Android), then R8/ProGuard will print a warning.
# However, since in this case they will not be used, we can disable these warnings
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# ---- What is deliberately NOT here (reviewed 2026-09-02 against the merged configuration) --------
# * kotlinx-serialization 1.8.1 ships consumer rules (META-INF/com.android.tools/r8/*.pro), so the block
#   above duplicates them; it is kept so readers can see what is retained. The one case the shipped rules
#   do not cover under R8 full mode is a @Serializable class with a *named* companion object. Review rule
#   for :shared: do not give @Serializable classes named companions. If one ever becomes necessary, the
#   library README's template is (fill in the class, then uncomment):
#-keepattributes InnerClasses
#-if @kotlinx.serialization.Serializable class io.github.mbaliga.clavis.core.HasNamedCompanion { static **$* *; }
#-keepnames class <1>$$serializer { static <1>$$serializer INSTANCE; }
#-keepclasseswithmembers, allowshrinking, allowobfuscation, allowaccessmodification class io.github.mbaliga.clavis.core.HasNamedCompanion { *; }
# * Compose, kotlinx-coroutines, kotlinx-datetime and androidx.graphics:graphics-path ship their own
#   consumer rules; blanket "-keep class androidx.compose.**" rules would defeat the shrinking those
#   rules already scope correctly. Do not add them.
# * If R8 ever
#   writes build/outputs/mapping/release/missing_rules.txt, paste its content here verbatim.

# ---- Diagnostics -----------------------------------------------------------------------------
# Keep file names and line numbers so release stack traces stay readable (pair with mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
