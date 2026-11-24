-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keep class com.onda.music.core.model.** { *; }
-keep class com.onda.music.core.database.** { *; }
-keep class com.onda.music.core.sources.** { *; }
-keep class com.onda.music.core.transfer.** { *; }

-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class com.onda.music.**$$serializer { *; }
-keepclassmembers class com.onda.music.** {
    *** Companion;
}

-keep class org.schabi.newpipe.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class com.metrolist.innertube.** { *; }
-keep class com.zionhuang.innertube.** { *; }
-keep class org.jellyfin.media3.ffmpeg.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn kotlinx.serialization.**
-dontwarn org.schabi.newpipe.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
-dontwarn org.brotli.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.Unsafe
