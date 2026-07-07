# ─── Crash reporting ─────────────────────────────────────────────────────────
# Keep line numbers in stack traces so Firebase/Logcat output is actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Enums used via name/valueOf() from intent extras and DataStore ───────────
# BlockReason is serialised to/from intent extras as a string. ContentFilter
# is persisted in DataStore by ordinal name. R8 full mode strips unused enum
# members, so we must preserve the reflective access points.
-keepclassmembers enum com.focusguard.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
}

# ─── Domain sealed class hierarchy ───────────────────────────────────────────
# BlockingStrategy subclasses are round-tripped through Room TypeConverters
# and through DataStore serialisation. Keep all members so the type names and
# constructor parameters survive full-mode shrinking.
-keep class com.focusguard.core.domain.model.BlockingStrategy { *; }
-keep class com.focusguard.core.domain.model.BlockingStrategy$* { *; }

# ─── Room entities (field names become column names via reflection) ────────────
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
# TypeConverters are referenced by class name in the @Database annotation.
-keep @androidx.room.TypeConverters class * { *; }
-keepclassmembers @androidx.room.TypeConverters class * { *; }

# ─── WorkManager / HiltWorker ─────────────────────────────────────────────────
# Worker class names appear as strings inside WorkRequest builders.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── dnsjava — uses SPI and Class.forName() for record type dispatch ──────────
-keep class org.xbill.dns.** { *; }
-keepnames class org.xbill.dns.**
-dontwarn org.xbill.dns.**
# SPI provider registered in META-INF/services
-keep class org.xbill.dns.spi.** { *; }

# ─── AndroidX Security Crypto / Tink (EncryptedSharedPreferences) ─────────────
# Tink uses reflection internally to load key types and primitive factories.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ─── Kotlin serialization (Navigation Compose uses @Serializable routes) ──────
# The serialization plugin generates companion `serializer()` methods that
# R8 full mode considers dead code without these rules.
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    static *** serializer();
    kotlinx.serialization.descriptors.SerialDescriptor serialVersionUID;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ─── Vico charts ─────────────────────────────────────────────────────────────
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# ─── Suppress warnings for optional transitive dependencies ───────────────────
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.errorprone.**
# dnsjava uses Lombok (compile-time only) and SLF4J (replaced by no-op on Android).
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
# dnsjava SPI providers target desktop JVMs; the interfaces don't exist on Android.
-dontwarn java.net.spi.InetAddressResolverProvider
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
