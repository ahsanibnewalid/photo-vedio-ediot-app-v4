# Shuvo Studio release diagnostics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature,InnerClasses,EnclosingMethod

# Media3 artifacts ship consumer R8 rules. Keep rules narrow and add rules only
# for app classes accessed by reflection or when a reproducible release-only
# failure demonstrates they are required. Do not blanket-keep androidx.media3.
# Kotlin and kotlinx.coroutines are normally handled by the Kotlin/R8 toolchain.
